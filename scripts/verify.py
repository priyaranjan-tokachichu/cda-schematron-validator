#!/usr/bin/env python3
"""Produce reviewable local verification evidence without storing submitted XML."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
checks = []
def run(name, command):
    result = subprocess.run(command, cwd=root, text=True, capture_output=True)
    if result.returncode:
        print(result.stdout[-4000:]); print(result.stderr[-2000:])
        raise SystemExit(name+' failed')
    checks.append({'name': name, 'status': 'passed'})
    print(name+': passed', flush=True)
    return result.stdout

node = run('JavaScript API, isolation, and HTTP client tests', ['npm', 'test'])
node_count = int(re.search(r'tests (\d+)', node).group(1))
audit = json.loads(run('npm dependency audit', ['npm', 'audit', '--json']))
assert audit['metadata']['vulnerabilities']['total'] == 0
run('TypeScript declaration check', ['npx', '--yes', '--package', 'typescript', 'tsc', '--noEmit', '--strict', '--target', 'es2022', 'validator.d.ts'])
run('Java service and official-profile tests', ['mvn', '-q', '-f', 'service/pom.xml', 'verify'])
java_count = 0
for report in (root/'service/target/surefire-reports').glob('TEST-*.xml'):
    suite = ET.parse(report).getroot()
    assert int(suite.attrib['failures']) == int(suite.attrib['errors']) == int(suite.attrib['skipped']) == 0
    java_count += int(suite.attrib['tests'])
run('Compose configuration', ['docker', 'compose', 'config', '--quiet'])
run('Official upstream release check', ['python3', 'scripts/check-upstream.py'])
json.loads((root/'service/web/openapi.json').read_text())
checks.append({'name': 'OpenAPI JSON syntax', 'status': 'passed'})

base_url = os.environ.get('CDA_VALIDATOR_URL', 'http://127.0.0.1:'+os.environ.get('CDA_VALIDATOR_PORT', '17643')).rstrip('/')
with urllib.request.urlopen(base_url+'/health/ready',timeout=5) as response:
    ready=json.load(response)
assert ready['profiles'] == 2
checks.append({'name': 'Container readiness with both profiles loaded', 'status': 'passed'})
cli=subprocess.run(['node','bin/cda-validate.js','service/web/sample.xml','--url',base_url,'--json'],cwd=root,text=True,capture_output=True)
assert cli.returncode==1
result=json.loads(cli.stdout)
assert result['status']=='invalid' and result['executionComplete'] and not result['validationComplete']
assert any(f['stage']=='xsd' for f in result['findings'])
assert any(f['stage']=='schematron' and 'xpath' in f for f in result['findings'])
checks.append({'name': 'CLI, HTTP findings, locations, exit code, and upstream coverage', 'status': 'passed'})
container=subprocess.check_output(['docker','compose','ps','-q','validator'],cwd=root,text=True).strip()
inspection=json.loads(subprocess.check_output(['docker','inspect',container],text=True))[0]
checks.append({'name': 'Non-root, read-only container', 'status': 'passed' if inspection['Config']['User']=='10001:10001' and inspection['HostConfig']['ReadonlyRootfs'] else 'failed'})
assert checks[-1]['status']=='passed'
browser_file=root/'reports/browser-verification.json'
evidence={'verifiedAt':datetime.now(timezone.utc).isoformat(),'checks':checks,'javascriptTests':node_count,'javaTests':java_count,
          'browserChecks':json.loads(browser_file.read_text())['checks'] if browser_file.exists() else [],
          'npmVulnerabilities':audit['metadata']['vulnerabilities'],'nodeVersion':subprocess.check_output(['node','--version'],text=True).strip(),
          'container':{'architecture':inspection['Platform'] if 'Platform' in inspection else 'linux','user':inspection['Config']['User'],
                       'readOnlyRoot':inspection['HostConfig']['ReadonlyRootfs'],'memoryBytes':inspection['HostConfig']['Memory'],
                       'nanoCpus':inspection['HostConfig']['NanoCpus'],'health':inspection['State'].get('Health',{}).get('Status'),
                       'startupLog':subprocess.check_output(['docker','compose','logs','--no-log-prefix','--tail','1','validator'],cwd=root,text=True).strip()},
          'exampleResult':{k:result[k] for k in ['status','validationComplete','executionComplete','summary','profile','engine']}}
(root/'reports').mkdir(exist_ok=True)
(root/'reports/verification.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(f'Verified {node_count} JavaScript tests and {java_count} Java tests. Evidence saved to reports/verification.json')
