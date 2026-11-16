const getFailedAssertions = require('./getFailedAssertions');
const processFailedAssertions = require('./processFailedAssertions');
/**
 * @method processRulesAsync Entry method to start processing schematron rules. Calls methods `getFailedAssertions` and `processFailedAssertions`
 * This method is same as `processRules` except that this is an Async method wrapping a promise, and returning the promise
 * @param {Array} rules Array of ruleIds
 * @param {object} resultInfo Object that hosts the errors, warnings and ignored arrays to store the results
 * @param {object} options Passing on the options object passed to the `validate` or `validateAsync` methods
 */

async function processRulesAsync(rules, assertionInfo, resultInfo, options) {
    for (const ruleId of rules) {
        const failedAssertions = getFailedAssertions(ruleId, assertionInfo, options);
        if (failedAssertions) processFailedAssertions(failedAssertions, assertionInfo, resultInfo);
    }
}

module.exports = processRulesAsync;
