const fs = require('fs');
const assert = require('node:assert/strict');
const { describe, it, beforeEach } = require('node:test');

const validator = require('../validator');
const path = require('path');

// Which xml file to test
const xmlPath = path.resolve(__dirname, './VitalSigns.xml');
// Which schematron to test against
const schematronPath = path.resolve(__dirname, './CDAR2_IG_CCDA_CLINNOTES_R1_DSTU2.1_2015AUG_Vol2_2019JUNwith_errata.sch');

const xml = fs.readFileSync(xmlPath, 'utf-8').toString();
const schematron = fs.readFileSync(schematronPath, 'utf-8').toString();
const options = {
    resourceDir: path.resolve(__dirname, './'),
    includeWarnings: true
};

describe('Vital Sign async schematron validation should', function() {
    let results;
    it('return results', async function() {
        results = await validator.validateAsync(xml, schematron, options);
        assert.equal(typeof results, 'object');
    });
    it('return correct number of errors', function() {
        assert.equal(results.errorCount, 1);
    });
    it('return correct number of warnings', function() {
        assert.equal(results.warningCount, 6);
    });
    it('return correct number of ignored', function() {
        assert.equal(results.ignoredCount, 0);
    });
});
