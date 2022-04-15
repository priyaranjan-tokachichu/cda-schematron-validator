/**
 * @method getSelectXmlChunk Use xpath to get xml chunks from the xml based on the queried context
 * @param {string} context Used to query the xml using xpath
 * @param {object} options Options passed to the validator and any other variables passed between the methods
 * @returns {array} xpath selected xml chunks array
 */

function getSelectXmlChunk(context, options) {
    // Determine the sections within context, load selected section from cache if possible
    let selectedXml = options.contextMap[context];
    let contextModified = context;
    if (!selectedXml) {
        if (context) {
            if (context.indexOf('/')) {
                contextModified = '//' + context;
            }
            selectedXml = options.xpathSelect(contextModified, options.xmlDoc);
        }
        else {
            selectedXml = [options.xmlDoc];
        }
        options.contextMap[context] = selectedXml;
    }
    return selectedXml;
}

module.exports = getSelectXmlChunk;
