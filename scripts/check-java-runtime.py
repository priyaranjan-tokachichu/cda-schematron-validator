#!/usr/bin/env python3
"""Verify the actual JVM in a pinned image, or check the selected LTS release upstream."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def verify(text, lock):
    version = re.search(r'^\s*java.version = (\S+)', text, re.MULTILINE)
    runtime = re.search(r'^\s*java.runtime.version = (\S+)', text, re.MULTILINE)
    vendor = re.search(r'^\s*java.vendor = (.+)', text, re.MULTILINE)
    if not version or not runtime or not vendor:
        raise ValueError('Cannot determine runtime version and vendor')
    if version.group(1) != lock['javaVersion'] or not runtime.group(1).startswith(lock['javaVersion'] + '+' + str(lock['javaBuild']) + '-') or vendor.group(1).strip() != lock['vendor']:
        raise ValueError('Image JVM does not match the reviewed patched runtime lock')
    return {'javaVersion': version.group(1), 'runtimeVersion': runtime.group(1), 'vendor': vendor.group(1).strip()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image')
    parser.add_argument('--platform', choices=['linux/amd64', 'linux/arm64'])
    parser.add_argument('--upstream', action='store_true')
    args = parser.parse_args()
    lock = json.loads((ROOT / 'profiles/runtime.lock.json').read_text())
    if args.upstream:
        request = urllib.request.Request('https://api.github.com/repos/adoptium/temurin' + str(lock['javaFeature']) + '-binaries/releases/latest', headers={'User-Agent': 'cda-validator-runtime-watch'})
        with urllib.request.urlopen(request, timeout=30) as response:
            latest = json.load(response)['tag_name']
        if latest != lock['release']:
            raise SystemExit('Java update requires review: locked ' + lock['release'] + ', available ' + latest)
        print('Selected LTS matches latest official Temurin release:', latest)
    else:
        if not args.image:
            parser.error('--image or --upstream is required')
        command = ['docker', 'run', '--rm', '--network', 'none']
        if args.platform:
            command += ['--platform', args.platform]
        result = subprocess.run(command + ['--entrypoint', 'java', args.image, '-XshowSettings:properties', '-version'], capture_output=True, text=True, check=True)
        print(json.dumps({'image': args.image, 'platform': args.platform, **verify(result.stderr + result.stdout, lock)}))
