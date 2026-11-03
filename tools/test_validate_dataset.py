import gzip
import json
from pathlib import Path
import tempfile
import unittest

from validate_dataset import sha256, validate


class DatasetValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifest = {
            'schemaVersion': '1.0.0', 'state': 'completed', 'candidateCount': 1,
            'options': {'aspects': True}, 'files': [],
        }
        self.shard('items', [{'id': 'test:item@0', 'registryName': 'test:item', 'metadata': 0}])
        self.shard('aspects', [{'itemId': 'test:item@0', 'status': 'empty', 'aspects': {}}])

    def shard(self, category, rows):
        path = self.root / f'{category}-00001.jsonl.gz'
        payload = ''.join(json.dumps(row) + '\n' for row in rows).encode()
        path.write_bytes(gzip.compress(payload))
        self.manifest['files'].append({
            'path': path.name, 'sha256': sha256(path), 'bytes': path.stat().st_size,
            'records': len(rows), 'uncompressedBytes': len(payload),
        })
        self.save()

    def save(self):
        (self.root / 'manifest.json').write_text(json.dumps(self.manifest), encoding='utf-8')

    def test_consistent_dataset(self):
        self.assertTrue(validate(self.root)['valid'])

    def test_corrupted_shard(self):
        (self.root / 'items-00001.jsonl.gz').write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError, 'Integrity mismatch'):
            validate(self.root)

    def test_unresolved_recipe_input(self):
        self.shard('recipes', [{'id': 'r1', 'input': {'kind': 'item', 'itemId': 'test:missing@0'}}])
        result = validate(self.root)
        self.assertFalse(result['valid'])
        self.assertIn('unresolved', result['errors'][0])

    def test_partial_requires_opt_in(self):
        self.manifest['state'] = 'cancelled'
        self.save()
        with self.assertRaisesRegex(ValueError, 'cancelled'):
            validate(self.root)
        self.assertTrue(validate(self.root, allow_partial=True)['valid'])

    def test_path_traversal_is_rejected(self):
        self.manifest['files'][0]['path'] = '../outside.jsonl.gz'
        self.save()
        with self.assertRaisesRegex(ValueError, 'escapes dataset'):
            validate(self.root)


if __name__ == '__main__':
    unittest.main()
