// jshint node:true
// jshint shadow:true

/**
 * Export all the main methods
 */

module.exports = {
    validate: require('./validateSync').validateSync,
    validateAsync: require('./validateAsync').validateAsync,
    validateFileListAsync: require('./validateAsync').validateFileListAsync,
    validateFileObjectAsync: require('./validateAsync').validateFileObjectAsync,
    clearCache: require('./validateSync').clearCache,
    clearCacheAsync: require('./validateAsync').clearCache,
    parseSchematron: require('./lib/parseSchematron')
};
