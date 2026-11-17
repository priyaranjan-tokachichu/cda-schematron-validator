#!/usr/bin/env python3
"""Import authorized local SVS exports as checksum-pinned, offline membership indexes."""
import argparse
from datetime import datetime, timezone
import glob
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def import_exports(inputs, expected, output):
    expected = {item['oid']: item['expectedCount'] for item in expected if 'expectedCount' in item}
    output = Path(output)
    snapshots = {}
    for filename in inputs:
        source = Path(filename)
        if source.stat().st_size > 500 * 1024 * 1024:
            raise ValueError('SVS input exceeds supported size')
        raw = source.read_bytes()
        if b'<!DOCTYPE' in raw or b'<!ENTITY' in raw:
            raise ValueError('SVS exports must not contain DTDs or entities')
        root = ET.fromstring(raw)
        if root.tag != '{urn:ihe:iti:svs:2008}RetrieveMultipleValueSetsResponse':
            raise ValueError('Expected an IHE SVS RetrieveMultipleValueSetsResponse')
        for value_set in root.findall('{urn:ihe:iti:svs:2008}DescribedValueSet'):
            oid = value_set.get('ID', '')
            if oid not in expected:
                continue  # Only explicitly reviewed/count-checked exports are imported.
            pairs, systems = set(), {}
            for concept in value_set.findall('.//{urn:ihe:iti:svs:2008}Concept'):
                system, code = concept.get('codeSystem', ''), concept.get('code', '')
                if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)+', system) or not code or any(ord(c)<32 for c in code):
                    raise ValueError('Invalid SVS code/system')
                key = system + '|' + code
                if key in pairs:
                    raise ValueError('Duplicate code/system in SVS expansion')
                pairs.add(key)
                systems.setdefault(system, set()).add(concept.get('codeSystemVersion', 'unspecified'))
            if not pairs or len(pairs) != expected[oid]:
                raise ValueError(f'SVS count does not match reviewed count for {oid}: {len(pairs)} vs {expected[oid]}')
            data = (json.dumps(sorted(pairs), ensure_ascii=False, separators=(',', ':')) + '\n').encode()
            if oid in snapshots and snapshots[oid][1] != data:
                raise ValueError('Conflicting expansions for the same value set; select one explicitly')
            def child(name):
                return value_set.findtext('{urn:ihe:iti:svs:2008}' + name)
            info = {'canonical': 'http://cts.nlm.nih.gov/fhir/ValueSet/' + oid,
                    'file': oid + '.json', 'count': len(pairs), 'sha256': hashlib.sha256(data).hexdigest(),
                    'source': 'https://vsac.nlm.nih.gov/valueset/' + oid,
                    'sourceSha256': hashlib.sha256(raw).hexdigest(), 'sourceVersion': value_set.get('version'),
                    'definitionRevision': child('RevisionDate'), 'status': child('Status'),
                    'codeSystems': {k: sorted(v) for k, v in systems.items()},
                    'retrievedAt': datetime.fromtimestamp(source.stat().st_mtime, timezone.utc).isoformat()}
            snapshots[oid] = (info, data)
    if not snapshots:
        raise ValueError('No reviewed SVS expansions found')
    missing = set(expected) - set(snapshots)
    if missing:
        raise ValueError('Reviewed exports are missing: ' + ', '.join(sorted(missing)))
    # Validate all inputs before replacing the pack manifest. Data filenames include
    # their content hash so an old manifest never points to partially replaced data.
    output.mkdir(parents=True, exist_ok=True)
    for info, data in snapshots.values():
        info['file'] = info['file'][:-5] + '-' + info['sha256'] + '.json'
        target = output / info['file']
        target.write_bytes(data)
    manifest = {'schemaVersion': 1, 'valueSets': [info for info, _ in sorted(snapshots.values(), key=lambda x: x[0]['canonical'])]}
    temporary = output / 'manifest.json.tmp'
    temporary.write_text(json.dumps(manifest, indent=2) + '\n')
    temporary.replace(output / 'manifest.json')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-glob', required=True, help='Quoted glob for local SVS XML exports')
    parser.add_argument('--expected', required=True, help='Reviewed JSON array of {oid, expectedCount}')
    parser.add_argument('--output', default='.cache/terminology')
    args = parser.parse_args()
    manifest = import_exports(sorted(glob.glob(args.input_glob)), json.loads(Path(args.expected).read_text()), args.output)
    print(json.dumps({'valueSets': len(manifest['valueSets']), 'concepts': sum(v['count'] for v in manifest['valueSets']), 'output': args.output}))
