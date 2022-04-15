// crypto is now built-in node
const initializeProcess = require('./lib/initializeProcess');
const processRulesAsync = require('./lib/processRulesAsync');
const getReturnObject = require('./lib/getReturnObject');

// Parsed object cache
let parsedMap = Object.create({});
let contextMap = Object.create({});
let schematronMap = Object.create({});

function clearCache() {
    parsedMap = Object.create({});
    contextMap = Object.create({});
    schematronMap = Object.create({});
}

/**
 * @method validateAsync The entry method called to validate an xml with schematron. Calls `initializeProcess` and `processRulesAsync` methods, and gets the results.
 * This is similar to `validate` method except that it uses asynchronous `processRulesAsync` method.
 * @param {string} xml string content of an xml file or path to an xml file
 * @param {string} schematron string content of a schematron file or path to a schematron file
 * @param {object} options object that takes parameters
 * `resourceDir`: Where additional documents with added rules such as value set restrictions are present. Default value is the same directory of the code being executed './'.,
 * `xmlSnippetMaxLength`: Restrict the length of the xpath mapped xml snippet when returning the error/warning data to the user. Default value is 200 characters.,
 * `includeWarnings`: User can choose to look for warnings and include them in the report. The default value is false.,
 * `parsedSchematronMap`: User can choose to supply the schematron map instead of a schematron file avoiding the parsing of the schematron file for multiple xml files or check for cache.
 * @returns Calls `getReturnObject` and gets the results (errors, warning, and ignored) as javascript objects
 */

async function validateAsync(xml, schematron, options = {}) {
    const resultInfo = {
        errors: [],
        warnings: [],
        ignored: []
    };
    options.parsedMap = parsedMap;
    options.contextMap = contextMap;
    options.schematronMap = schematronMap;
    initializeProcess(xml, schematron, options);
    return Promise.all(
        Object.keys(options.schematronMap.patternRuleMap).map(async (patternId) => {
            if (options.schematronMap.patternRuleMap.hasOwnProperty(patternId)) {
                const assertionInfo = Object.create({});
                assertionInfo.patternId = patternId;
                const rules = options.schematronMap.patternRuleMap[patternId];
                await processRulesAsync(rules, assertionInfo, resultInfo, options);
            }
        })
    ).then(() => {
        return getReturnObject(resultInfo);
    });
}

/**
 * @method validateFileListAsync Run the validateAsync method on each xml file of the array provided, and combine the results into an array and return it.
 * It is important to know that the results could be in a different order than the submitted files. We can't reliably identify which error belongs to which file.
 * The property `fileIndex` of each result object will provide the index of the xml data to which the error belongs to.
 * @param {array} xmlArray array of string content of an xml file or path to an xml file
  * @param {string} schematron string content of a schematron file or path to a schematron file
 * @param {object} options object that takes parameters
 * `resourceDir`: Where additional documents with added rules such as value set restrictions are present. Default value is the same directory of the code being executed './'.,
 * `xmlSnippetMaxLength`: Restrict the length of the xpath mapped xml snippet when returning the error/warning data to the user. Default value is 200 characters.,
 * `includeWarnings`: User can choose to look for warnings and include them in the report. The default value is false.,
 * `parsedSchematronMap`: User can choose to supply the schematron map instead of a schematron file avoiding the parsing of the schematron file for multiple xml files or check for cache.
 * @returns {array} gets array of return objects with results
 */

async function validateFileListAsync(xmlArray, schematron, options = {}) {
    const resultInfo = [];
    return Promise.all(
        xmlArray.map(async (xml, index) => {
            const resultObject = await validateAsync(xml, schematron, options);
            resultObject.fileIndex = index;
            resultInfo.push(resultObject);
            console.log(`Number of files processed: ${resultInfo.length}`);
            console.log(`Processed file index: ${index}`);
        })
    ).then(() => {
        return resultInfo;
    });
}

/**
 * @method validateFileObjectAsync Run the validateAsync method on each xml file of the xml data object provided, and combine the results into an array and return it.
 * It is important to know that the results could be in a different order than the submitted files. We can't reliably identify which error belongs to which file.
 * The property `fileInfo` of each result object will provide the information (probably an identifier or the name of the xml) the xml data to which the error belongs to.
 * @param {object} xmlDataObject object with key identifying the xml and value to be the xml file data or xml file path
  * @param {string} schematron string content of a schematron file or path to a schematron file
 * @param {object} options object that takes parameters
 * `resourceDir`: Where additional documents with added rules such as value set restrictions are present. Default value is the same directory of the code being executed './'.,
 * `xmlSnippetMaxLength`: Restrict the length of the xpath mapped xml snippet when returning the error/warning data to the user. Default value is 200 characters.,
 * `includeWarnings`: User can choose to look for warnings and include them in the report. The default value is false.,
 * `parsedSchematronMap`: User can choose to supply the schematron map instead of a schematron file avoiding the parsing of the schematron file for multiple xml files or check for cache.
 * @returns {array} gets array of return objects with results
 */

async function validateFileObjectAsync(xmlDataObject, schematron, options = {}) {
    const resultInfo = [];
    return Promise.all(
        Object.entries(xmlDataObject).map(async ([xmlFileInfo, xml]) => {
            const resultObject = await validateAsync(xml, schematron, options);
            resultObject.fileInfo = xmlFileInfo;
            resultInfo.push(resultObject);
            console.log(`Number of files processed: ${resultInfo.length}`);
            console.log(`Processed file info: ${xmlFileInfo}`);
        })
    ).then(() => {
        return resultInfo;
    });
}

module.exports = {
    validateAsync,
    validateFileListAsync,
    validateFileObjectAsync,
    clearCache
};
