const fs = require('fs');
const expect = require('chai').expect;

const validator = require('../validator');

// Which xml file to test
const xmlPath = './test/test.xml';
// Which schematron to test against
const schematronPath = './test/test.sch';

const xml = fs.readFileSync(xmlPath, 'utf-8').toString();
const schematron = fs.readFileSync(schematronPath, 'utf-8').toString();

describe('Validator should', function() {
    let results;
    it('return results', async function() {
        results = await validator.validateAsync(xml, schematron, { includeWarnings: true });
        expect(results).to.be.an('object');
    });
    it('return errorCount', function() {
        expect(results.errorCount).to.be.a('number');
    });
    it('return warningCount', function() {
        expect(results.warningCount).to.be.a('number');
    });
    it('return ignoredCount', function() {
        expect(results.ignoredCount).to.be.a('number');
    });
    it('return errors array', function() {
        expect(results.errors).to.be.a('array');
    });
    it('return warnings array', function() {
        expect(results.warnings).to.be.a('array');
    });
    it('return ignored array', function() {
        expect(results.ignored).to.be.a('array');
    });
    it('return matching errorCount', function() {
        expect(results.errorCount).to.be.equal(results.errors.length);
    });
    it('return matching warningCount', function() {
        expect(results.warningCount).to.be.equal(results.warnings.length);
    });
    it('return matching ignoredCount', function() {
        expect(results.ignoredCount).to.be.equal(results.ignored.length);
    });
    it('return correct number of errors', function() {
        expect(results.errorCount).to.be.equal(16);
    });
    it('return correct number of warnings', function() {
        expect(results.warningCount).to.be.equal(15);
    });
    it('return correct number of ignored', function() {
        expect(results.ignoredCount).to.be.equal(1);
    });

    it('return similar results without warnings', async function() {
        results = await validator.validateAsync(xml, schematron);
        expect(results).to.be.an('object');
        expect(results.errorCount).to.be.a('number');
        expect(results.warningCount).to.be.a('number');
        expect(results.ignoredCount).to.be.a('number');
        expect(results.errors).to.be.a('array');
        expect(results.warnings).to.be.a('array');
        expect(results.ignored).to.be.a('array');
        expect(results.errorCount).to.be.equal(results.errors.length);
        expect(results.warningCount).to.be.equal(results.warnings.length);
        expect(results.ignoredCount).to.be.equal(results.ignored.length);
        expect(results.errorCount).to.be.equal(16);
        expect(results.warningCount).to.be.equal(0);
        expect(results.ignoredCount).to.be.equal(1);
    });

    it('return similar results given xml filepath', async function() {
        results = await validator.validateAsync(xmlPath, schematron, { includeWarnings: true });
        expect(results).to.be.an('object');
        expect(results.errorCount).to.be.a('number');
        expect(results.warningCount).to.be.a('number');
        expect(results.ignoredCount).to.be.a('number');
        expect(results.errors).to.be.a('array');
        expect(results.warnings).to.be.a('array');
        expect(results.ignored).to.be.a('array');
        expect(results.errorCount).to.be.equal(results.errors.length);
        expect(results.warningCount).to.be.equal(results.warnings.length);
        expect(results.ignoredCount).to.be.equal(results.ignored.length);
        expect(results.errorCount).to.be.equal(16);
        expect(results.warningCount).to.be.equal(15);
        expect(results.ignoredCount).to.be.equal(1);
    });

    it('return similar results given schematron filepath', async function() {
        results = await validator.validateAsync(xml, schematronPath, { includeWarnings: true });
        expect(results).to.be.an('object');
        expect(results.errorCount).to.be.a('number');
        expect(results.warningCount).to.be.a('number');
        expect(results.ignoredCount).to.be.a('number');
        expect(results.errors).to.be.a('array');
        expect(results.warnings).to.be.a('array');
        expect(results.ignored).to.be.a('array');
        expect(results.errorCount).to.be.equal(results.errors.length);
        expect(results.warningCount).to.be.equal(results.warnings.length);
        expect(results.ignoredCount).to.be.equal(results.ignored.length);
        expect(results.errorCount).to.be.equal(16);
        expect(results.warningCount).to.be.equal(15);
        expect(results.ignoredCount).to.be.equal(1);
    });

    it('return similar results given xml filepath and schematron filepath', async function() {
        results = await validator.validateAsync(xmlPath, schematronPath, { includeWarnings: true });
        expect(results).to.be.an('object');
        expect(results.errorCount).to.be.a('number');
        expect(results.warningCount).to.be.a('number');
        expect(results.ignoredCount).to.be.a('number');
        expect(results.errors).to.be.a('array');
        expect(results.warnings).to.be.a('array');
        expect(results.ignored).to.be.a('array');
        expect(results.errorCount).to.be.equal(results.errors.length);
        expect(results.warningCount).to.be.equal(results.warnings.length);
        expect(results.ignoredCount).to.be.equal(results.ignored.length);
        expect(results.errorCount).to.be.equal(16);
        expect(results.warningCount).to.be.equal(15);
        expect(results.ignoredCount).to.be.equal(1);
    });
});
