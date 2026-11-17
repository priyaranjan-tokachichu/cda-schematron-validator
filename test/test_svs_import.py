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

    def test_encoding_aware_dtd_rejection_preserves_existing_pack(self):
        xml = '''<RetrieveMultipleValueSetsResponse xmlns="urn:ihe:iti:svs:2008"><DescribedValueSet ID="1.2.3"><ConceptList><Concept code="safe" codeSystem="1.2"/></ConceptList></DescribedValueSet></RetrieveMultipleValueSetsResponse>'''
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, output = root / 'source.xml', root / 'pack'
            expected = [{'oid': '1.2.3', 'expectedCount': 1}]
            source.write_text(xml)
            module.import_exports([source], expected, output)
            original = {p.name: p.read_bytes() for p in output.iterdir()}
            for encoding in ('utf-8', 'utf-16', 'utf-16-le', 'utf-16-be'):
                for dtd in ('<!DOCTYPE RetrieveMultipleValueSetsResponse>',
                            '<!DOCTYPE RetrieveMultipleValueSetsResponse SYSTEM "file:///nonexistent-svs.dtd">',
                            '<!DOCTYPE RetrieveMultipleValueSetsResponse [<!ENTITY code "expanded">]>'):
                    with self.subTest(encoding=encoding, dtd=dtd):
                        payload = xml.replace('code="safe"', 'code="&code;"') if '<!ENTITY' in dtd else xml
                        declaration = 'utf-8' if encoding == 'utf-8' else 'utf-16'
                        source.write_bytes((f'<?xml version="1.0" encoding="{declaration}"?>' + dtd + payload).encode(encoding))
                        with self.assertRaisesRegex(ValueError, 'DTDs or entities'):
                            module.import_exports([source], expected, output)
                        self.assertEqual(original, {p.name: p.read_bytes() for p in output.iterdir()})
            # Legitimate UTF-16 SVS exports remain supported.
            source.write_bytes(('<?xml version="1.0" encoding="utf-16"?>' + xml).encode('utf-16'))
            self.assertEqual(1, module.import_exports([source], expected, output)['valueSets'][0]['count'])

    def test_rejects_entities_and_empty_expansions(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.xml'
            for text in ['<!DOCTYPE x><x/>', '<RetrieveMultipleValueSetsResponse xmlns="urn:ihe:iti:svs:2008"><DescribedValueSet ID="1.2.3"><ConceptList/></DescribedValueSet></RetrieveMultipleValueSetsResponse>']:
                source.write_text(text)
                with self.assertRaises(ValueError):
                    module.import_exports([source], [{'oid': '1.2.3', 'expectedCount': 1}], Path(directory) / 'pack')

    def test_adding_an_export_preserves_other_data_and_grammar_configuration(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source, pack = root / 'source.xml', root / 'pack'
            def xml(oid):
                return f'<RetrieveMultipleValueSetsResponse xmlns="urn:ihe:iti:svs:2008"><DescribedValueSet ID="{oid}"><ConceptList><Concept code="SYNTHETIC" codeSystem="1.2"/></ConceptList></DescribedValueSet></RetrieveMultipleValueSetsResponse>'
            source.write_text(xml('1.2.3'))
            manifest = module.import_exports([source], [{'oid': '1.2.3', 'expectedCount': 1}], pack)
            manifest['grammars'] = {'synthetic': {'version': 'test'}}
            (pack / 'manifest.json').write_text(json.dumps(manifest))
            source.write_text(xml('1.2.4'))
            result = module.import_exports([source], [{'oid': '1.2.4', 'expectedCount': 1}], pack)
            self.assertEqual(2, len(result['valueSets']))
            self.assertEqual(manifest['grammars'], result['grammars'])


if __name__ == '__main__':
    unittest.main()
