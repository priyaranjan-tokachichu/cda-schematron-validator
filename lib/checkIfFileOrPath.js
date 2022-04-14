const fs = require('fs');
/**
 * @method checkIfFileOrPath Checks if a given xml string is xml content or file path to the xml, and extracts the content and
 * assigns it back to the variable. If no data could be found or there is an issue to access the file, an error will be returned.
 * @param {string} givenString xml string or a path to the xml file (the xml can be an xml file or schematron file which is basically an xml)
 * @returns If it is a valid xml, returns it. If it is a file path, extracts the content and returns it. Otherwise throws appropriate error.
 */

function checkIfFileOrPath(givenString) {
    // If not valid xml, it might be a filepath
    // Adding explicit check to make it clear
    if (!givenString) {
        return new Error('No data found in the xml or schematron');
    }
    if (givenString.trim().indexOf('<') === -1) {
        try {
            givenString = fs.readFileSync(givenString, 'utf-8').toString();
        }
        catch (err) {
            // If no valid xml found, inform user, and return immediately
            console.log('No valid xml or schematron could be found');
            return err;
        }
    }
    return givenString;
}

module.exports = checkIfFileOrPath;
