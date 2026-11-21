#!/usr/bin/env python3
"""Compare targeted synthetic probes with a separately run HL7 FHIR validator.

Run the reference CLI on test/coverage-reference/*.xml with C-CDA 5.0.0,
-tx n/a and JSON Bundle output. This checks named constraints, not whole-report
or terminology equivalence. Neither tool sends clinical documents anywhere.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def compare(reference, base_url):
    outcomes = {}
    for entry in json.loads(Path(reference).read_text())['entry']:
        outcome = entry['resource']
        name = next(e['valueString'] for e in outcome['extension'] if e['url'].endswith('operationoutcome-file'))
        outcomes[Path(name).stem] = outcome['issue']
    results = []
    for case in json.loads((ROOT / 'test/coverage-reference/cases.json').read_text()):
        name = case['case']
        source = ROOT / 'test/coverage-reference' / (name + '.xml')
        xml = source.read_bytes()
        request = urllib.request.Request(base_url.rstrip('/') + '/v1/validate', data=json.dumps({'xml': xml.decode(), 'profile': 'ccda-5.0', 'includeWarnings': True}).encode(), headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=45) as response:
            ours = json.load(response)
        findings = ours['findings']
        texts = [i.get('details', {}).get('text', '') for i in outcomes[name] if i['severity'] in ('error', 'fatal')]
        result = {**case, 'inputSha256': hashlib.sha256(xml).hexdigest()}
        if 'rule' in case:
            rule = case['rule']
            ref_failure = any('Constraint failed: ' + rule + ':' in t for t in texts)
            service_failure = any(f.get('ruleId') == rule and f['stage'] != 'coverage' for f in findings)
            unknown = any(f.get('ruleId', '').startswith(rule) and f['stage'] == 'coverage' for f in findings)
            result.update(referenceFailure=ref_failure, serviceFailure=service_failure, serviceReportsIndeterminateCoverage=unknown)
            if case['expectedFailure'] is None:
                assert unknown and not service_failure, result
            else:
                assert ref_failure == service_failure == case['expectedFailure'], result
            if 'highExpectedFailure' in case:
                high = 'active-high-ts-after-document'
                assert any('Constraint failed: ' + high + ':' in t for t in texts) == case['highExpectedFailure'], result
                assert any(f.get('ruleId') == high and f['stage'] != 'coverage' for f in findings) == case['highExpectedFailure'], result
        elif 'serviceRuleSuffix' in case:
            suffix = case['serviceRuleSuffix']
            if suffix.endswith('code-cardinality'):
                target = ('Observation' if 'Observation-' in suffix else 'Act') + '.code: minimum required = 1'
            elif suffix.endswith('II-1'):
                target = 'Constraint failed: II-1:'
            else:
                target = '#Act.typeId.root'
            ref_failure = any(target in text for text in texts)
            service_failure = any(f.get('ruleId', '').startswith('careplan-base-') and f['ruleId'].endswith(suffix) and f['severity'] == 'error' for f in findings)
            assert ref_failure and service_failure, (result, target)
            result.update(referenceFailure=ref_failure, serviceFailure=service_failure)
        else:
            service_failure = any(f.get('ruleId', '').startswith('careplan-base-') and f['severity'] == 'error' for f in findings)
            # Synthetic parent documents intentionally omit other required CCD content.
            # These probes establish only base-child union behavior.
            base_failure = any('StructureDefinition/Act|2.0.3-sd' in t or 'StructureDefinition/Observation|2.0.3-sd' in t for t in texts)
            assert not service_failure and not base_failure, (result, texts)
            result.update(serviceBaseChildFailure=False, referenceBaseChildFailure=False)
        results.append(result)
    return {'verifiedAt': datetime.now(timezone.utc).isoformat(), 'reference': 'HL7 FHIR Validator 7.0.6',
            'referenceSha256': '2686590539f0b3ba7683149b25d78249f6886c04e2c326e884c5049d8947df89',
            'referenceResultsSha256': hashlib.sha256(Path(reference).read_bytes()).hexdigest(),
            'profile': 'hl7.cda.us.ccda#5.0.0', 'terminology': 'Reference uses -tx n/a. No terminology or complete model-equivalence claim.',
            'scope': 'Targeted constraint comparisons on intentionally incomplete synthetic documents; unrelated whole-document findings are excluded.',
            'broadComparison': 'A separate full-CCD FHIR comparison with terminology disabled produced memberOf and slicing errors. It is inconclusive without equivalent terminology and is not counted as a pass or full-model equivalence.',
            'cases': results}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference-results', required=True)
    parser.add_argument('--base-url', default='http://127.0.0.1:17643')
    parser.add_argument('--output', default='reports/reference-comparison.json')
    args = parser.parse_args()
    result = compare(args.reference_results, args.base_url)
    Path(args.output).write_text(json.dumps(result, indent=2) + '\n')
    print('Passed', len(result['cases']), 'targeted reference comparisons; see scope limitations in output.')
