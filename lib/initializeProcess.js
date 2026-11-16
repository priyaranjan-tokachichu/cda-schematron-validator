const xpath = require('xpath');
const Dom = require('@xmldom/xmldom').DOMParser;
const checkIfFileOrPath = require('./checkIfFileOrPath');
const getSchematronMap = require('./getSchematronMap');
/**
 * @method initializeProcess Checks the validity of the supplied xml and schematron, and sets some global variables used across functions in this script.
 * This calls the `getSchematronMap` and assigns the value to schematronMap variable.
 * @param {string} xml string content of an xml file or path to an xml file
 * @param {string} schematron string content of a schematron file or path to a schematron file
 * @param {object} options object that takes parameters as seen in the `validate` method
 * Sets some method specific variables that will be passed to other methods using the options object
 * options.xmlDoc, options.schematronMap, options.resourceDir, options.xmlSnippetMaxLength, and options.xpathSelect
 */

function initializeProcess(xml, schematron, options) {
    xml = checkIfFileOrPath(xml);
    if (!options.parsedSchematronMap) schematron = checkIfFileOrPath(schematron);
    // Load xml doc
    options.xmlDoc = new Dom().parseFromString(xml);
    // Allowing users to send a parsed schematron map if testing multiple xml files with the same schematron
    options.schematronMap = getSchematronMap(schematron, options);
    // Extract data from parsed schematron object
    if (!options?.resourceDir) {
        options.resourceDir = '../';
    }
    if (options.xmlSnippetMaxLength === undefined) {
        options.xmlSnippetMaxLength = 200;
    }
    // Create selector object, initialized with namespaces
    // Avoid using 'select' as a variable name as it is overused
    options.xpathSelect = xpath.useNamespaces(options.schematronMap.namespaceMap);
}

module.exports = initializeProcess;
