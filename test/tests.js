const fs = require('fs');
const assert = require('node:assert/strict');
const { describe, it, beforeEach } = require('node:test');

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
    it('return results', function(context, done) {
        results = validator.validate(xml, schematron, { includeWarnings: true });
        assert.equal(typeof results, 'object');
        done();
    });
    it('return errorCount', function(context, done) {
        assert.equal(typeof results.errorCount, 'number');
        done();
    });
    it('return warningCount', function(context, done) {
        assert.equal(typeof results.warningCount, 'number');
        done();
    });
    it('return ignoredCount', function(context, done) {
        assert.equal(typeof results.ignoredCount, 'number');
        done();
    });
    it('return errors array', function(context, done) {
        assert.ok(Array.isArray(results.errors));
        done();
    });
    it('return warnings array', function(context, done) {
        assert.ok(Array.isArray(results.warnings));
        done();
    });
    it('return ignored array', function(context, done) {
        assert.ok(Array.isArray(results.ignored));
        done();
    });
    it('return matching errorCount', function(context, done) {
        assert.equal(results.errorCount, results.errors.length);
        done();
    });
    it('return matching warningCount', function(context, done) {
        assert.equal(results.warningCount, results.warnings.length);
        done();
    });
    it('return matching ignoredCount', function(context, done) {
        assert.equal(results.ignoredCount, results.ignored.length);
        done();
    });
    it('return correct number of errors', function(context, done) {
        assert.equal(results.errorCount, 16);
        done();
    });
    it('return correct number of warnings', function(context, done) {
        assert.equal(results.warningCount, 15);
        done();
    });
    it('return correct number of ignored', function(context, done) {
        assert.equal(results.ignoredCount, 1);
        done();
    });

    it('return similar results without warnings', function(context, done) {
        results = validator.validate(xml, schematron);
        assert.equal(typeof results, 'object');
        assert.equal(typeof results.errorCount, 'number');
        assert.equal(typeof results.warningCount, 'number');
        assert.equal(typeof results.ignoredCount, 'number');
        assert.ok(Array.isArray(results.errors));
        assert.ok(Array.isArray(results.warnings));
        assert.ok(Array.isArray(results.ignored));
        assert.equal(results.errorCount, results.errors.length);
        assert.equal(results.warningCount, results.warnings.length);
        assert.equal(results.ignoredCount, results.ignored.length);
        assert.equal(results.errorCount, 16);
        assert.equal(results.warningCount, 0);
        assert.equal(results.ignoredCount, 1);
        done();
    });

    it('return similar results given xml filepath', function(context, done) {
        results = validator.validate(xmlPath, schematron, { includeWarnings: true });
        assert.equal(typeof results, 'object');
        assert.equal(typeof results.errorCount, 'number');
        assert.equal(typeof results.warningCount, 'number');
        assert.equal(typeof results.ignoredCount, 'number');
        assert.ok(Array.isArray(results.errors));
        assert.ok(Array.isArray(results.warnings));
        assert.ok(Array.isArray(results.ignored));
        assert.equal(results.errorCount, results.errors.length);
        assert.equal(results.warningCount, results.warnings.length);
        assert.equal(results.ignoredCount, results.ignored.length);
        assert.equal(results.errorCount, 16);
        assert.equal(results.warningCount, 15);
        assert.equal(results.ignoredCount, 1);
        done();
    });

    it('return similar results given schematron filepath', function(context, done) {
        results = validator.validate(xml, schematronPath, { includeWarnings: true });
        assert.equal(typeof results, 'object');
        assert.equal(typeof results.errorCount, 'number');
        assert.equal(typeof results.warningCount, 'number');
        assert.equal(typeof results.ignoredCount, 'number');
        assert.ok(Array.isArray(results.errors));
        assert.ok(Array.isArray(results.warnings));
        assert.ok(Array.isArray(results.ignored));
        assert.equal(results.errorCount, results.errors.length);
        assert.equal(results.warningCount, results.warnings.length);
        assert.equal(results.ignoredCount, results.ignored.length);
        assert.equal(results.errorCount, 16);
        assert.equal(results.warningCount, 15);
        assert.equal(results.ignoredCount, 1);
        done();
    });

    it('return similar results given xml filepath and schematron filepath', function(context, done) {
        results = validator.validate(xmlPath, schematronPath, { includeWarnings: true });
        assert.equal(typeof results, 'object');
        assert.equal(typeof results.errorCount, 'number');
        assert.equal(typeof results.warningCount, 'number');
        assert.equal(typeof results.ignoredCount, 'number');
        assert.ok(Array.isArray(results.errors));
        assert.ok(Array.isArray(results.warnings));
        assert.ok(Array.isArray(results.ignored));
        assert.equal(results.errorCount, results.errors.length);
        assert.equal(results.warningCount, results.warnings.length);
        assert.equal(results.ignoredCount, results.ignored.length);
        assert.equal(results.errorCount, 16);
        assert.equal(results.warningCount, 15);
        assert.equal(results.ignoredCount, 1);
        done();
    });
});
