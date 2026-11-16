const assert = require('node:assert/strict');
const { describe, it, beforeEach } = require('node:test');
const http = require('node:http');
const { createClient } = require('../validator');
describe('Modern service client', function() {
    it('submits XML and options and preserves detailed reports', async function() {
        const server = http.createServer(async (req, res) => {
            let body = '';
            for await (const chunk of req) body += chunk;
            const parsed = JSON.parse(body);
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ xml: parsed.xml, profile: parsed.profile, includeWarnings: parsed.includeWarnings, authenticated: req.headers.authorization === 'Bearer test-token' }));
        });
        await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
        try {
            const report = await createClient({ baseUrl: `http://127.0.0.1:${server.address().port}`, token: 'test-token' }).validate('<root/>', { profile: 'ccda-2.1', includeWarnings: false });
            assert.deepEqual(report, { xml: '<root/>', profile: 'ccda-2.1', includeWarnings: false, authenticated: true });
        } finally { await new Promise((resolve) => server.close(resolve)); }
    });
    it('rejects embedded credentials and missing XML', function() {
        assert.throws(() => createClient({ baseUrl: 'https://user:password@example.com' }));
        assert.throws(() => createClient().validate(''));
    });
    it('preserves engine failure reports on HTTP errors', async function() {
        const server = http.createServer((req, res) => { res.writeHead(500, { 'Content-Type': 'application/json' }); res.end('{"validationComplete":false,"error":"Engine failure"}'); });
        await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
        try {
            let failure;
            try { await createClient({ baseUrl: `http://127.0.0.1:${server.address().port}` }).validate('<root/>'); } catch (error) { failure = error; }
            assert.equal(failure.status, 500); assert.equal(failure.report.validationComplete, false);
        } finally { await new Promise((resolve) => server.close(resolve)); }
    });
});
