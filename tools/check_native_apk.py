"""Verify 16 KB ELF/ZIP alignment in the built unsigned release APK, without executing it."""
from pathlib import Path
import hashlib
import json
import struct
import zipfile

root = Path(__file__).resolve().parents[1]
apk = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
report = {'apk': apk.name, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'bytes': apk.stat().st_size, 'libraries': []}
with zipfile.ZipFile(apk) as archive:
    for entry in archive.infolist():
        if not (entry.filename.startswith('lib/') and entry.filename.endswith('.so')):
            continue
        data = archive.read(entry)
        assert data[:4] == b'\x7fELF'
        bits = data[4]
        order = '<' if data[5] == 1 else '>'
        if bits == 2:
            offset = struct.unpack_from(order + 'Q', data, 32)[0]
            size, count = struct.unpack_from(order + 'HH', data, 54)
        else:
            offset = struct.unpack_from(order + 'I', data, 28)[0]
            size, count = struct.unpack_from(order + 'HH', data, 42)
        alignments = []
        for index in range(count):
            start = offset + index * size
            if struct.unpack_from(order + 'I', data, start)[0] == 1:
                alignments.append(struct.unpack_from(order + ('Q' if bits == 2 else 'I'), data, start + (48 if bits == 2 else 28))[0])
        with apk.open('rb') as stream:
            stream.seek(entry.header_offset)
            header = stream.read(30)
        name_length, extra_length = struct.unpack_from('<HH', header, 26)
        payload = entry.header_offset + 30 + name_length + extra_length
        assert alignments and all(value >= 16384 for value in alignments)
        assert entry.compress_type == zipfile.ZIP_STORED and payload % 16384 == 0
        report['libraries'].append({'path': entry.filename, 'sha256': hashlib.sha256(data).hexdigest(), 'elf_load_alignment': alignments, 'zip_payload_alignment_16kb': True})
report['all_16kb_aligned'] = True
(root / 'docs/validation/native-apk.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('Final optimized APK: all', len(report['libraries']), 'native libraries meet 16 KB ELF and ZIP alignment.')
