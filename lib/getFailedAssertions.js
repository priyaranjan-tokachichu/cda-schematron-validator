const _get = require('lodash.get');
const { checkRule } = require('./checkRule');

/**
 * @method getFailedAssertions Get assertions and extension from the rule object and check the rule against the context
 * @param {string} ruleId Rule identifier
 * @param {object} assertionInfo Object to store assertion info
 * @param {object} options Options passed to the validator and any other variables passed between the methods
 * @returns {array} array of failedAssertionObjects
 */

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
