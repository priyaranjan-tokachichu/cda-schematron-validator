const xpath = require('xpath');
const Dom = require('@xmldom/xmldom').DOMParser;
const _get = require('lodash.get');
const checkIfFileOrPath = require('./checkIfFileOrPath');
const getSchematronMap = require('./getSchematronMap');
/**
 * @method initializeProcess Checks the validity of the supplied xml and schematron, and sets some global variables used across functions in this script.
 * This calls the `getSchematronMap` and assigns the value to schematronMap variable.
 * @param {string} xml string content of an xml file or path to an xml file
 * @param {string} schematron string content of a schematron file or path to a schematron file
 * @param {object} options object that takes parameters as seen in the `validate` method
 * @returns Sets the following variables with file scope
 * xml, schematron, xmlDoc,
 * schematronMap, namespaceMap,
 * patternRuleMap, ruleAssertionMap,
 * resourceDir, xmlSnippetMaxLength,
 * xpathSelect, errors, warnings,
 * ignored, and resultInfo
 */

function initializeProcess(xml, schematron, options) {
    try {
        xml = checkIfFileOrPath(xml);
        schematron = checkIfFileOrPath(schematron);
    }
    catch (err) {
        return err;
    }
    // Load xml doc
    options.xmlDoc = new Dom().parseFromString(xml);
    // Allowing users to send a parsed schematron map if testing multiple xml files with the same schematron
    options.schematronMap = getSchematronMap(schematron, options);
    // Extract data from parsed schematron object
    if (!_get(options, 'resourceDir')) {
        options.resourceDir = '../';
    }
    if (!_get(options, 'xmlSnippetMaxLength')) {
        options.xmlSnippetMaxLength = 200;
    }
    // Create selector object, initialized with namespaces
    // Avoid using 'select' as a variable name as it is overused
    options.xpathSelect = xpath.useNamespaces(options.schematronMap.namespaceMap);
}

module.exports = initializeProcess;
