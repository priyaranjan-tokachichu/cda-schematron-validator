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
