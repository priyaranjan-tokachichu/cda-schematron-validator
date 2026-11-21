#!/usr/bin/env python3
"""Import complete, reviewed local FHIR ValueSet expansions; never contacts a terminology server."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
try:
    from terminology_pack import update_pack
except ModuleNotFoundError:
    from scripts.terminology_pack import update_pack


def load_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate JSON property')
            result[key] = value
        return result
    if Path(path).stat().st_size > 500 * 1024 * 1024:
        raise ValueError('Input exceeds supported size')
    return json.loads(Path(path).read_bytes(), object_pairs_hook=unique,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Non-finite JSON number')))


def import_expansions(inputs, expected, systems, output, replace=False):
    reviewed = {item['canonical']: item for item in expected}
    if len(reviewed) != len(expected) or not reviewed:
        raise ValueError('Expected expansions must be unique and nonempty')
    sources = {item.get('sourceCanonical', item['canonical']): item for item in expected}
    if len(sources) != len(expected):
        raise ValueError('Each reviewed source canonical must have one explicit target')
    for system, oid in systems.items():
        if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)+', oid):
            raise ValueError('Code systems must map to explicit CDA OIDs')
    prepared = {}
    for source in map(Path, inputs):
        resource = load_json(source)
        source_canonical = resource.get('url')
        if resource.get('resourceType') != 'ValueSet' or source_canonical not in sources:
            raise ValueError('Expected a reviewed FHIR ValueSet')
        review = sources[source_canonical]
        canonical = review['canonical']
        if not review.get('version') or resource.get('version') != review['version']:
            raise ValueError('ValueSet version does not match the reviewed edition')
        expansion = resource.get('expansion', {})
        count = review['expectedCount']
        if type(count) is not int or count <= 0 or expansion.get('total') != count or expansion.get('offset', 0) != 0:
            raise ValueError('Expansion total/offset does not match the reviewed complete count')
        if any(p.get('name') in ('limitedExpansion', 'incomplete') and p.get('valueBoolean') is True for p in expansion.get('parameter', [])):
            raise ValueError('Partial expansion is not supported')
        if any(e.get('url') in ('http://hl7.org/fhir/StructureDefinition/valueset-unclosed', 'http://hl7.org/fhir/StructureDefinition/valueset-toocostly') and e.get('valueBoolean') is True for e in expansion.get('extension', [])):
            raise ValueError('Unclosed or incomplete expansion cannot establish complete membership')
        pairs, editions = set(), {}
        def visit(concepts, inherited_system=None, inherited_version=None):
            for concept in concepts:
                system = concept.get('system', inherited_system)
                version = concept.get('version', inherited_version)
                if concept.get('abstract') is True:
                    raise ValueError('Request a flat expansion excluding abstract concepts')
                code = concept.get('code')
                if not isinstance(code, str) or not code or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in code):
                    raise ValueError('Invalid expansion code')
                if system not in systems:
                    raise ValueError('Missing explicit code-system OID mapping')
                key = systems[system] + '|' + code
                if len(key.encode('utf-16-le')) // 2 > 1024 or key in pairs:
                    raise ValueError('Duplicate or oversized membership key')
                pairs.add(key)
                editions.setdefault(system, set()).add(version or 'unspecified')
                visit(concept.get('contains', []), system, version)
        visit(expansion.get('contains', []))
        if len(pairs) != count:
            raise ValueError('Expansion is empty, partial, or count-inconsistent')
        data = (json.dumps(sorted(pairs), ensure_ascii=False, separators=(',', ':')) + '\n').encode()
        digest = hashlib.sha256(data).hexdigest()
        info = {'canonical': canonical, 'file': digest + '.json', 'count': count, 'sha256': digest,
                'source': source_canonical, 'canonicalAlias': canonical != source_canonical, 'sourceVersion': resource['version'],
                'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                'expansionIdentifier': expansion.get('identifier'), 'expansionTimestamp': expansion.get('timestamp'),
                'codeSystems': {system: sorted(versions) for system, versions in editions.items()},
                'codeSystemEditions': {systems[system]: sorted(v for v in versions if v != 'unspecified') for system, versions in editions.items()},
                'retrievedAt': datetime.fromtimestamp(source.stat().st_mtime, timezone.utc).isoformat()}
        if canonical in prepared:
            raise ValueError('Duplicate ValueSet input')
        prepared[canonical] = (info, data)
    if set(prepared) != set(reviewed):
        raise ValueError('A reviewed expansion is missing')
    return update_pack(output, prepared, replace)



if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', nargs='+', required=True)
    parser.add_argument('--expected', required=True, help='Reviewed [{canonical,version,expectedCount}] JSON')
    parser.add_argument('--systems', required=True, help='Reviewed {FHIR-system-URI:CDA-system-OID} JSON')
    parser.add_argument('--output', default='terminology')
    parser.add_argument('--replace', action='store_true')
    args = parser.parse_args()
    result = import_expansions(args.input, load_json(args.expected), load_json(args.systems), args.output, args.replace)
    print(json.dumps({'valueSets': len(result['valueSets']), 'concepts': sum(v['count'] for v in result['valueSets'])}))
