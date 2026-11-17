import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('public_terminology', Path(__file__).parents[1] / 'scripts/prepare-public-terminology.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PublicTerminologyTest(unittest.TestCase):
    def definitions(self):
        return {module.PRIORITY_CS: {'content': 'complete', 'version': 'test', 'concept': [
            {'code': 'CS', 'concept': [{'code': 'CSP'}, {'code': 'CSR'}]}, {'code': 'R'}]},
            'https://example.test/callback': {'compose': {'include': [{'system': module.PRIORITY_CS,
                'filter': [{'property': 'concept', 'op': 'is-a', 'value': 'CS'}]}]}},
            'https://example.test/priority': {'compose': {'include': [
                {'system': module.PRIORITY_CS, 'concept': [{'code': 'R'}]},
                {'valueSet': ['https://example.test/callback']}]}}}

    def test_expands_explicit_and_callback_hierarchy_union(self):
        result = module.expand('https://example.test/priority', self.definitions())
        self.assertEqual({'CS', 'CSP', 'CSR', 'R'}, {code for system, code, version in result})
        self.assertEqual({'test'}, {version for system, code, version in result})

    def test_rejects_cycles_partial_sources_and_unreviewed_combinations(self):
        definitions = self.definitions()
        definitions[module.PRIORITY_CS]['content'] = 'fragment'
        with self.assertRaises(ValueError):
            module.expand('https://example.test/priority', definitions)
        definitions = self.definitions()
        definitions['https://example.test/priority']['compose']['include'][0]['filter'] = []
        with self.assertRaises(ValueError):
            module.expand('https://example.test/priority', definitions)
        definitions = {'cycle': {'compose': {'include': [{'valueSet': ['cycle']}]}}}
        with self.assertRaisesRegex(ValueError, 'cycle'):
            module.expand('cycle', definitions)


if __name__ == '__main__':
    unittest.main()
