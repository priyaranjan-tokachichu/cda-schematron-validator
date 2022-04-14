const getResults = require('./getResults');
function processFailedAssertions(failedAssertions, assertionInfo, resultInfo) {
    for (let index = 0; index < failedAssertions.length; index++) {
        const assertionObject = failedAssertions[index];
        getResults(assertionObject, assertionInfo, resultInfo);
    }
}

module.exports = processFailedAssertions;
