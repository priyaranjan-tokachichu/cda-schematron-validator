/**
 * @method getIgnored If there ws an erroneous rule, it results in the rule being ignored.
 * This method will add the ignored assertion info to the ignored array.
 * @param {object} assertionObject Carries the ignored assertion info
 * @param {object} assertionInfo carries the assertion info from pattern, rule and context
 * @param {object} resultInfo carries the ignored array
 */

function getIgnored(assertionObject, assertionInfo, resultInfo) {
    const { type, assertionId, test, simplifiedTest, description, errorMessage } = assertionObject;
    const { patternId, ruleId, context } = assertionInfo;
    const { ignored } = resultInfo;
    const obj = {
        errorMessage,
        type,
        test,
        simplifiedTest,
        description,
        patternId,
        ruleId,
        assertionId,
        context
    };
    ignored.push(obj);
}

module.exports = getIgnored;
