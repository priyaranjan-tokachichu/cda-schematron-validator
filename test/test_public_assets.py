import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('public_assets', Path(__file__).parents[1] / 'scripts/package-public-assets.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PublicAssetsTest(unittest.TestCase):
    def test_public_assets_exclude_entire_mixed_license_vocabulary(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            legacy = root / 'ccda-2.1'
            legacy.mkdir()
            content = b'<systems><code codeSystem="2.16.840.1.113883.6.12" value="SYNTHETIC-NOT-A-CPT-CODE"/></systems>'
            digest = hashlib.sha256(content).hexdigest()
            (legacy / 'voc.xml').write_bytes(content)
            (legacy / '.files.json').write_text(json.dumps({'voc.xml': digest}))
            (root / 'manifest.json').write_text(json.dumps({'profiles': [{'id': 'ccda-2.1', 'limitations': []}]}))
            module.package_assets(root)
            self.assertNotIn(b'SYNTHETIC-NOT-A-CPT-CODE', (legacy / 'voc.xml').read_bytes())
            self.assertEqual(digest, json.loads((root / 'manifest.json').read_text())['profiles'][0]['operatorVocabulary']['sha256'])
            self.assertEqual(hashlib.sha256((legacy / 'voc.xml').read_bytes()).hexdigest(), json.loads((legacy / '.files.json').read_text())['voc.xml'])
            module.package_assets(root)
            self.assertEqual(digest, json.loads((root / 'manifest.json').read_text())['profiles'][0]['operatorVocabulary']['sha256'])


if __name__ == '__main__':
    unittest.main()
