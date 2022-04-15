const getResults = require('./getResults');

/**
 * @method processFailedAssertions Get failed assertion object, get results and store in resultInfo
 * @param {array} failedAssertions Array of failed assertions
 * @param {object} assertionInfo Object with the assertion information
 * @param {object} resultInfo Object that hosts the errors, warnings and ignored arrays to store the results
 */

function processFailedAssertions(failedAssertions, assertionInfo, resultInfo) {
    for (let index = 0; index < failedAssertions.length; index++) {
        const assertionObject = failedAssertions[index];
        getResults(assertionObject, assertionInfo, resultInfo);
    }
}

module.exports = processFailedAssertions;
