#!/usr/bin/env python3
"""Derive local US Core code indexes from a reviewed full UMLS and LOINC release.

No terminology is downloaded, licensed, or redistributed by this command.
Source atom identifiers are used for SNOMED hierarchy traversal; UMLS CUIs
are never treated as SNOMED identifiers or as subsumption relationships.
"""
import argparse
from collections import Counter
import csv
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import tempfile
import zipfile
try:
    from terminology_pack import update_pack
except ModuleNotFoundError:
    from scripts.terminology_pack import update_pack

ROOT = Path(__file__).resolve().parents[1]
CONDITION = 'http://hl7.org/fhir/us/core/ValueSet/us-core-condition-code'
PROCEDURE = 'http://hl7.org/fhir/us/core/ValueSet/us-core-procedure-code'
SNOMED = '2.16.840.1.113883.6.96'
OIDS = {'CPT': '2.16.840.1.113883.6.12', 'CDT': '2.16.840.1.113883.6.13',
        'HCPCS': '2.16.840.1.113883.6.285', 'ICD10PCS': '2.16.840.1.113883.6.4',
        'ICD10CM': '2.16.840.1.113883.6.90', 'ICD9CM': '2.16.840.1.113883.6.103'}
FILTERS = {
    'CPT': ({'PT'}, r'(?:[0-9]{5}|[0-9]{4}[A-Z])'),
    'CDT': ({'PT', 'OP'}, r'D[0-9]{4}'),
    'HCPCS': ({'PT', 'OP'}, r'[A-Z][0-9]{4}'),
    'ICD10PCS': ({'PT'}, r'[0-9A-HJ-NP-Z]{7}'),
    'ICD10CM': (None, r'[A-Z][0-9][0-9A-Z](?:\.[A-Z0-9]{1,4})?'),
    'ICD9CM': (None, r'(?:[0-9]{2,3}(?:\.[0-9]{1,2})?|V[0-9]{2}(?:\.[0-9]{1,2})?|E[0-9]{3}(?:\.[0-9])?)'),
}


