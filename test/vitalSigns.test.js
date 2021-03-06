const fs = require('fs');
const expect = require('chai').expect;

const validator = require('../validator');
const path = require('path');

// Which xml file to test
const xmlPath = path.resolve(__dirname, './vitalSigns.xml');
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
        expect(results).to.be.an('object');
    });
    it('return correct number of errors', function() {
        expect(results.errorCount).to.be.equal(1);
    });
    it('return correct number of warnings', function() {
        expect(results.warningCount).to.be.equal(6);
    });
    it('return correct number of ignored', function() {
        expect(results.ignoredCount).to.be.equal(0);
    });
});
