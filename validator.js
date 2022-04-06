// jshint node:true
// jshint shadow:true

module.exports = {
    validate: validate,
    validateAsync: validateAsync,
    clearCache: clearCache,
    parseSchematron: require('./parseSchematron')
};

const fs = require('fs');
const xpath = require('xpath');
const Dom = require('@xmldom/xmldom').DOMParser;
// crypto is now built-in node
const crypto = require('crypto');
const _get = require('lodash.get');
const parseSchematron = require('./parseSchematron');
const testAssertion = require('./testAssertion');
const includeExternalDocument = require('./includeExternalDocument');

// Parsed object cache
let parsedMap = Object.create({});
let contextMap = Object.create({});

function clearCache() {
    parsedMap = Object.create({});
    contextMap = Object.create({});
}
function checkIfFileOrPath(givenString) {
    // If not valid xml, it might be a filepath
    // Adding explicit check to make it clear
    if (!givenString) {
        return new Error('No data found in the xml or schematron');
    }
    if (givenString.trim().indexOf('<') === -1) {
        try {
            givenString = fs.readFileSync(givenString, 'utf-8').toString();
        }
        catch (err) {
            // If no valid xml found, inform user, and return immediately
            console.log('No valid xml or schematron could be found');
            return err;
        }
    }
    return givenString;
}
function getSchematronMap(schematron, options = {}) {
    if (_get(options, 'parsedSchematronMap')) {
        return _get(options, 'parsedSchematronMap');
    }
    const hash = crypto
        .createHash('md5')
        .update(schematron)
        .digest('hex');
    if (parsedMap[hash]) {
        return parsedMap[hash];
    }
    // If not in cache
    // Load schematron doc
    const schematronDoc = new Dom().parseFromString(schematron);
    // Parse schematron
    parsedMap[hash] = parseSchematron(schematronDoc, options);
    return parsedMap[hash];
}
function getErrorsAndWarnings(assertionObject, assertionInfo) {
    const { type, assertionId, test, simplifiedTest, description, results } = assertionObject;
    const { patternId, ruleId, context, errors, warnings } = assertionInfo;
    for (let index = 0; index < results.length; index++) {
        const resultObject = results[index];
        const { result, line, path, xml } = resultObject;
        // If the xpath did not return a result (result=false), then there should be
        // and error or warning
        if (!result) {
            const obj = {
                type,
                test,
                simplifiedTest,
                description,
                line,
                path,
                patternId,
                ruleId,
                assertionId,
                context,
                xml
            };
            if (type === 'error') {
                errors.push(obj);
            }
            else {
                warnings.push(obj);
            }
        }
    }
}
function getIgnored(assertionObject, assertionInfo) {
    const { type, assertionId, test, simplifiedTest, description, errorMessage } = assertionObject;
    const { patternId, ruleId, context, ignored } = assertionInfo;
    const obj = {
        errorMessage,
        type,
        test,
        simplifiedTest,
        description,
        patternId,
        ruleId,
        assertionId,
        context
    };
    ignored.push(obj);
}
function getResults(assertionObject, assertionInfo) {
    const { results } = assertionObject;
    if (Array.isArray(results)) {
        getErrorsAndWarnings(assertionObject, assertionInfo);
    }
    else if (results.ignored) {
        getIgnored(assertionObject, assertionInfo);
    }
}
function processFailedAssertions(failedAssertions, assertionInfo) {
    for (let index = 0; index < failedAssertions.length; index++) {
        const assertionObject = failedAssertions[index];
        getResults(assertionObject, assertionInfo);
    }
}
function getFailedAssertions(ruleId, options) {
    let failedAssertions;
    assertionInfo.ruleId = ruleId;
    const ruleObject = ruleAssertionMap[ruleId];
    if (!_get(ruleObject, 'abstract')) {
        const context = _get(ruleObject, 'context');
        assertionInfo.context = context;
        const assertionsAndExtensions = _get(ruleObject, 'assertionsAndExtensions') || [];
        failedAssertions = checkRule(context, assertionsAndExtensions, options);
    }
    return failedAssertions;
}
function processRules(rules, assertionInfo, options) {
    for (let index = 0; index < rules.length; index++) {
        const ruleId = rules[index];
        const failedAssertions = getFailedAssertions(ruleId, options);
        if (failedAssertions) {
            processFailedAssertions(failedAssertions, assertionInfo);
        }
    }
}
async function processRulesAsync(rules, assertionInfo, options) {
    const rulesProcessed = new Promise((resolve, reject) => {
        try {
            for (let index = 0; index < rules.length; index++) {
                const ruleId = rules[index];
                const failedAssertions = getFailedAssertions(ruleId, options);
                resolve (failedAssertions);
                if (failedAssertions) {
                    processFailedAssertions(failedAssertions, assertionInfo);
                }
            }
        }
        catch (promiseError) {
            reject (new Error (`Promise Error: ${promiseError}`));
        }
    });
    return rulesProcessed;
}
function getReturnObject(assertionInfo) {
    const { errors, warnings, ignored } = assertionInfo;
    const result = {
        errorCount: errors.length,
        warningCount: warnings.length,
        ignoredCount: ignored.length,
        errors: errors,
        warnings: warnings,
        ignored: ignored
    };
    return result;
}
let namespaceMap;
let patternRuleMap;
let ruleAssertionMap;
let xpathSelect;
let resourceDir;
let xmlSnippetMaxLength;
let xmlDoc;
let schematronMap;
let errors;
let warnings;
let ignored;
let assertionInfo;
function initializeProcess(xml, schematron, options) {
    try {
        xml = checkIfFileOrPath(xml);
        schematron = checkIfFileOrPath(schematron);
    }
    catch (err) {
        return err;
    }
    // Load xml doc
    xmlDoc = new Dom().parseFromString(xml);
    // Allowing users to send a parsed schematron map if testing multiple xml files with the same schematron
    schematronMap = getSchematronMap(schematron, options);
    // Extract data from parsed schematron object
    namespaceMap = schematronMap.namespaceMap;
    patternRuleMap = schematronMap.patternRuleMap;
    ruleAssertionMap = schematronMap.ruleAssertionMap;
    resourceDir = './';
    xmlSnippetMaxLength = 200;
    if (_get(options, 'resourceDir')) {
        resourceDir = _get(options, 'resourceDir');
    }
    if (_get(options, 'xmlSnippetMaxLength')) {
        xmlSnippetMaxLength = _get(options, 'xmlSnippetMaxLength');
    }
    // Create selector object, initialized with namespaces
    // Avoid using 'select' as a variable name as it is overused
    xpathSelect = xpath.useNamespaces(namespaceMap);
    errors = [];
    warnings = [];
    ignored = [];
    assertionInfo = {
        errors,
        warnings,
        ignored
    };
}
function validate(xml, schematron, options = {}) {
    initializeProcess(xml, schematron, options);
    for (const patternId in patternRuleMap) {
        if (!patternRuleMap.hasOwnProperty(patternId)) {
            continue;
        }
        assertionInfo.patternId = patternId;
        const rules = patternRuleMap[patternId];
        processRules(rules, assertionInfo, options);
    }
    return getReturnObject(assertionInfo);
}

