#!/usr/bin/env python3
"""Prepare reviewed public HL7/CDC expansions in an operator folder, never in the application image."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
PRIORITY = 'http://terminology.hl7.org/ValueSet/v3-ActPriority'
PRIORITY_CS = 'http://terminology.hl7.org/CodeSystem/v3-ActPriority'
ODH = 'http://terminology.hl7.org/CodeSystem/PHOccupationalDataForHealthODH'
CMS = 'https://www.cms.gov/Medicare/Coding/place-of-service-codes/Place_of_Service_Code_Set'
CMS_SOURCE = 'http://terminology.hl7.org/ValueSet/CMSPlaceOfServiceCodes'
CMS_TARGET = 'http://hl7.org/cda/us/ccda/ValueSet/CMSPlaceOfServiceCodes'
OCCUPATIONAL = ['http://phinvads.cdc.gov/fhir/ValueSet/2.16.840.1.114222.4.11.' + suffix for suffix in ('7900', '7901')]
WANTED = {PRIORITY, PRIORITY_CS, CMS_SOURCE, CMS, ODH, *OCCUPATIONAL,
          'http://terminology.hl7.org/ValueSet/v3-ActPriorityCallback',
          'http://terminology.hl7.org/ValueSet/v3-xEncounterAdmissionUrgency'}


def flatten(nodes):
    result = set()
    for node in nodes:
        if node['code'] in result:
            raise ValueError('Duplicate source concept')
        result.add(node['code'])
        nested = flatten(node.get('concept', []))
        if result & nested:
            raise ValueError('Duplicate source concept')
        result |= nested
    return result


def descendants(nodes, code):
    for node in nodes:
        if node['code'] == code:
            return flatten([node])
        found = descendants(node.get('concept', []), code)
        if found:
            return found
    return set()


def expand(url, resources, seen=frozenset()):
    if url in seen:
        raise ValueError('Value-set import cycle')
    resource = resources[url]
    if resource.get('compose', {}).get('exclude'):
        raise ValueError('This reviewed recipe does not support exclusions')
    result = set()
    for include in resource['compose']['include']:
        if set(include) - {'system', 'version', 'concept', 'filter', 'valueSet'}:
            raise ValueError('Unreviewed include fields')
        if include.get('concept'):
            if set(include) - {'system', 'version', 'concept'}:
                raise ValueError('Unreviewed explicit include combination')
            system = include['system']
            version = include.get('version', resources.get(system, {}).get('version', 'unspecified'))
            values = {(system, c['code'], version) for c in include['concept']}
            if len(values) != len(include['concept']):
                raise ValueError('Duplicate explicit concept')
        elif include.get('filter'):
            if set(include) - {'system', 'version', 'filter'}:
                raise ValueError('Unreviewed filter combination')
            if include['system'] != PRIORITY_CS or len(include['filter']) != 1:
                raise ValueError('Unreviewed hierarchy filter')
            rule = include['filter'][0]
            system = resources[PRIORITY_CS]
            if system.get('content') != 'complete' or rule != {'property': 'concept', 'op': 'is-a', 'value': 'CS'}:
                raise ValueError('Unreviewed ActPriority hierarchy')
            codes = descendants(system['concept'], 'CS')
            if not codes:
                raise ValueError('Missing callback hierarchy')
            values = {(PRIORITY_CS, code, system['version']) for code in codes}
        elif include.get('valueSet'):
            if set(include) != {'valueSet'} or len(include['valueSet']) != 1:
                raise ValueError('Unreviewed include/import combination')
            values = expand(include['valueSet'][0], resources, seen | {url})
        elif include.get('system') == CMS:
            if set(include) - {'system', 'version'}:
                raise ValueError('Unreviewed CMS include combination')
            system = resources[CMS]
            if system.get('content') != 'complete':
                raise ValueError('CMS code system is incomplete')
            values = {(CMS, code, system['version']) for code in flatten(system['concept'])}
        else:
            raise ValueError('Unsupported source include')
        result |= values
    return result


def prepare(cache, output, download=False, grammars=False):
    cache, output = Path(cache), Path(output)
    cache.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / 'profiles/terminology-sources.lock.json').read_text())
    resources, provenance, oids = {}, {}, {}
    for source in lock['sources']:
        archive = cache / source['file']
        valid = archive.exists() and archive.stat().st_size <= 40 * 1024 * 1024 and hashlib.sha256(archive.read_bytes()).hexdigest() == source['sha256']
        if not valid:
            if not download:
                raise ValueError('Source is missing or changed; use --download to fetch the pinned public package')
            with urllib.request.urlopen(source['url'], timeout=60) as response:
                data = response.read(40 * 1024 * 1024 + 1)
            if hashlib.sha256(data).hexdigest() != source['sha256']:
                raise ValueError('Public package checksum mismatch')
            archive.write_bytes(data)
        with tarfile.open(archive) as package:
            index = package.getmember('package/.index.json')
            if index.size > 10 * 1024 * 1024:
                raise ValueError('Package index exceeds supported bounds')
            for item in json.load(package.extractfile(index))['files']:
                if item.get('url') not in WANTED and not (item.get('resourceType') == 'NamingSystem' and item.get('id') in ('PHOccupationalDataForHealthODH', 'CMSPlaceofServiceCodes')):
                    continue
                member = package.getmember('package/' + item['filename'])
                if member.size > 20 * 1024 * 1024:
                    raise ValueError('Source definition exceeds supported bounds')
                data = package.extractfile(member).read()
                resource = json.loads(data)
                if resource['resourceType'] == 'NamingSystem':
                    identifiers = resource['uniqueId']
                    oid = next((i['value'] for i in identifiers if i['type'] == 'oid' and i.get('preferred')), None)
                    for identifier in identifiers:
                        if identifier['type'] == 'uri' and oid:
                            oids[identifier['value']] = oid
                    continue
                url = resource['url']
                if url in resources and resources[url] != resource:
                    raise ValueError('Conflicting source definitions')
                resources[url] = resource
                provenance[url] = {'definitionSha256': hashlib.sha256(data).hexdigest(),
                                   'package': source['id'], 'packageVersion': source['version'],
                                   'packageSha256': source['sha256']}
    for system in (PRIORITY_CS, CMS):
        resource = resources[system]
        oid = next(i['value'][8:] for i in resource['identifier'] if i['value'].startswith('urn:oid:'))
        if system in oids and oids[system] != oid:
            raise ValueError('Conflicting authoritative OID mapping')
        oids[system] = oid
    if oids.get(ODH) != '2.16.840.1.114222.4.5.327':
        raise ValueError('ODH OID mapping requires review')
    grammar_data = {}
    if grammars:
        for name, info in lock['grammars'].items():
            for filename, checksum, url in [('file', 'sha256', 'source'), *([('extensionsFile', 'extensionsSha256', 'extensionsSource')] if name == 'language' else [])]:
                target = cache / info[filename]
                data = target.read_bytes() if target.exists() and target.stat().st_size <= 5 * 1024 * 1024 else b''
                if hashlib.sha256(data).hexdigest() != info[checksum]:
                    if not download:
                        raise ValueError('Grammar definition is missing or changed; use --download for pinned public definitions')
                    with urllib.request.urlopen(info[url], timeout=60) as response:
                        data = response.read(5 * 1024 * 1024 + 1)
                    if hashlib.sha256(data).hexdigest() != info[checksum]:
                        raise ValueError('Public grammar definition changed; review its edition and update the lock intentionally')
                    target.write_bytes(data)
                grammar_data[info[filename]] = data
    prepared = cache / 'prepared-expansions'
    prepared.mkdir(exist_ok=True)
    checklist, files, receipts = [], [], []
    for canonical, expected in [(PRIORITY, 15), (OCCUPATIONAL[0], 19254), (OCCUPATIONAL[1], 59583), (CMS_SOURCE, 52)]:
        resource = resources[canonical]
        pairs = expand(canonical, resources)
        if len(pairs) != expected or len({(system, code) for system, code, version in pairs}) != expected:
            raise ValueError('Reviewed expansion count changed')
        if canonical == PRIORITY and {code for system, code, version in pairs} != flatten(resources[PRIORITY_CS]['concept']):
            raise ValueError('ActPriority union changed')
        expansion = {'resourceType': 'ValueSet', 'url': canonical, 'version': resource['version'],
                     'expansion': {'total': expected, 'offset': 0, 'identifier': 'urn:sha256:' + provenance[canonical]['definitionSha256'],
                                   'contains': [{'system': system, 'code': code, 'version': version} for system, code, version in sorted(pairs)]}}
        filename = resource['id'] + '.json'
        if Path(filename).name != filename:
            raise ValueError('Invalid source resource identifier')
        path = prepared / filename
        path.write_text(json.dumps(expansion, separators=(',', ':')))
        target = CMS_TARGET if canonical == CMS_SOURCE else canonical
        checklist.append({'canonical': target, 'sourceCanonical': canonical, 'version': resource['version'], 'expectedCount': expected})
        receipts.append({'canonical': target, 'sourceCanonical': canonical, 'count': expected, **provenance[canonical]})
        files.append(path)
    spec = importlib.util.spec_from_file_location('fhir_import', ROOT / 'scripts/import-fhir-terminology.py')
    importer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(importer)
    result = importer.import_expansions(files, checklist, oids, output)
    if grammars:
        try:
            from terminology_pack import atomic_write
        except ModuleNotFoundError:
            from scripts.terminology_pack import atomic_write
        for filename, data in grammar_data.items():
            atomic_write(output / filename, data)
        result['grammars'] = lock['grammars']
        encoded = (json.dumps(result, indent=2) + '\n').encode()
        if len(encoded) > 2 * 1024 * 1024:
            raise ValueError('Pack manifest exceeds byte budget')
        atomic_write(output / 'manifest.json', encoded)
    # This receipt contains source identities and hashes, never membership data.
    (cache / 'public-source-receipts.json').write_text(json.dumps(receipts, indent=2) + '\n')
    return {'valueSets': len(result['valueSets']), 'publicExpansionsPrepared': receipts}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', default='.cache/coverage-sources')
    parser.add_argument('--output', default='terminology')
    parser.add_argument('--download', action='store_true', help='Download only checksum-pinned public HL7/CDC packages')
    parser.add_argument('--grammars', action='store_true', help='Also install pinned public UCUM and IANA definitions')
    args = parser.parse_args()
    print(json.dumps(prepare(args.cache, args.output, args.download, args.grammars), indent=2))
