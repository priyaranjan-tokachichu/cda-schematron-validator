import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('svs_import', Path(__file__).parents[1] / 'scripts/import-svs.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SvsImportTest(unittest.TestCase):
    def test_complete_count_checked_snapshot_preserves_quoted_codes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source.xml'
            source.write_text('''<RetrieveMultipleValueSetsResponse xmlns="urn:ihe:iti:svs:2008"><DescribedValueSet ID="1.2.3" version="Latest"><ConceptList><Concept code="a&apos;b" codeSystem="1.2" codeSystemVersion="2026"/><Concept code="a|b" codeSystem="1.2" codeSystemVersion="2026"/></ConceptList><RevisionDate>2026-01-01</RevisionDate><Status>Active</Status></DescribedValueSet></RetrieveMultipleValueSetsResponse>''')
            manifest = module.import_exports([source], [{'oid': '1.2.3', 'expectedCount': 2}], root / 'pack')
            info = manifest['valueSets'][0]
            self.assertEqual(2, info['count'])
            self.assertEqual(['1.2|a\'b', '1.2|a|b'], json.loads((root / 'pack' / info['file']).read_text()))
            self.assertEqual({'1.2': ['2026']}, info['codeSystems'])
            self.assertEqual('2026-01-01', info['definitionRevision'])
            self.assertNotIn(str(root), json.dumps(manifest))
            # A new manifest may not silently omit reviewed exports or accept a partial list.
            for expected in [[{'oid': '1.2.3', 'expectedCount': 3}], [{'oid': '1.2.3', 'expectedCount': 2}, {'oid': '1.2.4', 'expectedCount': 1}]]:
                with self.assertRaises(ValueError):
                    module.import_exports([source], expected, root / 'pack')
            self.assertEqual(manifest, json.loads((root / 'pack/manifest.json').read_text()))

    def test_rejects_entities_and_empty_expansions(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.xml'
            for text in ['<!DOCTYPE x><x/>', '<RetrieveMultipleValueSetsResponse xmlns="urn:ihe:iti:svs:2008"><DescribedValueSet ID="1.2.3"><ConceptList/></DescribedValueSet></RetrieveMultipleValueSetsResponse>']:
                source.write_text(text)
                with self.assertRaises(ValueError):
                    module.import_exports([source], [{'oid': '1.2.3', 'expectedCount': 1}], Path(directory) / 'pack')


if __name__ == '__main__':
    unittest.main()