async function validateAsync(xml, schematron, options = {}) {
    initializeProcess(xml, schematron, options);
    for (const patternId in patternRuleMap) {
        if (!patternRuleMap.hasOwnProperty(patternId)) {
            continue;
        }
        assertionInfo.patternId = patternId;
        const rules = patternRuleMap[patternId];
        await processRulesAsync(rules, assertionInfo, options);
    }
    return getReturnObject(assertionInfo);
}
function getSelectXmlChunk(context) {
    // Determine the sections within context, load selected section from cache if possible
    let selectedXml = contextMap[context];
    let contextModified = context;
    if (!selectedXml) {
        if (context) {
            if (context.indexOf('/')) {
                contextModified = '//' + context;
            }
            selectedXml = xpathSelect(contextModified, xmlDoc);
        }
        else {
            selectedXml = [xmlDoc];
        }
        contextMap[context] = selectedXml;
    }
    return selectedXml;
}
function getAssertionObject(level, id, originalTest, simplifiedTest, description) {
    const assertionObject = {
        type: level,
        assertionId: id,
        test: originalTest,
        simplifiedTest: simplifiedTest,
        description: description
    };
    return assertionObject;
}
function processExtension(assertionAndExtensionObject, context, options) {
    const extensionRule = assertionAndExtensionObject.rule;
    let failedSubAssertions = [];
    if (extensionRule) {
        const newRuleObject = ruleAssertionMap[extensionRule];
        const subAssertionsAndExtensions = newRuleObject ? newRuleObject.assertionsAndExtensions : null;
        if (subAssertionsAndExtensions) {
            failedSubAssertions = checkRule(context, subAssertionsAndExtensions, options);
        }
    }
    return failedSubAssertions;
}
function processAssertions(assertionAndExtensionObject, selectedXml, failedAssertions, options) {
    // const failedAssertions = [];
    let { level, test, id, description } = assertionAndExtensionObject;
    // Extract values from external document and modify test if a document call is made
    const originalTest = test;
    let simplifiedTest = null;
    try {
        test = includeExternalDocument(test, resourceDir);
        if (originalTest !== test) {
            simplifiedTest = test;
        }
        if (level === 'error' || _get(options, 'includeWarnings')) {
            const testAssertionResponse = testAssertion(test, selectedXml, xpathSelect, xmlDoc, resourceDir, xmlSnippetMaxLength);
            if ((Array.isArray(testAssertionResponse) && testAssertionResponse.length) || _get(testAssertionResponse, 'ignored')) {
                const assertionObject = getAssertionObject(level, id, originalTest, simplifiedTest, description);
                assertionObject.results = testAssertionResponse;
                failedAssertions.push(assertionObject);
            }
        }
    }
    catch (err) {
        const assertionObject = getAssertionObject(level, id, originalTest, simplifiedTest, description);
        assertionObject.results = { ignored: true, errorMessage: err.message };
        failedAssertions.push(assertionObject);
    }
    return failedAssertions;
}
// Take the checkRule function out of validate function, and pass on the variable needed as parameters and options
function checkRule(context, assertionsAndExtensions, options) {
    // Context cache
    const failedAssertions = [];
    // Determine the sections within context, load selected section from cache if possible
    const selectedXml = getSelectXmlChunk(context);
    for (let i = 0; i < assertionsAndExtensions.length; i++) {
        const assertionAndExtensionObject = assertionsAndExtensions[i];
        if (assertionAndExtensionObject.type === 'assertion') {
            processAssertions(assertionAndExtensionObject, selectedXml, failedAssertions, options);
        }
        else {
            const failedSubAssertions = processExtension(assertionAndExtensionObject, context, options);
            failedAssertions.push(...failedSubAssertions);
        }
    }
    return failedAssertions;
}
