'use strict';

/** Modern HTTP client; the existing in-process Schematron APIs remain available. */
module.exports = function createClient({ baseUrl = 'http://127.0.0.1:8080', token, timeoutMs = 35000 } = {}) {
    const base = new URL(baseUrl);
    if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password) {
        throw new TypeError('Use an HTTP(S) service URL without embedded credentials');
    }
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) throw new TypeError('timeoutMs must be positive');
    async function request(path, body) {
        const response = await fetch(new URL(path, base), {
            method: body ? 'POST' : 'GET',
            headers: Object.assign({ Accept: 'application/json' }, body ? { 'Content-Type': 'application/json' } : {}, token ? { Authorization: 'Bearer ' + token } : {}),
            body: body ? JSON.stringify(body) : undefined,
            redirect: 'error',
            signal: AbortSignal.timeout(timeoutMs)
        });
        const report = await response.json();
        if (!response.ok) {
            const error = new Error(report.error || 'Validation service returned HTTP ' + response.status);
            error.status = response.status;
            error.report = report;
            throw error;
        }
        return report;
    }
    return Object.freeze({
        validate(xml, { profile = 'ccda-5.0', includeWarnings = true } = {}) {
            if (typeof xml !== 'string' || !xml.trim()) throw new TypeError('xml must be non-empty XML text');
            return request('/v1/validate', { xml, profile, includeWarnings });
        },
        profiles: () => request('/v1/profiles'),
        version: () => request('/v1/version')
    });
};
