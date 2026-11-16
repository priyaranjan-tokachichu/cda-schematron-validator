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

describe('Async file list schematron validation should', function() {
    let results;
    it('return results', async function() {
        results = await validator.validateFileListAsync([xml, xml], schematron, options);
        assert.equal(results.length, 2);
        assert.ok([0, 1].includes(results[0].fileIndex));
        assert.ok([0, 1].includes(results[1].fileIndex));
    });
    it('return correct number of errors', function() {
        assert.equal(results[0].errorCount, 1);
        assert.equal(results[1].errorCount, 1);
    });
    it('return correct number of warnings', function() {
        assert.equal(results[0].warningCount, 6);
        assert.equal(results[1].warningCount, 6);
    });
    it('return correct number of ignored', function() {
        assert.equal(results[0].ignoredCount, 0);
        assert.equal(results[1].ignoredCount, 0);
    });
});

describe('Async xml data object schematron validation should', function() {
    let results;
    it('return results', async function() {
        results = await validator.validateFileObjectAsync({file_one: xml, file_two: xml}, schematron, options);
        assert.equal(results.length, 2);
        assert.ok(['file_one', 'file_two'].includes(results[0].fileInfo));
        assert.ok(['file_one', 'file_two'].includes(results[1].fileInfo));
    });
    it('return correct number of errors', function() {
        assert.equal(results[0].errorCount, 1);
        assert.equal(results[1].errorCount, 1);
    });
    it('return correct number of warnings', function() {
        assert.equal(results[0].warningCount, 6);
        assert.equal(results[1].warningCount, 6);
    });
    it('return correct number of ignored', function() {
        assert.equal(results[0].ignoredCount, 0);
        assert.equal(results[1].ignoredCount, 0);
    });
});
