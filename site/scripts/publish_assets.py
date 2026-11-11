"""Utilities for the public static artifact. Standard library only."""
from pathlib import Path
import struct

PRIVATE_PNG_CHUNKS = {b'tEXt', b'zTXt', b'iTXt', b'eXIf'}


def public_png(data: bytes) -> bytes:
    """Drop free-text/EXIF metadata while preserving pixel and color-profile chunks."""
    signature = b'\x89PNG\r\n\x1a\n'
    if not data.startswith(signature):
        raise ValueError('Expected PNG image')
    chunks = [signature]
    cursor = len(signature)
    ended = False
    while cursor + 12 <= len(data):
        length = struct.unpack('>I', data[cursor:cursor + 4])[0]
        end = cursor + length + 12
        if end > len(data):
            raise ValueError('Truncated PNG chunk')
        kind = data[cursor + 4:cursor + 8]
        if kind not in PRIVATE_PNG_CHUNKS:
            chunks.append(data[cursor:end])
        cursor = end
        if kind == b'IEND':
            ended = True
            break
    if not ended or cursor != len(data):
        raise ValueError('Unexpected trailing PNG content')
    return b''.join(chunks)


def public_origin(path: str) -> str:
    """Public provenance for pre-existing approved assets; no local/private paths."""
    name = Path(path).name
    if name in {'icon.png', 'brand-icon.png'}:
        return 'Wallpaper Shift app icon. Source: design/icon/icon.svg in this repository.'
    if name == 'paper.png':
        return 'Paper ground texture from the approved product-page visual reference; no artwork or UI text.'
    return ('User-approved Leo library wallpaper set, generated with the built-in image tool '
            'from user-provided Leo persona and library references. Asset: ' + name)
