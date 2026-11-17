import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('loinc_import', Path(__file__).parents[1] / 'scripts/import-loinc.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class LoincImportTest(unittest.TestCase):
    def archive(self, path, table='1234-5,Doc\n5678-9,Qn\n', linked='1234-5', property_name='http://loinc.org/property/SCALE_TYP'):
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr(module.TABLE, 'LOINC_NUM,SCALE_TYP\n' + table)
            archive.writestr(module.LINKS, 'LoincNumber,PartNumber,PartTypeName,Property\n'
                             + linked + ',LP32888-7,SCALE,' + property_name + '\n')

    def test_complete_document_filter_requires_independent_part_link_agreement(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'loinc.zip'
            self.archive(path)
            self.assertEqual({'1234-5'}, module.document_codes(path, 2, 1))
            self.archive(path, linked='5678-9')
            with self.assertRaisesRegex(ValueError, 'cross-check'):
                module.document_codes(path, 2, 1)
            self.archive(path, property_name='different-property')
            with self.assertRaisesRegex(ValueError, 'semantics'):
                module.document_codes(path, 2, 1)

    def test_rejects_duplicates_incomplete_table_and_unreviewed_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'loinc.zip'
            self.archive(path, table='1234-5,Doc\n1234-5,Qn\n')
            with self.assertRaisesRegex(ValueError, 'duplicate'):
                module.document_codes(path, 2, 1)
            self.archive(path)
            with self.assertRaisesRegex(ValueError, 'cross-check'):
                module.document_codes(path, 3, 1)
            output = Path(directory) / 'pack'
            with self.assertRaisesRegex(ValueError, 'reviewed LOINC edition'):
                module.install(path, output)
            self.assertFalse(output.exists())
