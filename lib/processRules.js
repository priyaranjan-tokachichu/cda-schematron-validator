const getFailedAssertions = require('./getFailedAssertions');
const processFailedAssertions = require('./processFailedAssertions');
/**
 * @method processRules Entry method to start processing schematron rules. Calls methods `getFailedAssertions` and `processFailedAssertions`
 * @param {Array} rules Array of ruleIds
 * @param {object} resultInfo Object that hosts the errors, warnings and ignored arrays to store the results
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

module.exports = processRules;
