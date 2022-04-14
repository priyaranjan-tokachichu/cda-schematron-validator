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
