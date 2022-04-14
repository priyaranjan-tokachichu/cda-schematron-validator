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
let schematronMap = Object.create({});

function clearCache() {
    parsedMap = Object.create({});
    contextMap = Object.create({});
    schematronMap = Object.create({});
}


/**
 * @method validate The entry method called to validate an xml with schematron. Calls `initializeProcess` and `processRules` methods, and gets the results
 * @param {string} xml string content of an xml file or path to an xml file
 * @param {string} schematron string content of a schematron file or path to a schematron file
 * @param {object} options object that takes parameters
 * `resourceDir`: Where additional documents with added rules such as value set restrictions are present. Default value is the same directory of the code being executed './'.,
 * `xmlSnippetMaxLength`: Restrict the length of the xpath mapped xml snippet when returning the error/warning data to the user. Default value is 200 characters.,
 * `includeWarnings`: User can choose to look for warnings and include them in the report. The default value is false.,
 * `parsedSchematronMap`: User can choose to supply the schematron map instead of a schematron file avoiding the parsing of the schematron file for multiple xml files or check for cache.
 * @returns Calls `getReturnObject` and gets the results (errors, warning, and ignored) as javascript objects
 */

function validate(xml, schematron, options = {}) {
    const resultInfo = {
        errors: [],
        warnings: [],
        ignored: []
    };
    initializeProcess(xml, schematron, options);
    for (const patternId in schematronMap.patternRuleMap) {
        if (!schematronMap.patternRuleMap.hasOwnProperty(patternId)) {
            continue;
        }
        const assertionInfo = Object.create({});
        assertionInfo.patternId = patternId;
        const rules = schematronMap.patternRuleMap[patternId];
        processRules(rules, assertionInfo, resultInfo, options);
    }
    return getReturnObject(resultInfo);
}

/**
 * @method validateAsync The entry method called to validate an xml with schematron. Calls `initializeProcess` and `processRulesAsync` methods, and gets the results.
 * This is similar to `validate` method except that it uses asynchronous `processRulesAsync` method.
 * @param {string} xml string content of an xml file or path to an xml file
 * @param {string} schematron string content of a schematron file or path to a schematron file
 * @param {object} options object that takes parameters
 * `resourceDir`: Where additional documents with added rules such as value set restrictions are present. Default value is the same directory of the code being executed './'.,
 * `xmlSnippetMaxLength`: Restrict the length of the xpath mapped xml snippet when returning the error/warning data to the user. Default value is 200 characters.,
 * `includeWarnings`: User can choose to look for warnings and include them in the report. The default value is false.,
 * `parsedSchematronMap`: User can choose to supply the schematron map instead of a schematron file avoiding the parsing of the schematron file for multiple xml files or check for cache.
 * @returns Calls `getReturnObject` and gets the results (errors, warning, and ignored) as javascript objects
 */

async function validateAsync(xml, schematron, options = {}) {
    const resultInfo = {
        errors: [],
        warnings: [],
        ignored: []
    };
    initializeProcess(xml, schematron, options);
    return Promise.all(
        Object.keys(schematronMap.patternRuleMap).map(async (patternId) => {
            if (schematronMap.patternRuleMap.hasOwnProperty(patternId)) {
                const assertionInfo = Object.create({});
                assertionInfo.patternId = patternId;
                const rules = schematronMap.patternRuleMap[patternId];
                await processRulesAsync(rules, assertionInfo, resultInfo, options);
            }
        })
    ).then(() => {
        return getReturnObject(resultInfo);
    });
}

/**
 * @method initializeProcess Checks the validity of the supplied xml and schematron, and sets some global variables used across functions in this script.
 * This calls the `getSchematronMap` and assigns the value to schematronMap variable.
 * @param {string} xml string content of an xml file or path to an xml file
 * @param {string} schematron string content of a schematron file or path to a schematron file
 * @param {object} options object that takes parameters as seen in the `validate` method
 * @returns Sets the following variables with file scope
 * xml, schematron, xmlDoc,
 * schematronMap, namespaceMap,
 * patternRuleMap, ruleAssertionMap,
 * resourceDir, xmlSnippetMaxLength,
 * xpathSelect, errors, warnings,
 * ignored, and resultInfo
 */

function initializeProcess(xml, schematron, options) {
    try {
        xml = checkIfFileOrPath(xml);
        schematron = checkIfFileOrPath(schematron);
    }
    catch (err) {
        return err;
    }
    // Load xml doc
    options.xmlDoc = new Dom().parseFromString(xml);
    // Allowing users to send a parsed schematron map if testing multiple xml files with the same schematron
    schematronMap = getSchematronMap(schematron, options);
    // Extract data from parsed schematron object
    if (!_get(options, 'resourceDir')) {
        options.resourceDir = './';
    }
    if (!_get(options, 'xmlSnippetMaxLength')) {
        options.xmlSnippetMaxLength = 200;
    }
    // Create selector object, initialized with namespaces
    // Avoid using 'select' as a variable name as it is overused
    options.xpathSelect = xpath.useNamespaces(schematronMap.namespaceMap);
}

/**
 * @method getSchematronMap A method to get the schematron map from the provided schematron or utilize the data from cache or options object
 * @param {string} schematron String data of schematron
 * @param {object} options options object passed to `validate` or `validateAsync` method
 * @returns {object} parsed schemtaron map object
 */

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

