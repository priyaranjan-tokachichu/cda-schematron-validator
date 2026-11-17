#!/usr/bin/env python3
"""Repeatable HTTP benchmark using repository synthetic/example fixtures, never patient uploads."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import platform
import statistics
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:17643')
parser.add_argument('--requests', type=int, default=100)
parser.add_argument('--concurrency', type=int, nargs='+', default=[1, 2, 4])
parser.add_argument('--output', default='reports/benchmark.json')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
results = []
for file, profile in [('service/web/sample.xml', 'ccda-5.0'), ('test/VitalSigns.xml', 'ccda-2.1')]:
    xml = (root / file).read_text()
    body = json.dumps({'xml': xml, 'profile': profile, 'includeWarnings': True}).encode()
    def request(_):
        start = time.perf_counter()
        req = urllib.request.Request(args.base_url.rstrip('/')+'/v1/validate', data=body, headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=40) as response:
            report = json.load(response)
        if not report.get('executionComplete'):
            raise RuntimeError('Incomplete engine execution; benchmark is not a successful run')
        return (time.perf_counter()-start)*1000, report['durationMs'], report['summary']['errors']
    for _ in range(15):
        request(0)
    for concurrency in args.concurrency:
        start = time.perf_counter()
        with ThreadPoolExecutor(max_workers=concurrency) as pool:
            timings = list(pool.map(request, range(args.requests)))
        elapsed = time.perf_counter()-start
        latency = sorted(t[0] for t in timings)
        result = {'fixture': file, 'profile': profile, 'bytes': len(xml.encode()), 'requests': args.requests,
                  'concurrency': concurrency, 'requestsPerSecond': round(args.requests/elapsed, 2),
                  'httpP50Ms': round(statistics.median(latency), 2),
                  'httpP95Ms': round(latency[max(0, math.ceil(len(latency)*.95)-1)], 2),
                  'engineP50Ms': round(statistics.median(t[1] for t in timings), 2),
                  'consistentErrorCounts': len(set(t[2] for t in timings)) == 1}
        if not result['consistentErrorCounts']:
            raise RuntimeError('Non-deterministic validation results')
        results.append(result)
        print(json.dumps(result), flush=True)
document = {'measuredAt': datetime.now(timezone.utc).isoformat(), 'hostPlatform': platform.system(),
            'architecture': platform.machine(), 'method': '15 warmup requests per fixture, followed by independent HTTP POST requests; full XSD and Schematron; no document-result cache; loopback HTTP; limited examples, not an industry-wide ranking.',
            'results': results}
output = root / args.output
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(document, indent=2)+'\n')
