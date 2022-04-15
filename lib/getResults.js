const getErrorsAndWarnings = require('./getErrorsAndWarnings');
const getIgnored = require('./getIgnored');

/**
 * @method getResults Get the results from the assertionObject and store the information in
 * errors, warnings and ignored arrays
 * @param {object} assertionObject Object with the assertion check results
 * @param {object} assertionInfo Object with the assertion information
 * @param {object} resultInfo Object that hosts the errors, warnings and ignored arrays to store the results
 */

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
