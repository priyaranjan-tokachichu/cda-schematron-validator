import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from scripts.terminology_pack import update_pack


class TerminologyPackTest(unittest.TestCase):
    def test_atomic_writes_do_not_follow_planted_symlinks(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            pack = root / 'pack'
            pack.mkdir()
            outside = root / 'outside.txt'
            outside.write_text('unchanged')
            data = b'["1.2|SYNTHETIC"]\n'
            digest = hashlib.sha256(data).hexdigest()
            name = digest + '.json'
            (pack / name).symlink_to(outside)
            (pack / 'manifest.json.tmp').symlink_to(outside)
            info = {'canonical': 'https://example.test/vs', 'file': name, 'count': 1, 'sha256': digest}
            update_pack(pack, {info['canonical']: (info, data)})
            self.assertEqual('unchanged', outside.read_text())
            self.assertFalse((pack / name).is_symlink())
            original = (pack / 'manifest.json').read_bytes()
            (pack / name).write_bytes(b'[]')
            with self.assertRaisesRegex(ValueError, 'integrity'):
                update_pack(pack, {})
            self.assertEqual(original, (pack / 'manifest.json').read_bytes())


if __name__ == '__main__':
    unittest.main()
