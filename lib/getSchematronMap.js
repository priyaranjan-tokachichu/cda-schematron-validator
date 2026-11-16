const Dom = require('@xmldom/xmldom').DOMParser;
const crypto = require('crypto');
const parseSchematron = require('./parseSchematron');
/**
 * @method getSchematronMap A method to get the schematron map from the provided schematron or utilize the data from cache or options object
 * @param {string} schematron String data of schematron
 * @param {object} options Options passed to the validator and any other variables passed between the methods
 * @returns {object} parsed schemtaron map object
 */

function getSchematronMap(schematron, options = {}) {
    if (options?.parsedSchematronMap) {
        return options?.parsedSchematronMap;
    }
    const hash = crypto
        .createHash('sha256')
        .update(schematron)
        .update(String(Boolean(options.includeWarnings)))
        .digest('hex');
    if (options.parsedMap[hash]) {
        return options.parsedMap[hash];
    }
    // If not in cache
    // Load schematron doc
    const schematronDoc = new Dom().parseFromString(schematron);
    // Parse schematron
    if (Object.keys(options.parsedMap).length >= 32) {
        delete options.parsedMap[Object.keys(options.parsedMap)[0]];
    }
    options.parsedMap[hash] = parseSchematron(schematronDoc, options);
    return options.parsedMap[hash];
}

module.exports = getSchematronMap;
