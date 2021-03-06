const fs = require('fs');
const expect = require('chai').expect;

const validator = require('../validator');
const path = require('path');
// Which xml file to test
const xmlPath = path.resolve(__dirname, './test.xml');
// Which schematron to test against
const schematronPath = path.resolve(__dirname, './test.sch');
const xml = fs.readFileSync(xmlPath, 'utf-8').toString();
const schematron = fs.readFileSync(schematronPath, 'utf-8').toString();

describe('Classic validator should', function() {
    let results;
    it('return results', function(done) {
        results = validator.validate(xml, schematron, { includeWarnings: true });
        expect(results).to.be.an('object');
        done();
    });
    it('return errorCount', function(done) {
        expect(results.errorCount).to.be.a('number');
        done();
    });
    it('return warningCount', function(done) {
        expect(results.warningCount).to.be.a('number');
        done();
    });
    it('return ignoredCount', function(done) {
        expect(results.ignoredCount).to.be.a('number');
        done();
    });
    it('return errors array', function(done) {
        expect(results.errors).to.be.a('array');
        done();
    });
    it('return warnings array', function(done) {
        expect(results.warnings).to.be.a('array');
        done();
    });
    it('return ignored array', function(done) {
        expect(results.ignored).to.be.a('array');
        done();
    });
    it('return matching errorCount', function(done) {
        expect(results.errorCount).to.be.equal(results.errors.length);
        done();
    });
    it('return matching warningCount', function(done) {
        expect(results.warningCount).to.be.equal(results.warnings.length);
        done();
    });
    it('return matching ignoredCount', function(done) {
        expect(results.ignoredCount).to.be.equal(results.ignored.length);
        done();
    });
    it('return correct number of errors', function(done) {
        expect(results.errorCount).to.be.equal(16);
        done();
    });
    it('return correct number of warnings', function(done) {
        expect(results.warningCount).to.be.equal(15);
        done();
    });
    it('return correct number of ignored', function(done) {
        expect(results.ignoredCount).to.be.equal(1);
        done();
    });

    it('return similar results without warnings', function(done) {
        results = validator.validate(xml, schematron);
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
        done();
    });

    it('return similar results given xml filepath', function(done) {
        results = validator.validate(xmlPath, schematron, { includeWarnings: true });
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
        done();
    });

    it('return similar results given schematron filepath', function(done) {
        results = validator.validate(xml, schematronPath, { includeWarnings: true });
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
        done();
    });

    it('return similar results given xml filepath and schematron filepath', function(done) {
        results = validator.validate(xmlPath, schematronPath, { includeWarnings: true });
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
        done();
    });
});
