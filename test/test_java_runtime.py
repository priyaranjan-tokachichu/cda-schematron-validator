import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('java_runtime', Path(__file__).parents[1] / 'scripts/check-java-runtime.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class JavaRuntimeTest(unittest.TestCase):
    def test_requires_the_patched_build_and_vendor_not_just_major_version(self):
        lock = {'javaVersion': '25.0.5.1', 'javaBuild': 2, 'vendor': 'Eclipse Adoptium'}
        settings = '    java.version = 25.0.5.1\n    java.runtime.version = 25.0.5.1+2-LTS\n    java.vendor = Eclipse Adoptium\n'
        self.assertEqual('25.0.5.1', module.verify(settings, lock)['javaVersion'])
        for bad in [settings.replace('25.0.5.1', '25.0.5'), settings.replace('+2-', '+1-'),
                    settings.replace('+2-', '+20-'), settings.replace('Eclipse Adoptium', 'other'), '']:
            with self.assertRaises(ValueError):
                module.verify(bad, lock)
