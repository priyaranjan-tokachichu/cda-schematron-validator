// jshint node:true
// jshint shadow:true
module.exports = modifyTest;

const fs = require('fs');
const xpath = require('xpath');
const Dom = require('@xmldom/xmldom').DOMParser;
const path = require('path');

const loadedExternalDocuments = Object.create({});

function modifyTest(test, resourceDir) {
    let matches = /=document\((\'[-_.A-Za-z0-9]+\'|\"[-_.A-Za-z0-9]+\")\)/.exec(test);
    while (matches) {
        // String processing to select the non-regular predicate expression
        const equalInd = test.indexOf(matches[0]);
        let start = equalInd;
        let bracketDepth = 0;
        for (let i = equalInd; i >= 0; i--) {
            if (!bracketDepth && (test[i] === '[' || test[i] === ' ')) {
                start = i + 1;
                break;
            }
            if (test[i] === ']') {
                bracketDepth++;
            } else if (test[i] === '[') {
                bracketDepth--;
            }
        }

        let end = test.length;
        bracketDepth = 0;
        for (let i = start + matches[0].length; i < test.length; i++) {
            if (!bracketDepth && (test[i] === ']' || test[i] === ' ')) {
                end = i;
                break;
            }
            if (test[i] === '[') {
                bracketDepth++;
            } else if (test[i] === ']') {
                bracketDepth--;
            }
        }

        const predicate = test.slice(start, end);

        // Load external doc (load from "cache" if already loaded)
        const filepath = matches[1].slice(1, -1);
        let externalDoc;
        if (!loadedExternalDocuments[filepath]) {
            let externalXml = null;
            try {
                externalXml = fs.readFileSync(path.join(resourceDir, filepath), 'utf-8').toString();
            } catch (err) {
                throw new Error('No such file \'' + filepath + '\'');
            }
            externalDoc = new Dom().parseFromString(externalXml);
            loadedExternalDocuments[filepath] = externalDoc;
        } else {
            externalDoc = loadedExternalDocuments[filepath];
        }

        const externalXpath = test.slice(equalInd + matches[0].length, end);

        // Extract namespaces
        const defaultNamespaceKey = /([^(<>.\/)]+):[^(<>.\/)]+/.exec(externalXpath)[1];
        const externalNamespaceMap = externalDoc.lastChild._nsMap;
        const namespaceMap = {};
        for (const key in externalNamespaceMap) {
            if (externalNamespaceMap.hasOwnProperty(key)) {
                if (key) {
                    namespaceMap[key] = externalNamespaceMap[key];
                }
            }
        }
        namespaceMap[defaultNamespaceKey] = externalNamespaceMap[''];

        const externalSelect = xpath.useNamespaces(namespaceMap);

        // Create new predicate from extract values
        const values = [];
        const externalResults = externalSelect(externalXpath, externalDoc);
        for (let i = 0; i < externalResults.length; i++) {
            values.push(externalResults[i].value);
        }
        const lhv = predicate.slice(0, predicate.indexOf('=document('));
        let newPredicate = '(';
        for (let i = 0; i < values.length; i++) {
            newPredicate += lhv + '=\'' + values[i] + '\'';
            if (i < values.length - 1) {
                newPredicate += ' or ';
            }
        }
        newPredicate += ')';

        // Replace test
        test = test.slice(0, start) + newPredicate + test.slice(end);

        matches = /@[^\[\]]+=document\((\'[-_.A-Za-z0-9]+\'|\"[-_.A-Za-z0-9]+\")\)/.exec(test);
    }

    return test;
}
