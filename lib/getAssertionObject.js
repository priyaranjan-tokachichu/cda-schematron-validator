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
