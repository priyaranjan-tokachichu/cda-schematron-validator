#!/usr/bin/env python3
"""Fetch immutable upstream validation assets. Never runs on document submission."""
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / 'service' / 'assets'
lock = json.loads((ROOT / 'profiles/sources.lock.json').read_text())
DEST.mkdir(parents=True, exist_ok=True)
for source in lock['sources']:
    target = DEST / source['id']
    marker = target / '.sha256'
    inventory_file = target / '.files.json'
    inventory = json.loads(inventory_file.read_text()) if inventory_file.exists() else {}
    cached_ok = inventory and all(
        (target / name).is_file() and hashlib.sha256((target / name).read_bytes()).hexdigest() == digest
        for name, digest in inventory.items()
    )
    if marker.exists() and marker.read_text().strip() == source['sha256'] and cached_ok:
        print('Verified cached asset:', source['id'])
        continue
    request = urllib.request.Request(source['url'], headers={'User-Agent': 'cda-validator-build'})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read(40 * 1024 * 1024 + 1)
    if hashlib.sha256(data).hexdigest() != source['sha256']:
        raise SystemExit('Checksum mismatch: ' + source['id'])
    target.mkdir(parents=True, exist_ok=True)
    inventory = {}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if sum(i.file_size for i in archive.infolist()) > 150 * 1024 * 1024:
            raise SystemExit('Archive exceeds extraction limit')
        for entry in archive.infolist():
            if entry.is_dir() or entry.filename.startswith('__MACOSX/'):
                continue
            output = (target / entry.filename).resolve()
            if not output.is_relative_to(target.resolve()):
                raise SystemExit('Unsafe archive path')
            output.parent.mkdir(parents=True, exist_ok=True)
            content = archive.read(entry)
            output.write_bytes(content)
            inventory[entry.filename] = hashlib.sha256(content).hexdigest()
    inventory_file.write_text(json.dumps(inventory, indent=2) + '\n')
    marker.write_text(source['sha256'] + '\n')
    print('Downloaded and verified:', source['id'], source['release'])

manifest = {
    'sources': lock['sources'],
    'profiles': [
        {'id': 'ccda-2.1', 'name': 'C-CDA 2.1', 'release': '2025SEP',
         'schematron': 'ccda-2.1/Consolidated CDA Templates for Clinical Notes (US Realm) DSTU R2.1.sch',
         'schema': 'cda-xsd/infrastructure/cda/CDA_SDTC.xsd',
         'limitations': ['Terminology checks are limited to the upstream voc.xml snapshot; external terminology services are not used.']},
        {'id': 'ccda-5.0', 'name': 'C-CDA 5.0', 'release': '2026JUL',
         'schematron': 'ccda-5.0/CCDA-5.0.sch',
         'schema': 'cda-xsd/infrastructure/cda/CDA_SDTC.xsd',
         'limitations': ['Generated Schematron is not equivalent to complete StructureDefinition or terminology validation.']}
    ]
}
report = json.loads((DEST / 'ccda-5.0/CCDA-5.0-Results.json').read_text())
manifest['profiles'][1]['generationCoverage'] = {k: len(v) for k, v in report.items()}
manifest['profiles'][1]['limitations'].extend(
    f'Upstream generation report: {len(v)} {k}.' for k, v in report.items() if len(v)
)
for profile in manifest['profiles']:
    profile['schemaRelease'] = '20260205_SDTC'
    profile['compilation'] = 'SchXslt 1.10.1; explicit phase severity; stable generated assertion IDs; XPath 1.0 backwards-compatible evaluation of these upstream releases'
    profile['artifactChecksums'] = {
        key: hashlib.sha256((DEST / profile[key]).read_bytes()).hexdigest()
        for key in ['schema', 'schematron']
    }
(DEST / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
