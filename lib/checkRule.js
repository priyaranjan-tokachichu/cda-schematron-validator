const _get = require('lodash.get');
const getSelectXmlChunk = require('./getSelectXmlChunk');
const includeExternalDocument = require('./includeExternalDocument');
const testAssertion = require('./testAssertion');
const getAssertionObject = require('./getAssertionObject');
// Take the checkRule function out of validate function, and pass on the variable needed as parameters and options

/**
 * @method checkRule Validate assertions and extension against the provided context of the xml with the given options
 * @param {string} context Used to query the xml using xpath
 * @param {array} assertionsAndExtensions Each assertionsAndExtensions object is processed further based on the information in the object
 * @param {object} options Options passed to the validator and any other variables passed between the methods
 * @returns {array} Failed assertion objects are returned
 */

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
            const failedSubAssertions = processExtensions(assertionAndExtensionObject, context, options);
            failedAssertions.push(...failedSubAssertions);
        }
    }
    return failedAssertions;
}

/**
 * @method processAssertions When the assertionAndExtensionObject type is assertion process it
 * @param {object} assertionAndExtensionObject Assertion object with information to test against the selectedXml using testAssertion
 * @param {array} selectedXml array of selected xml based on context
 * @param {array} failedAssertions array to hold failedAssertions
 * @param {object} options Options passed to the validator and any other variables passed between the methods
 * @returns {array} Failed assertion objects are returned
 */

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

/**
 * @method processExtension When the assertionAndExtensionObject type is 'not an assertion' (extension) process it
 * Cannot have this as a separate file with a circular reference to checkRule export in a separate file. That will cause
 * the code to fail. So, these methods (checkRule and processExtension) should be in the same file.
 * @param {object} assertionAndExtensionObject Extension object with a rule is processed with checkRule
 * @param {string} context Used to query the xml using xpath
 * @param {object} options Options passed to the validator and any other variables passed between the methods
 * @returns {array} Failed assertion objects are returned
 */

function processExtensions(assertionAndExtensionObject, context, options) {
    const extensionRule = assertionAndExtensionObject.rule;
    let failedSubAssertions = [];
    if (extensionRule) {
        const newRuleObject = options.schematronMap.ruleAssertionMap[extensionRule];
        const subAssertionsAndExtensions = newRuleObject ? newRuleObject.assertionsAndExtensions : null;
        if (subAssertionsAndExtensions) {
            failedSubAssertions = checkRule(context, subAssertionsAndExtensions, options);
        }
    }
    return failedSubAssertions;
}

module.exports = {
    checkRule,
    processAssertions,
    processExtensions
};
