/**
 * @method getReturnObject Simple method to avoid redundancy of result object representation
 * @param {object} resultInfo carrying the arrays errors, warnings and ignored
 * @returns result object with information about overall errors, warnings and ignored
 */

function getReturnObject(resultInfo) {
    const { errors, warnings, ignored } = resultInfo;
    const result = {
        errorCount: errors.length,
        warningCount: warnings.length,
        ignoredCount: ignored.length,
        errors: errors,
        warnings: warnings,
        ignored: ignored
    };
    return result;
}

module.exports = getReturnObject;
