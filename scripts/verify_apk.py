#!/usr/bin/env python3
"""Reject damaged packaged DEX before APK publication; uses only Python's standard library.

DEX header/checksum layout: https://source.android.com/docs/core/runtime/dex-format
This validates the APK bytes, unlike JVM/Robolectric tests of pre-DEX classes.
"""
import argparse
import hashlib
import json
import re
import struct
import subprocess
import tempfile
import zipfile
import zlib
from pathlib import Path


def checked_dex(data, name):
    def fail(reason):
        raise ValueError(f"{name}: {reason}")

    if len(data) < 112 or data[:8] not in [b"dex\n" + v + b"\0" for v in (b"035", b"037", b"038", b"039", b"040")]:
        fail("unsupported or missing DEX header")
    fields = struct.unpack_from("<20I", data, 32)
    if fields[0] != len(data):
        fail(f"header file_size {fields[0]} differs from actual size {len(data)}")
    if fields[1] != 112 or fields[2] != 0x12345678:
        fail("invalid header size or endianness")
    if hashlib.sha1(data[32:]).digest() != data[12:32]:
        fail("DEX SHA-1 mismatch")
    if (zlib.adler32(data[12:]) & 0xffffffff) != struct.unpack_from("<I", data, 8)[0]:
        fail("DEX Adler-32 mismatch")
    for size, off, width in [(fields[6], fields[7], 4), (fields[8], fields[9], 4),
                            (fields[10], fields[11], 12), (fields[12], fields[13], 8),
                            (fields[14], fields[15], 8), (fields[16], fields[17], 32)]:
        if size and (off < 112 or off + size * width > len(data)):
            fail("identifier table outside file")
    classes = []
    for i in range(fields[16]):
        type_index = struct.unpack_from("<I", data, fields[17] + i * 32)[0]
        if type_index >= fields[8]:
            fail("class type index outside table")
        string_index = struct.unpack_from("<I", data, fields[9] + type_index * 4)[0]
        if string_index >= fields[6]:
            fail("class descriptor index outside table")
        off = struct.unpack_from("<I", data, fields[7] + string_index * 4)[0]
        for _ in range(5):
            if off >= len(data):
                fail("class descriptor outside file")
            value = data[off]
            off += 1
            if not value & 0x80:
                break
        else:
            fail("invalid descriptor string length")
        end = data.find(b"\0", off)
        if end == -1:
            fail("unterminated class descriptor")
        classes.append(data[off:end].decode("utf-8", errors="replace"))
    return classes


def verify(apk, dexdump=None):
    all_classes = set()
    entries = []
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as tmp:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("duplicate ZIP entry names")
        corrupt = archive.testzip()
        if corrupt:
            raise ValueError(f"ZIP CRC failed: {corrupt}")
        dex_names = [n for n in names if re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", n)]
        expected = {"classes.dex"} | {f"classes{i}.dex" for i in range(2, len(dex_names) + 1)}
        if set(dex_names) != expected:
            raise ValueError("missing or non-contiguous DEX sequence")
        for name in dex_names:
            data = archive.read(name)
            classes = checked_dex(data, name)
            if len(classes) != len(set(classes)) or all_classes.intersection(classes):
                raise ValueError(f"{name}: duplicate class definitions")
            all_classes.update(classes)
            if dexdump:
                path = Path(tmp) / name
                path.write_bytes(data)
                subprocess.run([str(dexdump), "-f", str(path)], check=True,
                               stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            entries.append({"name": name, "bytes": len(data), "classes": len(classes),
                            "sha256": hashlib.sha256(data).hexdigest()})
    required = {"Lcom/example/MainActivity;", "Lcom/example/viewmodel/DietaViewModel;",
                "Lcom/example/data/AppDatabase_Impl;", "Landroidx/activity/ComponentActivity;",
                "Lcom/google/mlkit/common/internal/MlKitInitProvider;"}
    if not required.issubset(all_classes):
        raise ValueError("required app/runtime classes missing: " + str(sorted(required - all_classes)))
    return {"apk": str(apk), "sha256": hashlib.sha256(Path(apk).read_bytes()).hexdigest(),
            "dex_files": entries, "class_count": len(all_classes), "status": "passed"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--dexdump", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.apk, args.dexdump), indent=2))
    except (ValueError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f"APK validation FAILED: {error}\n")
