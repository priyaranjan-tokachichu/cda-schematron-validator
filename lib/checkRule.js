const getSelectXmlChunk = require('./getSelectXmlChunk');
const processAssertions = require('./processAssertions');
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

function processExtension(assertionAndExtensionObject, context, options) {
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

module.exports = checkRule;
