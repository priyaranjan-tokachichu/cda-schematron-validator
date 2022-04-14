const getErrorsAndWarnings = require('./getErrorsAndWarnings');
const getIgnored = require('./getIgnored');
function getResults(assertionObject, assertionInfo, resultInfo) {
    const { results } = assertionObject;
    if (Array.isArray(results)) {
        getErrorsAndWarnings(assertionObject, assertionInfo, resultInfo);
    }
    else if (results.ignored) {
        getIgnored(assertionObject, assertionInfo, resultInfo);
    }
}

module.exports = getResults;
