const fs = require('fs');
const assert = require('node:assert/strict');
const { describe, it, beforeEach } = require('node:test');

const validator = require('../validator');

// Which xml file to test
const xmlPath = './test/test.xml';
// Which schematron to test against
const schematronPath = './test/test.sch';

const xml = fs.readFileSync(xmlPath, 'utf-8').toString();
const schematron = fs.readFileSync(schematronPath, 'utf-8').toString();

describe('Async validator should', function() {
    let results;
    it('return results', async function() {
        results = await validator.validateAsync(xml, schematron, { includeWarnings: true });
        assert.equal(typeof results, 'object');
    });
    it('return errorCount', function() {
        assert.equal(typeof results.errorCount, 'number');
    });
    it('return warningCount', function() {
        assert.equal(typeof results.warningCount, 'number');
    });
    it('return ignoredCount', function() {
        assert.equal(typeof results.ignoredCount, 'number');
    });
    it('return errors array', function() {
        assert.ok(Array.isArray(results.errors));
    });
    it('return warnings array', function() {
        assert.ok(Array.isArray(results.warnings));
    });
    it('return ignored array', function() {
        assert.ok(Array.isArray(results.ignored));
    });
    it('return matching errorCount', function() {
        assert.equal(results.errorCount, results.errors.length);
    });
    it('return matching warningCount', function() {
        assert.equal(results.warningCount, results.warnings.length);
    });
    it('return matching ignoredCount', function() {
        assert.equal(results.ignoredCount, results.ignored.length);
    });
    it('return correct number of errors', function() {
        assert.equal(results.errorCount, 16);
    });
    it('return correct number of warnings', function() {
        assert.equal(results.warningCount, 15);
    });
    it('return correct number of ignored', function() {
        assert.equal(results.ignoredCount, 1);
    });

    it('return similar results without warnings', async function() {
        results = await validator.validateAsync(xml, schematron);
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
    });

    it('return similar results given xml filepath', async function() {
        results = await validator.validateAsync(xmlPath, schematron, { includeWarnings: true });
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
    });

    it('return similar results given schematron filepath', async function() {
        results = await validator.validateAsync(xml, schematronPath, { includeWarnings: true });
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
    });

    it('return similar results given xml filepath and schematron filepath', async function() {
        results = await validator.validateAsync(xmlPath, schematronPath, { includeWarnings: true });
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
    });
});
