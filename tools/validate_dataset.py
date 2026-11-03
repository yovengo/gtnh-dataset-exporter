#!/usr/bin/env python3
"""Validate an exporter dataset without third-party dependencies (Python 3.9+)."""

import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import struct
import sys


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()


def dataset_path(root, relative):
    path = (root / relative).resolve()
    if not path.is_relative_to(root):
        raise ValueError(f'Path escapes dataset: {relative}')
    return path


def references(value):
    if isinstance(value, dict):
        if value.get('kind') == 'item':
            if value.get('itemId'):
                yield value['itemId']
            elif value.get('metadata') != '*':
                raise ValueError('Concrete item reference has no itemId')
        for child in value.values():
            yield from references(child)
    elif isinstance(value, list):
        for child in value:
            yield from references(child)


def validate(directory, allow_partial=False):
    root = Path(directory).resolve()
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('schemaVersion', '').split('.')[0] != '1':
        raise ValueError('Unsupported or missing schemaVersion')
    state = manifest.get('state')
    if state not in ('running', 'completed', 'cancelled', 'failed'):
        raise ValueError(f'Unknown run state: {state}')
    if state != 'completed' and not allow_partial:
        raise ValueError(f'Run is {state}; use --allow-partial to inspect finalized files')

    items, aspect_items, icon_items, referenced = set(), set(), set(), set()
    mods, statuses, counts = Counter(), Counter(), Counter()
    recipes = Counter()
    errors = []
    indexed = set()
    for entry in manifest.get('files', []):
        relative = entry['path']
        if relative in indexed:
            raise ValueError(f'Duplicate manifest path: {relative}')
        indexed.add(relative)
        path = dataset_path(root, relative)
        if path.stat().st_size != entry['bytes'] or sha256(path) != entry['sha256']:
            raise ValueError(f'Integrity mismatch: {relative}')
        if not relative.endswith('.jsonl.gz'):
            continue
        category = path.name.rsplit('-', 1)[0]
        row_count, byte_count = 0, 0
        with gzip.open(path, 'rb') as stream:
            for line in stream:
                byte_count += len(line)
                row_count += 1
                row = json.loads(line)
                counts[category] += 1
                referenced.update(references(row))
                if category == 'items':
                    item_id = row['id']
                    if item_id in items:
                        raise ValueError(f'Duplicate item ID: {item_id}')
                    if row['metadata'] == 32767:
                        raise ValueError(f'Wildcard in concrete catalog: {item_id}')
                    items.add(item_id)
                    mods[row['registryName'].split(':', 1)[0]] += 1
                elif category == 'aspects':
                    item_id = row['itemId']
                    if item_id in aspect_items:
                        raise ValueError(f'Duplicate aspect result: {item_id}')
                    if row['status'] not in ('present', 'empty', 'absent', 'error'):
                        raise ValueError(f'Unknown aspect status: {row["status"]}')
                    aspect_items.add(item_id)
                    referenced.add(item_id)
                    statuses['aspects.' + row['status']] += 1
                elif category == 'icons':
                    item_id = row['itemId']
                    if item_id in icon_items:
                        raise ValueError(f'Duplicate icon result: {item_id}')
                    icon_items.add(item_id)
                    referenced.add(item_id)
                    statuses['icons.' + row['status']] += 1
                    if row['status'] not in ('rendered', 'cached', 'empty', 'error'):
                        raise ValueError(f'Unknown icon status: {row["status"]}')
                    if row['status'] != 'error':
                        image = dataset_path(root, row['path'])
                        if image.stat().st_size != row['bytes'] or sha256(image) != row['sha256']:
                            raise ValueError(f'Icon integrity mismatch: {row["path"]}')
                        with image.open('rb') as source:
                            header = source.read(33)
                        if len(header) < 33 or header[:8] != b'\x89PNG\r\n\x1a\n' or header[12:16] != b'IHDR':
                            raise ValueError(f'Invalid PNG: {row["path"]}')
                        if struct.unpack('>II', header[16:24]) != (row['width'], row['height']):
                            raise ValueError(f'PNG dimension mismatch: {row["path"]}')
                elif category == 'recipes':
                    recipes[row['id']] += 1
        if row_count != entry['records']:
            raise ValueError(f'Record count mismatch: {relative}')
        if 'uncompressedBytes' in entry and byte_count != entry['uncompressedBytes']:
            raise ValueError(f'Uncompressed size mismatch: {relative}')

    unexpected = {path.name for path in root.glob('*.jsonl.gz')} - indexed
    if unexpected:
        errors.append(f'Unindexed JSONL files: {sorted(unexpected)}')
    missing = referenced - items
    if missing:
        errors.append(f'{len(missing)} unresolved item references; examples: {sorted(missing)[:10]}')
    options = manifest.get('options', {})
    if state == 'completed':
        if len(items) != manifest.get('candidateCount'):
            errors.append('Item count does not match candidateCount')
        if options.get('aspects') and aspect_items != items:
            errors.append(f'Aspect coverage mismatch: {len(items - aspect_items)} missing results')
        if options.get('icons') and icon_items != items:
            errors.append(f'Icon coverage mismatch: {len(items - icon_items)} missing results')
    return {
        'valid': not errors,
        'state': state,
        'categories': manifest.get('categories', {}),
        'records': dict(sorted(counts.items())),
        'statuses': dict(sorted(statuses.items())),
        'itemsByMod': dict(sorted(mods.items())),
        'duplicateRecipeRegistrations': sum(count - 1 for count in recipes.values()),
        'errors': errors,
        'note': 'Integrity and internal consistency only; this does not prove exhaustive game coverage.',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--allow-partial', action='store_true')
    args = parser.parse_args()
    try:
        result = validate(args.directory, args.allow_partial)
    except (OSError, ValueError, KeyError, TypeError, EOFError) as exc:
        print(f'Validation failed: {exc}', file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result['valid'] else 1


if __name__ == '__main__':
    sys.exit(main())
