#!/usr/bin/env python3
"""Regenerate the reviewed CarePlan base-root supplement from pinned HL7 packages."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--packages', required=True, type=Path, help='Local FHIR package cache')
args = parser.parse_args()
base = args.packages
core = base / 'hl7.cda.uv.core#2.0.3-sd/package'
tho = base / 'hl7.terminology.r5#7.1.0/package'
definitions, sources, profiles = {}, {}, {}
for directory in [core, tho]:
    for path in sorted(directory.glob('*.json')):
        if path.name.startswith(('ValueSet-', 'CodeSystem-')):
            resource = json.loads(path.read_text())
            definitions[resource['url']] = resource, path


def record(path):
    sources[str(path.relative_to(base))] = hashlib.sha256(path.read_bytes()).hexdigest()


def flatten(concepts):
    for concept in concepts:
        yield concept
        yield from flatten(concept.get('concept', []))


def enumeration(canonical):
    resource, path = definitions[canonical.split('|')[0]]
    record(path)
    values = []
    for group in resource['compose']['include']:
        if set(group) - {'system', 'version', 'concept'}:
            raise ValueError('Unreviewed terminology recipe')
        system, path = definitions[group['system']]
        record(path)
        oid = next(x['value'][8:] for x in system.get('identifier', []) if x['value'].startswith('urn:oid:'))
        concepts = group.get('concept')
        if concepts is None:
            if system['content'] != 'complete':
                raise ValueError('Incomplete code system')
            concepts = list(flatten(system['concept']))
        values.extend({'code': c['code'], 'system': oid} for c in concepts)
    return values


for name in ['Act', 'Observation']:
    path = core / ('StructureDefinition-' + name + '.json')
    resource = json.loads(path.read_text())
    record(path)
    elements, paths = [], {name: '.'}
    for element in resource['snapshot']['element'][1:]:
        parent = element['id'].rsplit('.', 1)[0]
        part = element['id'].split('.')[-1]
        attribute = 'xmlAttr' in element.get('representation', [])
        prefix = 'cda:'
        for extension in element.get('extension', []):
            if extension['url'].endswith('/xml-name'):
                part = extension['valueString']
            if extension['url'].endswith('/xml-namespace'):
                if extension['valueUri'] != 'urn:hl7-org:sdtc':
                    raise ValueError('Unreviewed XML namespace')
                prefix = 'sdtc:'
        xpath = (paths[parent] + '/' if parent != name else '') + ('@' if attribute else prefix) + part
        paths[element['id']] = xpath
        item = {'id': element['id'], 'path': xpath, 'parent': paths[parent], 'attribute': attribute,
                'min': element.get('min', 0), 'max': element.get('max', '*')}
        fixed = [v for k, v in element.items() if k.startswith('fixed')]
        if fixed:
            item['fixed'] = fixed[0]
        if any(t['code'].endswith('/II') for t in element.get('type', [])):
            item['iiRootOrNullFlavor'] = True
        if element.get('constraint'):
            if any(c['key'] != 'II-1' for c in element['constraint']):
                raise ValueError('Unreviewed invariant')
            item['iiRootOrNullFlavor'] = True
        binding = element.get('binding')
        if binding and binding['strength'] == 'required' and not fixed:
            item['binding'] = binding['valueSet']
            if binding['valueSet'] == 'http://hl7.org/fhir/ValueSet/all-languages':
                item['grammar'] = True
            else:
                item['members'] = enumeration(binding['valueSet'])
            item['primitive'] = attribute or any(t['code'].endswith('/CS') for t in element.get('type', []))
        elements.append(item)
    profiles[name] = elements
record(base / 'hl7.cda.us.ccda#5.0.0/package/StructureDefinition-CarePlanAct.json')
result = {'scope': 'Base Act/Observation root constraints for the three CarePlan type unions; complements the pinned XSD and Schematron, not a general StructureDefinition validator.',
          'packageSources': sources, 'profiles': profiles}
target = ROOT / 'service/src/main/resources/coverage/careplan-base.json'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(result, indent=2) + '\n')
print('Generated', target.relative_to(ROOT), 'from', len(sources), 'pinned source files')
