import gzip
import importlib.util
import hashlib
import json
from unittest.mock import patch
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('umls_import', Path(__file__).parents[1] / 'scripts/import-umls.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class UmlsImportTest(unittest.TestCase):
    def test_excludes_source_headings_partial_axes_and_wrong_identifier_system(self):
        for source, tty, code in [('CPT', 'PT', '12345'), ('CDT', 'OP', 'D1234'),
                                  ('HCPCS', 'PT', 'G1234'), ('ICD10PCS', 'PT', '0ABCXYZ'),
                                  ('ICD10CM', 'HT', 'E11'), ('ICD9CM', 'PT', '250.00'), ('ICD9CM', 'PT', '00.01')]:
            self.assertTrue(module.selected(source, tty, code), (source, tty, code))
        for source, tty, code in [('CPT', 'HT', '1008945'), ('CPT', 'POS', '1021897'),
                                  ('CDT', 'HT', 'D1000-D1999'), ('ICD10PCS', 'HX', '08P'),
                                  ('ICD10CM', 'HT', 'A00-B99'),
                                  ('CPT', 'MP', 'ET'), ('ICD9CM', 'HT', '001-009.99')]:
            self.assertFalse(module.selected(source, tty, code), (source, tty, code))

    def test_rejoins_arbitrary_fragments_before_utf8_decoding_and_checks_completeness(self):
        fields = ['C1', 'ENG', 'P', 'L1', 'PF', 'S1', 'Y', 'A1', '', 'SC1', '', 'CPT', 'PT', '12345', 'synthetic é', '0', 'N', '', '']
        data = ('|'.join(fields) + '\n').encode()
        split = data.index('é'.encode()) + 1
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'meta.zip'
            with zipfile.ZipFile(path, 'w') as archive:
                for name, content in zip(['aa', 'ab', 'ac'], [data[:split], data[split:-1], data[-1:]]):
                    archive.writestr('2026AB/META/MRCONSO.RRF.' + name + '.gz', gzip.compress(content))
            with zipfile.ZipFile(path) as archive:
                self.assertEqual([fields], list(module.rows(archive, 'MRCONSO')))
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('2026AB/META/MRCONSO.RRF.aa.gz', gzip.compress(data))
            with zipfile.ZipFile(path) as archive, self.assertRaisesRegex(ValueError, 'Incomplete'):
                list(module.rows(archive, 'MRCONSO'))

    def test_conflicting_independent_procedure_snapshot_aborts_without_pack_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'profiles').mkdir()
            source, loinc, output = root / 'umls.zip', root / 'loinc.zip', root / 'terms'
            with zipfile.ZipFile(source, 'w') as archive:
                archive.writestr('2026AB-full/2026ab-1-meta.nlm', b'synthetic metadata')
            loinc.write_bytes(b'synthetic LOINC input; extraction is isolated in this installer test')
            pairs = {module.CONDITION: {'1.2|condition'}, module.PROCEDURE: {'1.3|procedure'}}
            review = {'release': 'synthetic', 'archiveSha256': module.sha256(source),
                      'sources': {}, 'definitions': {key: {} for key in pairs},
                      'valueSetCounts': {key: len(value) for key, value in pairs.items()}}
            (root / 'profiles/umls-sources.lock.json').write_text(json.dumps(review))
            output.mkdir()
            old = b'["1.3|old-procedure"]\n'
            old_hash = hashlib.sha256(old).hexdigest()
            (output / (old_hash + '.json')).write_bytes(old)
            manifest = {'schemaVersion': 1, 'valueSets': [{'canonical': 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.80.28', 'file': old_hash + '.json', 'count': 1, 'sha256': old_hash}]}
            manifest_path = output / 'manifest.json'
            manifest_path.write_text(json.dumps(manifest))
            original = {path.name: path.read_bytes() for path in output.iterdir()}
            provenance = {'snomedProcedureMembershipSha256': 'reviewed-different-membership'}
            with patch.object(module, 'ROOT', root), patch.object(module, 'derive', return_value=(pairs, provenance)):
                with self.assertRaisesRegex(ValueError, 'Procedure snapshot differs'):
                    module.install(source, loinc, output, root / 'cache')
            self.assertEqual(original, {path.name: path.read_bytes() for path in output.iterdir()})