/**
 * @method checkIfFileOrPath Checks if a given xml string is xml content or file path to the xml, and extracts the content and
 * assigns it back to the variable. If no data could be found or there is an issue to access the file, an error will be returned.
 * @param {string} givenString xml string or a path to the xml file (the xml can be an xml file or schematron file which is basically an xml)
 * @returns If it is a valid xml, returns it. If it is a file path, extracts the content and returns it. Otherwise throws appropriate error.
 */

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

/**
 * @method processRules Entry method to start processing schematron rules. Calls methods `getFailedAssertions` and `processFailedAssertions`
 * @param {Array} rules Array of ruleIds
 * @param {object} resultInfo Capturing the information of the rule to pass it on to reporting object
 * @param {object} options Passing on the options object passed to the `validate` or `validateAsync` methods
 */

function processRules(rules, assertionInfo, resultInfo, options) {
    for (let index = 0; index < rules.length; index++) {
        const ruleId = rules[index];
        const failedAssertions = getFailedAssertions(ruleId, assertionInfo, options);
        if (failedAssertions) {
            processFailedAssertions(failedAssertions, assertionInfo, resultInfo);
        }
    }
}

/**
 * @method processRulesAsync Entry method to start processing schematron rules. Calls methods `getFailedAssertions` and `processFailedAssertions`
 * This method is same as `processRules` except that this is an Async method wrapping a promise, and returning the promise
 * @param {Array} rules Array of ruleIds
 * @param {object} resultInfo Capturing the information of the rule to pass it on to reporting object
 * @param {object} options Passing on the options object passed to the `validate` or `validateAsync` methods
 */

async function processRulesAsync(rules, assertionInfo, resultInfo, options) {
    const rulesProcessed = new Promise((resolve, reject) => {
        try {
            for (let index = 0; index < rules.length; index++) {
                const ruleId = rules[index];
                const failedAssertions = getFailedAssertions(ruleId, assertionInfo, options);
                resolve (failedAssertions);
                if (failedAssertions) {
                    processFailedAssertions(failedAssertions, assertionInfo, resultInfo);
                }
            }
        }
        catch (promiseError) {
            reject (new Error (`Promise Error: ${promiseError}`));
        }
    });
    return rulesProcessed;
}

/**
 * @method getReturnObject Simple method to avoid redundancy of result object representation
 * @param {object} resultInfo carrying the arrays errors, warnings and ignored
 * @returns result object with information about overall errors, warnings and ignored
 */

function getReturnObject(resultInfo) {
    const { errors, warnings, ignored } = resultInfo;
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

function getErrorsAndWarnings(assertionObject, assertionInfo, resultInfo) {
    const { type, assertionId, test, simplifiedTest, description, results } = assertionObject;
    const { patternId, ruleId, context } = assertionInfo;
    const { errors, warnings } = resultInfo;
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
function getIgnored(assertionObject, assertionInfo, resultInfo) {
    const { type, assertionId, test, simplifiedTest, description, errorMessage } = assertionObject;
    const { patternId, ruleId, context } = assertionInfo;
    const { ignored } = resultInfo;
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
function getResults(assertionObject, assertionInfo, resultInfo) {
    const { results } = assertionObject;
    if (Array.isArray(results)) {
        getErrorsAndWarnings(assertionObject, assertionInfo, resultInfo);
    }
    else if (results.ignored) {
        getIgnored(assertionObject, assertionInfo, resultInfo);
    }
}
function processFailedAssertions(failedAssertions, assertionInfo, resultInfo) {
    for (let index = 0; index < failedAssertions.length; index++) {
        const assertionObject = failedAssertions[index];
        getResults(assertionObject, assertionInfo, resultInfo);
    }
}
function getFailedAssertions(ruleId, assertionInfo, options) {
    let failedAssertions;
    assertionInfo.ruleId = ruleId;
    const ruleObject = schematronMap.ruleAssertionMap[ruleId];
    if (!_get(ruleObject, 'abstract')) {
        const context = _get(ruleObject, 'context');
        assertionInfo.context = context;
        const assertionsAndExtensions = _get(ruleObject, 'assertionsAndExtensions') || [];
        failedAssertions = checkRule(context, assertionsAndExtensions, options);
    }
    return failedAssertions;
}

// Take the checkRule function out of validate function, and pass on the variable needed as parameters and options
function checkRule(context, assertionsAndExtensions, options) {
    // Context cache
    const failedAssertions = [];
    // Determine the sections within context, load selected section from cache if possible
    const selectedXml = getSelectXmlChunk(context, options);
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

function getSelectXmlChunk(context, options) {
    // Determine the sections within context, load selected section from cache if possible
    let selectedXml = contextMap[context];
    let contextModified = context;
    if (!selectedXml) {
        if (context) {
            if (context.indexOf('/')) {
                contextModified = '//' + context;
            }
            selectedXml = options.xpathSelect(contextModified, options.xmlDoc);
        }
        else {
            selectedXml = [options.xmlDoc];
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
        const newRuleObject = schematronMap.ruleAssertionMap[extensionRule];
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
        test = includeExternalDocument(test, options.resourceDir);
        if (originalTest !== test) {
            simplifiedTest = test;
        }
        if (level === 'error' || _get(options, 'includeWarnings')) {
            const testAssertionResponse = testAssertion(test, selectedXml, options.xpathSelect, options.xmlDoc, options.resourceDir, options.xmlSnippetMaxLength);
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
