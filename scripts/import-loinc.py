#!/usr/bin/env python3
"""Build the reviewed US Core document-type index from an operator's LOINC Complete ZIP.

No download, account access, license acceptance, or redistribution is performed.
"""
import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import zipfile
try:
    from terminology_pack import update_pack
except ModuleNotFoundError:
    from scripts.terminology_pack import update_pack

ROOT = Path(__file__).resolve().parents[1]
TABLE = 'LoincTable/Loinc.csv'
LINKS = 'AccessoryFiles/PartFile/LoincPartLink_Primary.csv'


def document_codes(archive, expected_rows, expected_codes):
    """Cross-check the table's Doc scale against the primary SCALE part linkage."""
    with zipfile.ZipFile(archive) as source:
        for name in (TABLE, LINKS):
            entries = [entry for entry in source.infolist() if entry.filename == name]
            if len(entries) != 1 or entries[0].file_size > 512 * 1024 * 1024:
                raise ValueError('Missing, duplicate or oversized LOINC member')
        codes, linked, seen = set(), set(), set()
        with source.open(TABLE) as stream:
            rows = csv.DictReader(io.TextIOWrapper(stream, encoding='utf-8-sig'))
            if not {'LOINC_NUM', 'SCALE_TYP'} <= set(rows.fieldnames or []):
                raise ValueError('Unsupported LOINC table columns')
            for row in rows:
                code = row['LOINC_NUM']
                if not code or code in seen:
                    raise ValueError('Empty or duplicate LOINC code')
                seen.add(code)
                if len(seen) > expected_rows:
                    raise ValueError('LOINC table exceeds reviewed count')
                if row['SCALE_TYP'] == 'Doc':
                    codes.add(code)
        with source.open(LINKS) as stream:
            rows = csv.DictReader(io.TextIOWrapper(stream, encoding='utf-8-sig'))
            if not {'LoincNumber', 'PartNumber', 'PartTypeName', 'Property'} <= set(rows.fieldnames or []):
                raise ValueError('Unsupported LOINC part-link columns')
            for row in rows:
                if row['PartNumber'] == 'LP32888-7':
                    if row['PartTypeName'] != 'SCALE' or row['Property'] != 'http://loinc.org/property/SCALE_TYP':
                        raise ValueError('Unexpected document part semantics')
                    code = row['LoincNumber']
                    if code in linked:
                        raise ValueError('Duplicate document part link')
                    linked.add(code)
        if len(seen) != expected_rows or len(codes) != expected_codes or codes != linked:
            raise ValueError('LOINC count or document-scale cross-check failed')
        return codes


def install(archive, output, replace=False):
    review = json.loads((ROOT / 'profiles/terminology-sources.lock.json').read_text())['loincDocumentTypes']
    archive = Path(archive)
    if archive.stat().st_size > 256 * 1024 * 1024:
        raise ValueError('LOINC archive exceeds supported size')
    with archive.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if digest != review['sha256']:
        raise ValueError('Archive does not match the reviewed LOINC edition')
    codes = document_codes(archive, review['tableRows'], review['documentCodes'])
    # The pinned US Core 9.0.0 definition includes every Doc-scale code,
    # without a status filter, plus the explicitly enumerated NullFlavor UNK.
    pairs = ['2.16.840.1.113883.6.1|' + code for code in codes]
    pairs.append('2.16.840.1.113883.5.1008|UNK')
    data = (json.dumps(sorted(pairs), separators=(',', ':')) + '\n').encode()
    index_hash = hashlib.sha256(data).hexdigest()
    canonical = review['canonical']
    info = {'canonical': canonical, 'file': index_hash + '.json', 'sha256': index_hash,
            'count': len(pairs), 'source': canonical, 'sourceVersion': review['valueSetVersion'],
            'sourceSha256': digest, 'definitionSha256': review['definitionSha256'],
            'derivation': 'LOINC SCALE_TYP=Doc, cross-checked with SCALE part LP32888-7; plus NullFlavor UNK',
            'codeSystems': {'http://loinc.org': [review['version']],
                            'http://terminology.hl7.org/CodeSystem/v3-NullFlavor': ['UNK explicit enumeration']}}
    return update_pack(output, {canonical: (info, data)}, replace)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, help='Locally downloaded Loinc_2.83.zip')
    parser.add_argument('--output', default='terminology')
    parser.add_argument('--replace', action='store_true')
    args = parser.parse_args()
    result = install(args.input, args.output, args.replace)
    print(json.dumps({'valueSets': len(result['valueSets']), 'concepts': sum(v['count'] for v in result['valueSets'])}))
