const _get = require('lodash.get');
const includeExternalDocument = require('./includeExternalDocument');
const testAssertion = require('./testAssertion');
const getAssertionObject = require('./getAssertionObject');
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

module.exports = processAssertions;
