const _get = require('lodash.get');
const checkRule = require('./checkRule');
function getFailedAssertions(ruleId, assertionInfo, options) {
    let failedAssertions;
    assertionInfo.ruleId = ruleId;
    const ruleObject = options.schematronMap.ruleAssertionMap[ruleId];
    if (!_get(ruleObject, 'abstract')) {
        const context = _get(ruleObject, 'context');
        assertionInfo.context = context;
        const assertionsAndExtensions = _get(ruleObject, 'assertionsAndExtensions') || [];
        failedAssertions = checkRule(context, assertionsAndExtensions, options);
    }
    return failedAssertions;
}

module.exports = getFailedAssertions;
