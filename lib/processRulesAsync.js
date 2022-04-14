const getFailedAssertions = require('./getFailedAssertions');
const processFailedAssertions = require('./processFailedAssertions');
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

module.exports = processRulesAsync;
