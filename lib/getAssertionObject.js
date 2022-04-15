/**
 * @method getAssertionObject A simple wrapper to output assertionObject with the right properties as it is used by
 * multiple mthods
 * @param {string} level 'error' or 'warning'
 * @param {string} id rule id
 * @param {string} originalTest test information
 * @param {string} simplifiedTest if the originalTest has an addition from an external file
 * @param {string} description description of the error or warning
 * @returns {object} returns object with the properties type, assertionId, test, simplifiedTest, description
 */

function getAssertionObject(level, id, originalTest, simplifiedTest, description) {
    const assertionObject = {
        type: level,
        assertionId: id,
        test: originalTest,
        simplifiedTest: simplifiedTest,
        description: description
    };
    return assertionObject;
}

module.exports = getAssertionObject;
