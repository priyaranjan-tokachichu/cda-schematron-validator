const assert = require('node:assert/strict');
const { describe, it, beforeEach } = require('node:test');
const validator = require('../validator');
const Dom = require('@xmldom/xmldom').DOMParser;
const sch = '<schema xmlns="http://purl.oclc.org/dsdl/schematron"><phase id="errors"><active pattern="p"/></phase><pattern id="p"><rule id="r" context="item"><assert id="a" test="@ok=\'yes\'">SHALL be yes</assert></rule></pattern></schema>';
const good = '<root><item ok="yes"/></root>';
const bad = '<root><item ok="no"/></root>';
describe('Independent document validation', function() {
    beforeEach(function() { validator.clearCache(); validator.clearCacheAsync(); });
    it('does not reuse nodes from the previous document', function() {
        assert.equal(validator.validate(good, sch).errorCount, 0);
        assert.equal(validator.validate(bad, sch).errorCount, 1);
        assert.equal(validator.validate(good, sch).errorCount, 0);
    });
    it('preserves distinct documents in an async batch and does not mutate options', async function() {
        const options = Object.freeze({ includeWarnings: true });
        const results = await validator.validateFileListAsync([good, bad, good], sch, options);
        assert.deepEqual(results.map((r) => r.errorCount), [0, 1, 0]);
        assert.deepEqual(results.map((r) => r.fileIndex), [0, 1, 2]);
    });
    it('supports multiple rules without IDs', function() {
        const noIds = sch.replace('id="r"', '').replace('</pattern>', '<rule context="other"><assert test="true()">SHALL pass</assert></rule></pattern>');
        assert.equal(validator.validate(bad, noIds).errorCount, 1);
    });
    it('settles an async pattern with no rules', async function() {
        const result = await validator.validateAsync('<root/>', '<schema xmlns="http://purl.oclc.org/dsdl/schematron"><pattern id="empty"/></schema>');
        assert.equal(result.errorCount, 0);
    });
    it('uses a provided parsed map without requiring a schematron argument', function() {
        const parsedSchematronMap = validator.parseSchematron(new Dom().parseFromString(sch));
        assert.equal(validator.validate(bad, null, { parsedSchematronMap }).errorCount, 1);
    });
    it('throws useful errors for missing files and empty input', function() {
        assert.throws(() => validator.validate('/definitely-not-a-document.xml', sch));
        assert.throws(() => validator.validate('', sch), /Expected XML content/);
    });
});
