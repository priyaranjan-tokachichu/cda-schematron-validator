#!/usr/bin/env node
'use strict';
const fs = require('node:fs');
const createClient = require('../lib/createClient');

async function main() {
    const args = process.argv.slice(2);
    let file, profile = 'ccda-5.0', baseUrl = process.env.CDA_VALIDATOR_URL || 'http://127.0.0.1:17643';
    let json = false, includeWarnings = true;
    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--help' || args[i] === '-h') {
            console.log('Usage: cda-validate FILE|- [--profile ccda-5.0|ccda-2.1] [--url URL] [--json] [--no-warnings]\nSet CDA_VALIDATOR_TOKEN for authenticated services. Exit: 0 valid; 1 invalid; 2 incomplete or execution error.');
            return;
        }
        if (args[i] === '--profile' || args[i] === '--url') {
            const option = args[i], value = args[++i];
            if (!value) throw new Error('Missing value for ' + option);
            if (option === '--profile') profile = value; else baseUrl = value;
        } else if (args[i] === '--json') json = true;
        else if (args[i] === '--no-warnings') includeWarnings = false;
        else if (!file && (args[i] === '-' || !args[i].startsWith('-'))) file = args[i];
        else throw new Error('Unknown argument: ' + args[i]);
    }
    if (!file) throw new Error('An XML file or - for stdin is required. Use --help for usage.');
    const data = fs.readFileSync(file === '-' ? 0 : file);
    const xml = new TextDecoder('utf-8', { fatal: true }).decode(data);
    const report = await createClient({ baseUrl, token: process.env.CDA_VALIDATOR_TOKEN }).validate(xml, { profile, includeWarnings });
    if (json) console.log(JSON.stringify(report, null, 2));
    else {
        console.log(`${report.status.toUpperCase()} · ${report.profile.name} / ${report.profile.release} · ${report.summary.errors} errors · ${report.summary.warnings} warnings · ${report.durationMs} ms`);
        for (const f of report.findings) console.log(`${f.severity.toUpperCase()} [${f.stage}]${f.line ? ' line ' + f.line : ''}: ${f.message}`);
        if (!report.validationComplete) console.log('Coverage is incomplete; inspect the profile limitations and stage results.');
    }
    process.exitCode = report.status === 'invalid' ? 1 : report.validationComplete ? 0 : 2;
}
main().catch((error) => { console.error(error.message); process.exitCode = 2; });
