"""Shared bounded, atomic writes for operator terminology packs."""
import hashlib
import json
import os
from pathlib import Path
import tempfile


def atomic_write(target, data):
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix='.terminology-', delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
        temporary.chmod(0o644)
        os.replace(temporary, target)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def update_pack(output, prepared, replace=False):
    output = Path(output)
    manifest_file = output / 'manifest.json'
    if manifest_file.exists():
        if not manifest_file.resolve().is_relative_to(output.resolve()) or manifest_file.stat().st_size > 2 * 1024 * 1024:
            raise ValueError('Invalid existing manifest path or size')
        manifest = json.loads(manifest_file.read_bytes())
    else:
        manifest = {'schemaVersion': 1, 'valueSets': []}
    if type(manifest.get('schemaVersion')) is not int or manifest['schemaVersion'] != 1:
        raise ValueError('Unsupported pack format')
    existing, paths = {}, {}
    total_bytes = total_concepts = 0
    for info in manifest['valueSets']:
        canonical = info['canonical']
        if canonical in existing:
            raise ValueError('Duplicate existing canonical')
        path = (output / info['file']).resolve()
        if not path.is_relative_to(output.resolve()):
            raise ValueError('Existing pack path escapes its directory')
        total_bytes += path.stat().st_size
        if type(info['count']) is not int or info['count'] <= 0:
            raise ValueError('Invalid existing concept count')
        total_concepts += info['count']
        if total_bytes > 64 * 1024 * 1024 or total_concepts > 1_250_000:
            raise ValueError('Existing pack exceeds supported bounds')
        existing[canonical], paths[canonical] = info, path
    for canonical, info in existing.items():
        data = paths[canonical].read_bytes()
        if hashlib.sha256(data).hexdigest() != info['sha256']:
            raise ValueError('Existing pack integrity failure')
        values = json.loads(data)
        if not isinstance(values, list) or len(values) != info['count'] or not all(isinstance(v, str) for v in values) or len(set(values)) != len(values):
            raise ValueError('Existing pack concept count or uniqueness failure')
    for canonical, (info, data) in prepared.items():
        if Path(info['file']).name != info['file']:
            raise ValueError('Invalid prepared filename')
        if canonical in existing and existing[canonical]['sha256'] != info['sha256'] and not replace:
            raise ValueError('Conflicting installed expansion; use --replace after reviewing the new edition')
        existing[canonical] = info
    if sum(info['count'] for info in existing.values()) > 1_250_000:
        raise ValueError('Pack exceeds concept budget')
    if sum(len(prepared[c][1]) if c in prepared else paths[c].stat().st_size for c in existing) > 64 * 1024 * 1024:
        raise ValueError('Pack exceeds byte budget')
    result = {**manifest, 'schemaVersion': 1, 'valueSets': [existing[c] for c in sorted(existing)]}
    encoded = (json.dumps(result, indent=2) + '\n').encode()
    if len(encoded) > 2 * 1024 * 1024:
        raise ValueError('Pack manifest exceeds byte budget')
    output.mkdir(parents=True, exist_ok=True)
    for info, data in prepared.values():
        atomic_write(output / info['file'], data)
    atomic_write(manifest_file, encoded)
    return result