def sha256(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def rows(archive, table):
    """RRF parts split arbitrary bytes, including in the middle of a UTF-8 row."""
    names = sorted(n for n in archive.namelist() if n.startswith('2026AB/META/' + table + '.RRF.') and n.endswith('.gz'))
    expected = 3 if table == 'MRCONSO' else 5
    if len(names) != expected or len(set(names)) != len(names):
        raise ValueError('Incomplete or duplicate RRF fragments: ' + table)
    carry = b''
    for name in names:
        with archive.open(name) as member, gzip.GzipFile(fileobj=member) as stream:
            for piece in stream:
                line = carry + piece
                if len(line) > 1024 * 1024:
                    raise ValueError('RRF row exceeds supported bounds')
                if not line.endswith(b'\n'):
                    carry = line
                    continue
                carry = b''
                fields = line.rstrip(b'\r\n').decode('utf-8').split('|')
                if len(fields) != (19 if table == 'MRCONSO' else 10):
                    raise ValueError('Malformed RRF row: ' + table)
                yield fields
    if carry:
        raise ValueError('Truncated RRF table: ' + table)


def selected(source, tty, code):
    types, syntax = FILTERS[source]
    return (types is None or tty in types) and re.fullmatch(syntax, code) is not None


def derive(meta, review, loinc):
    codes = {source: set() for source in FILTERS}
    atoms, root_atoms = {}, {}
    source_rows = Counter()
    roots = set(review['snomedDescendants'])
    roots.add('160245001')
    with zipfile.ZipFile(meta) as archive:
        for f in rows(archive, 'MRCONSO'):
            source, tty, code = f[11], f[12], f[13]
            if source not in review['sources']:
                continue
            source_rows[source] += 1
            if source == 'SNOMEDCT_US':
                if f[7] in atoms and atoms[f[7]] != f[9]:
                    raise ValueError('Conflicting SNOMED source atom')
                atoms[f[7]] = f[9]
                if f[9] in roots:
                    root_atoms[f[7]] = f[9]
            elif selected(source, tty, code):
                codes[source].add(code)
        if dict(source_rows) != {s: v['rows'] for s, v in review['sources'].items()}:
            raise ValueError('Source row counts do not match the reviewed full release')
        if set(root_atoms.values()) != roots:
            raise ValueError('Required SNOMED root is missing')
        descendants = {root: {root} for root in roots}
        for f in rows(archive, 'MRHIER'):
            if f[4] != 'SNOMEDCT_US' or f[5] != 'isa':
                continue
            code = atoms.get(f[1])
            if not code:
                raise ValueError('Hierarchy references an unmapped source atom')
            for ancestor in f[6].split('.'):
                if ancestor in root_atoms:
                    descendants[root_atoms[ancestor]].add(code)
    for root, count in review['snomedDescendants'].items():
        if len(descendants[root]) != count:
            raise ValueError('SNOMED hierarchy count does not match the reviewed edition')
    loinc_review = json.loads((ROOT / 'profiles/terminology-sources.lock.json').read_text())['loincDocumentTypes']
    if sha256(loinc) != loinc_review['sha256']:
        raise ValueError('LOINC archive does not match the reviewed edition')
    with zipfile.ZipFile(loinc) as archive, archive.open('LoincTable/Loinc.csv') as member:
        loinc_codes = {row['LOINC_NUM'] for row in csv.DictReader(io.TextIOWrapper(member, encoding='utf-8-sig'))}
    if len(loinc_codes) != loinc_review['tableRows']:
        raise ValueError('LOINC table count does not match the reviewed release')
    conditions = set().union(*(descendants[root] for root in ('404684003', '243796009', '272379006')))
    conditions.add('160245001')
    condition = {SNOMED + '|' + code for code in conditions}
    for source in ('ICD10CM', 'ICD9CM'):
        condition.update(('2.16.840.1.113883.6.104' if source == 'ICD9CM' and re.fullmatch(r'[0-9]{2}(?:\.[0-9]{1,2})?', code) else OIDS[source]) + '|' + code for code in codes[source])
    procedure = {SNOMED + '|' + code for code in descendants['71388002']}
    for source in ('CPT', 'CDT', 'HCPCS', 'ICD10PCS'):
        procedure.update(OIDS[source] + '|' + code for code in codes[source])
    procedure.update('2.16.840.1.113883.6.1|' + code for code in loinc_codes)
    procedure_data = (json.dumps(sorted(SNOMED + '|' + c for c in descendants['71388002']), separators=(',', ':')) + '\n').encode()
    return {CONDITION: condition, PROCEDURE: procedure}, {'snomedProcedureMembershipSha256': hashlib.sha256(procedure_data).hexdigest(), 'sourceIdentifiers': {s: len(v) for s, v in codes.items()}, 'sourceRows': dict(source_rows)}


def install(source, loinc, output, cache, replace=False):
    review = json.loads((ROOT / 'profiles/umls-sources.lock.json').read_text())
    source, loinc = Path(source), Path(loinc)
    if source.stat().st_size > 8 * 1024**3 or sha256(source) != review['archiveSha256']:
        raise ValueError('Input does not match the reviewed UMLS full archive')
    cache = Path(cache)
    cache.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='umls-', dir=cache) as temporary:
        meta = Path(temporary) / 'meta.nlm'
        with zipfile.ZipFile(source) as archive:
            name = '2026AB-full/2026ab-1-meta.nlm'
            if archive.getinfo(name).file_size > 3 * 1024**3:
                raise ValueError('UMLS member exceeds supported bounds')
            with archive.open(name) as member, meta.open('wb') as destination:
                shutil.copyfileobj(member, destination, 1024 * 1024)
        values, provenance = derive(meta, review, loinc)
    prepared = {}
    for canonical, pairs in values.items():
        if len(pairs) != review['valueSetCounts'][canonical]:
            raise ValueError('Derived membership count differs from the reviewed recipe')
        data = (json.dumps(sorted(pairs), separators=(',', ':')) + '\n').encode()
        digest = hashlib.sha256(data).hexdigest()
        editions = {SNOMED: ['20260901', '2026-09', '2026-09-01', 'http://snomed.info/sct/731000124108/version/20260901'],
                    OIDS['CPT']: ['2026'], OIDS['CDT']: ['2025', 'CDT2025'], OIDS['HCPCS']: ['2026'],
                    OIDS['ICD10PCS']: ['2027'], OIDS['ICD10CM']: ['2027'], OIDS['ICD9CM']: ['2014'],
                    '2.16.840.1.113883.6.104': ['2014'], '2.16.840.1.113883.6.1': ['2.83']}
        used_systems = {pair.split('|', 1)[0] for pair in pairs}
        info = {'canonical': canonical, 'file': digest + '.json', 'sha256': digest, 'count': len(pairs),
                'source': canonical, 'sourceVersion': '9.0.0', 'sourceRelease': review['release'],
                'sourceSha256': review['archiveSha256'], 'loincSha256': sha256(loinc),
                'definition': review['definitions'][canonical],
                'derivation': 'Reviewed US Core 9.0.0 code-space union; UMLS source identifiers and source-asserted SNOMED isa paths; classifications exclude range headings and partial procedure axes. The shared ICD-9-CM FHIR URI maps diagnosis identifiers to CDA 6.103 and procedure identifiers to CDA 6.104. Standalone modifier and compositional-expression semantics remain indeterminate.',
                'unsupportedExpressions': [SNOMED], 'standaloneModifiersIndeterminate': True, 'provenance': provenance,
                'sourceEditions': {s: v['version'] for s, v in review['sources'].items()},
                'codeSystemEditions': {s: versions for s, versions in editions.items() if s in used_systems}}
        prepared[canonical] = (info, data)
    manifest_path = Path(output) / 'manifest.json'
    if manifest_path.exists():
        if not manifest_path.resolve().is_relative_to(Path(output).resolve()) or manifest_path.stat().st_size > 2 * 1024 * 1024:
            raise ValueError('Invalid existing manifest')
        existing = json.loads(manifest_path.read_bytes())
        for item in existing['valueSets']:
            if item['canonical'] == 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.80.28':
                if item['sha256'] != provenance['snomedProcedureMembershipSha256']:
                    raise ValueError('Existing Procedure snapshot differs from the reviewed UMLS hierarchy; review editions before import')
                # Add edition aliases only after exact independent membership
                # agreement. The original VSAC provenance remains intact.
                info = dict(item)
                info['codeSystemEditions'] = {SNOMED: ['2026-09', '20260901', '2026-09-01', 'http://snomed.info/sct/731000124108/version/20260901']}
                info['equivalenceEvidence'] = {'source': 'UMLS 2026AB SNOMED US isa hierarchy', 'sourceSha256': review['archiveSha256'], 'membershipSha256': item['sha256']}
                path = (Path(output) / item['file']).resolve()
                if not path.is_relative_to(Path(output).resolve()) or path.stat().st_size > 64 * 1024 * 1024:
                    raise ValueError('Invalid existing Procedure index')
                prepared[item['canonical']] = (info, path.read_bytes())
    return update_pack(output, prepared, replace)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, help='Authorized umls-2026AB-full.zip')
    parser.add_argument('--loinc', required=True, help='Authorized Loinc_2.83.zip')
    parser.add_argument('--output', default='terminology')
    parser.add_argument('--cache', default='.cache/umls-import')
    parser.add_argument('--replace', action='store_true')
    args = parser.parse_args()
    result = install(args.input, args.loinc, args.output, args.cache, args.replace)
    print(json.dumps({'valueSets': len(result['valueSets']), 'concepts': sum(v['count'] for v in result['valueSets'])}))
