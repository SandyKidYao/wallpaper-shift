#!/usr/bin/env python3
"""Stage only the site's referenced public files for GitHub Pages."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import sys
from urllib.parse import unquote, urlsplit

SITE = Path(__file__).resolve().parent
sys.path.insert(0, str(SITE / 'scripts'))
from publish_assets import public_origin, public_png


def referenced_path(url):
    parsed = urlsplit(url)
    if parsed.scheme or parsed.netloc or not parsed.path:
        return None
    path = Path(unquote(parsed.path))
    if path.is_absolute() or '..' in path.parts:
        raise ValueError(f'Non-relative site URL: {url}')
    source = SITE / path
    if not source.is_file() or not source.resolve().is_relative_to(SITE):
        raise ValueError(f'Missing or external local asset: {url}')
    return path


def build():
    files = {Path('index.html'), Path('styles.css'), Path('app.js')}
    html = (SITE / 'index.html').read_text()
    css = (SITE / 'styles.css').read_text()
    urls = re.findall(r'(?:src|href|srcset)="([^"]+)"', html)
    urls += re.findall(r'url\(\s*[\"\']?([^\"\')\s]+)', css)
    for url in urls:
        path = referenced_path(url)
        if path:
            files.add(path)
    keys = re.findall(r'key:\s*[\"\']([0-9]{2}-[a-z]+)[\"\']', (SITE / 'app.js').read_text())
    if len(keys) != 6 or len(set(keys)) != 6:
        raise ValueError('Expected six unique demonstration states')
    for key in keys:
        files.add(referenced_path(f'assets/leo/{key}.webp'))
        files.add(referenced_path(f'assets/plates/wallpaper-{key[3:]}.png'))
    for license_name in ['NotoSansSC-OFL.txt', 'BIZUDPGothic-OFL.txt']:
        files.add(referenced_path(f'assets/fonts/{license_name}'))
    output = SITE / 'dist'
    if output.is_symlink():
        raise ValueError('dist must not be a symlink')
    if output.exists():
        shutil.rmtree(output)
    output.mkdir()
    summary = []
    for path in sorted(files):
        source = SITE / path
        if source.is_symlink():
            raise ValueError(f'Publish input must not be a symlink: {path}')
        target = output / path
        target.parent.mkdir(parents=True, exist_ok=True)
        data = source.read_bytes()
        if path.suffix.lower() == '.png':
            data = public_png(data)
        target.write_bytes(data)
        if path.suffix.lower() in {'.png', '.webp'}:
            target.with_name(target.name + '.json').write_text(
                json.dumps({'prompt': 'Pre-existing approved asset. Origin: ' + public_origin(str(path))},
                           ensure_ascii=False, indent=2) + '\n')
        summary.append({'path': str(path), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    (output / '.nojekyll').write_text('')
    for path in output.rglob('*'):
        if path.is_symlink():
            raise ValueError('Pages artifact cannot contain symlinks')
        if path.is_file() and path.suffix in {'.html', '.css', '.js', '.json', '.txt'}:
            text = path.read_text()
            if '/Users/' in text or 'PRIVATE KEY' in text or 'fern-remake' in text:
                raise ValueError(f'Private source metadata in public artifact: {path}')
    print(json.dumps({'directory': str(output), 'files': len(summary),
                      'bytes': sum(row['bytes'] for row in summary),
                      'apk_sha256': next(row['sha256'] for row in summary if row['path'].endswith('.apk'))},
                     ensure_ascii=False))
    return summary


if __name__ == '__main__':
    build()
