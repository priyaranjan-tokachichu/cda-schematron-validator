#!/usr/bin/env python3
"""Report published upstream changes without changing locked assets or opening PRs."""
import json
from pathlib import Path
import urllib.request
import sys

root = Path(__file__).resolve().parent.parent
sources = json.loads((root/'profiles/sources.lock.json').read_text())['sources']
changed = False
for source in sources:
    repository = '/'.join(source['url'].split('/')[3:5])
    request = urllib.request.Request('https://api.github.com/repos/'+repository+'/releases/latest', headers={'User-Agent': 'cda-validator-upstream-watch', 'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        latest = json.load(response)
    update = latest['tag_name'] != source['release']
    changed = changed or update
    print(json.dumps({'source': source['id'], 'locked': source['release'], 'latest': latest['tag_name'], 'reviewNeeded': update, 'url': latest['html_url']}))
sys.exit(1 if changed else 0)
