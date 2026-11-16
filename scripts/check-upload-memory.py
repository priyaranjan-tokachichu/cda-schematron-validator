#!/usr/bin/env python3
"""Regress concurrent worst-case JSON uploads against an isolated 1 GiB-heap container."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import threading
import time
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--image', default='cda-validator:local')
parser.add_argument('--output', default='reports/upload-memory.json')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
name = 'validator-upload-regression-' + uuid.uuid4().hex[:8]
container = None
stop = threading.Event()
health = []
try:
    container = subprocess.check_output(['docker', 'run', '--rm', '-d', '--name', name,
        '--memory', '1536m', '--cpus', '2', '--read-only', '--tmpfs', '/tmp:size=64m,noexec,nosuid',
        '-e', 'JAVA_TOOL_OPTIONS=-Xmx1g', '-e', 'MAX_XML_MIB=20',
        '-p', '127.0.0.1::17643', args.image], text=True).strip()
    inspection = json.loads(subprocess.check_output(['docker', 'inspect', container], text=True))[0]
    port = inspection['NetworkSettings']['Ports']['17643/tcp'][0]['HostPort']
    base = 'http://127.0.0.1:' + port
    def get(path):
        with urllib.request.urlopen(base + path, timeout=5) as response:
            return json.load(response)
    deadline = time.monotonic() + 60
    while True:
        try:
            get('/health/ready')
            break
        except Exception:
            if time.monotonic() >= deadline:
                raise RuntimeError('Container did not become ready')
            time.sleep(.2)
    limits = get('/v1/profiles')['limits']
    assert limits['maxXmlBytes'] == 20 * 1024 * 1024
    # Serial GC may report a slightly smaller usable heap than the -Xmx setting.
    assert 160 * 1024 * 1024 < limits['uploadMemoryBudgetBytes'] <= 256 * 1024 * 1024
    assert limits['maxConcurrentUploads'] == 1
    sample = (root / 'service/web/sample.xml').read_text()
    padding = limits['maxXmlBytes'] - len(sample.encode())
    prefix = b'{"xml":"' + json.dumps(sample, ensure_ascii=True)[1:-1].encode()
    suffix = b'","profile":"ccda-5.0"}'
    wire_bytes = len(prefix) + padding * 6 + len(suffix)
    block_count = 8192
    block = b'\\u000a' * block_count
    assert wire_bytes <= limits['maxRequestBytes']
    def poll_health():
        while not stop.is_set():
            try:
                health.append(get('/health/ready')['status'] == 'ready')
            except Exception:
                health.append(False)
            stop.wait(.1)
    monitor = threading.Thread(target=poll_health, daemon=True)
    monitor.start()
    rounds = []
    for framing in ('known-length', 'chunked'):
        barrier = threading.Barrier(6)
        def upload(_):
            command = ['curl', '--silent', '--show-error', '--http1.1', '--max-time', '60',
                '--request', 'POST', '--header', 'Content-Type: application/json',
                '--header', 'Expect: 100-continue', '--upload-file', '-',
                '--write-out', '\n%{http_code}', base + '/v1/validate']
            if framing == 'known-length':
                command += ['--header', 'Content-Length: ' + str(wire_bytes), '--header', 'Transfer-Encoding:']
            barrier.wait()
            process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            try:
                process.stdin.write(prefix)
                for _ in range(padding // block_count):
                    process.stdin.write(block)
                process.stdin.write(b'\\u000a' * (padding % block_count))
                process.stdin.write(suffix)
            except BrokenPipeError:
                pass  # Early HTTP 429 intentionally stops the upload.
            finally:
                try:
                    process.stdin.close()
                except BrokenPipeError:
                    pass
            stdout = process.stdout.read().decode()
            stderr = process.stderr.read().decode()
            process.wait(timeout=65)
            body, code = stdout.rsplit('\n', 1)
            status = int(code)
            assert status in (200, 429), (status, stderr, body[:200])
            report = json.loads(body)
            if status == 200:
                assert report['executionComplete']
            return status
        with ThreadPoolExecutor(max_workers=6) as executor:
            statuses = list(executor.map(upload, range(6)))
        assert 200 in statuses and 429 in statuses, statuses
        assert get('/health/ready')['status'] == 'ready'
        rounds.append({'framing': framing, 'uploads': 6, 'decodedXmlBytesEach': limits['maxXmlBytes'],
                       'serializedJsonBytesEach': wire_bytes, 'httpStatuses': statuses})
        print(f'{framing}: {statuses}; readiness healthy', flush=True)
    request = urllib.request.Request(base + '/v1/validate', data=json.dumps({'xml': sample}).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=35) as response:
        assert json.load(response)['executionComplete']
    stop.set()
    monitor.join(timeout=6)
    assert health and all(health), health
    inspection = json.loads(subprocess.check_output(['docker', 'inspect', container], text=True))[0]
    assert inspection['State']['Running'] and not inspection['State']['OOMKilled']
    logs = subprocess.check_output(['docker', 'logs', container], text=True, stderr=subprocess.STDOUT)
    assert 'OutOfMemoryError' not in logs
    evidence = {'verifiedAt': datetime.now(timezone.utc).isoformat(), 'javaMaxHeapBytes': 1024**3,
        'containerMemoryBytes': 1536 * 1024**2, 'limits': limits, 'rounds': rounds,
        'healthSamples': len(health), 'allHealthChecksPassed': True, 'postLoadValidationPassed': True,
        'outOfMemory': False}
    output = root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(evidence, indent=2) + '\n')
    print('Concurrent upload regression passed with a 1 GiB heap.', flush=True)
finally:
    stop.set()
    if container:
        subprocess.run(['docker', 'stop', container], capture_output=True, timeout=30)
