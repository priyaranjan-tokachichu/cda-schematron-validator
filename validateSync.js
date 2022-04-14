// crypto is now built-in node
const initializeProcess = require('./lib/initializeProcess');
const processRules = require('./lib/processRules');
const getReturnObject = require('./lib/getReturnObject');

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

function validateSync(xml, schematron, options = {}) {
    const resultInfo = {
        errors: [],
        warnings: [],
        ignored: []
    };
    options.parsedMap = parsedMap;
    options.contextMap = contextMap;
    options.schematronMap = schematronMap;
    initializeProcess(xml, schematron, options);
    for (const patternId in options.schematronMap.patternRuleMap) {
        if (!options.schematronMap.patternRuleMap.hasOwnProperty(patternId)) {
            continue;
        }
        const assertionInfo = Object.create({});
        assertionInfo.patternId = patternId;
        const rules = options.schematronMap.patternRuleMap[patternId];
        processRules(rules, assertionInfo, resultInfo, options);
    }
    return getReturnObject(resultInfo);
}

module.exports = {
    validateSync,
    clearCache
};
