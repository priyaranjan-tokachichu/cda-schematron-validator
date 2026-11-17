#!/usr/bin/env python3
"""Remove the mixed-license legacy vocabulary from a public container asset copy."""
import hashlib
import json
from pathlib import Path
import sys


def package_assets(directory):
    root = Path(directory)
    manifest_file = root / 'manifest.json'
    manifest = json.loads(manifest_file.read_text())
    vocabulary = root / 'ccda-2.1/voc.xml'
    legacy = next(p for p in manifest['profiles'] if p['id'] == 'ccda-2.1')
    original_hash = legacy.get('operatorVocabulary', {}).get('sha256') or hashlib.sha256(vocabulary.read_bytes()).hexdigest()
    placeholder = b'<?xml version="1.0"?><systems xmlns="http://www.lantanagroup.com/voc"/>\n'
    vocabulary.write_bytes(placeholder)
    inventory_file = root / 'ccda-2.1/.files.json'
    inventory = json.loads(inventory_file.read_text())
    inventory['voc.xml'] = hashlib.sha256(placeholder).hexdigest()
    inventory_file.write_text(json.dumps(inventory, indent=2) + '\n')
    for profile in manifest['profiles']:
        if profile['id'] == 'ccda-2.1':
            profile['operatorVocabulary'] = {'file': 'ccda-2.1-voc.xml', 'sha256': original_hash,
                                             'reason': 'The upstream mixed-license vocabulary is operator-supplied; CPT content is not bundled.'}
            limitation = 'Install the authorized pinned C-CDA 2.1 vocabulary before using its Schematron checks.'
            if limitation not in profile['limitations']:
                profile['limitations'].append(limitation)
    manifest_file.write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    package_assets(sys.argv[1])
