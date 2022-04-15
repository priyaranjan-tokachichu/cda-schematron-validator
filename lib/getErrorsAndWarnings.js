/**
 * @method getErrorsAndWarnings Given the information update errors and warnings array in resultInfo
 * @param {object} assertionObject Provides the assertion and the results of the test
 * @param {object} assertionInfo Information about the assertion
 * @param {object} resultInfo Arrays to store error and warning info
 */

function getErrorsAndWarnings(assertionObject, assertionInfo, resultInfo) {
    const { type, assertionId, test, simplifiedTest, description, results } = assertionObject;
    const { patternId, ruleId, context } = assertionInfo;
    const { errors, warnings } = resultInfo;
    for (let index = 0; index < results.length; index++) {
        const resultObject = results[index];
        const { result, line, path, xml } = resultObject;
        // If the xpath did not return a result (result=false), then there should be
        // and error or warning
        if (!result) {
            const obj = {
                type,
                test,
                simplifiedTest,
                description,
                line,
                path,
                patternId,
                ruleId,
                assertionId,
                context,
                xml
            };
            if (type === 'error') {
                errors.push(obj);
            }
            else {
                warnings.push(obj);
            }
        }
    }
}

module.exports = getErrorsAndWarnings;
