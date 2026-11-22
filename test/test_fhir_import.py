import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('fhir_import', Path(__file__).parents[1] / 'scripts/import-fhir-terminology.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FhirImportTest(unittest.TestCase):
    def resource(self):
        return {'resourceType': 'ValueSet', 'url': 'https://example.test/vs', 'version': 'test-1',
                'expansion': {'total': 2, 'contains': [{'system': 'https://example.test/system', 'code': "a'b"},
                                                     {'system': 'https://example.test/system', 'code': 'a|b'}]}}

    def test_complete_expansion_and_explicit_system_mapping(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / 'vs.json'
            resource = self.resource()
            source.write_text(json.dumps(resource))
            expected = [{'canonical': resource['url'], 'version': 'test-1', 'expectedCount': 2}]
            pack = root / 'pack'
            result = module.import_expansions([source], expected, {'https://example.test/system': '1.2'}, pack)
            info = result['valueSets'][0]
            self.assertEqual(["1.2|a'b", '1.2|a|b'], json.loads((pack / info['file']).read_text()))
            original = (pack / 'manifest.json').read_bytes()
            # Any replacement must be deliberate; failed imports preserve the active manifest.
            resource['expansion']['contains'][0]['code'] = 'changed'
            source.write_text(json.dumps(resource))
            with self.assertRaisesRegex(ValueError, 'Conflicting'):
                module.import_expansions([source], expected, {'https://example.test/system': '1.2'}, pack)
            self.assertEqual(original, (pack / 'manifest.json').read_bytes())
            result = module.import_expansions([source], expected, {'https://example.test/system': '1.2'}, pack, True)
            self.assertNotEqual(info['sha256'], result['valueSets'][0]['sha256'])

    def test_rejects_partial_duplicate_wrong_edition_and_unmapped_system(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / 'vs.json'
            expected = [{'canonical': 'https://example.test/vs', 'version': 'test-1', 'expectedCount': 2}]
            mutations = [lambda r: r['expansion'].update(offset=2), lambda r: r.update(version='test-2'),
                         lambda r: r['expansion'].update(contains=r['expansion']['contains'][:1]),
                         lambda r: r['expansion'].update(contains=[r['expansion']['contains'][0]] * 2),
                         lambda r: r['expansion'].update(parameter=[{'name': 'limitedExpansion', 'valueBoolean': True}]),
                         lambda r: r['expansion'].update(extension=[{'url': 'http://hl7.org/fhir/StructureDefinition/valueset-unclosed', 'valueBoolean': True}]),
                         lambda r: r['expansion']['contains'][0].update(abstract=True)]
            for mutate in mutations:
                r = self.resource()
                mutate(r)
                source.write_text(json.dumps(r))
                with self.assertRaises(ValueError):
                    module.import_expansions([source], expected, {'https://example.test/system': '1.2'}, root / 'pack')
                self.assertFalse((root / 'pack/manifest.json').exists())
            source.write_text(json.dumps(self.resource()))
            with self.assertRaisesRegex(ValueError, 'OID mapping'):
                module.import_expansions([source], expected, {}, root / 'pack')

    def test_canonical_alias_requires_an_explicit_review_record(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / 'vs.json'
            source.write_text(json.dumps(self.resource()))
            expected = [{'canonical': 'https://example.test/reviewed-alias', 'version': 'test-1', 'expectedCount': 2}]
            with self.assertRaisesRegex(ValueError, 'reviewed FHIR ValueSet'):
                module.import_expansions([source], expected, {'https://example.test/system': '1.2'}, root / 'pack')
            expected[0]['sourceCanonical'] = 'https://example.test/vs'
            result = module.import_expansions([source], expected, {'https://example.test/system': '1.2'}, root / 'pack')
            info = result['valueSets'][0]
            self.assertTrue(info['canonicalAlias'])
            self.assertEqual('https://example.test/vs', info['source'])
            self.assertEqual(expected[0]['canonical'], info['canonical'])
            source.write_text('{"resourceType":"ValueSet","resourceType":"ValueSet"}')
            with self.assertRaisesRegex(ValueError, 'Duplicate JSON'):
                module.import_expansions([source], expected, {}, root / 'pack')


if __name__ == '__main__':
    unittest.main()
