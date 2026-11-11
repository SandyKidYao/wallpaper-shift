#!/usr/bin/env python3
"""Build/check Wallpaper Shift v1 packages. Requires Python 3.10+ and Pillow."""

import argparse
import io
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile
import warnings
import zipfile

from PIL import Image, ImageOps

FORMAT = "wallpaper-shift.set"
MAX_IMAGE = 64 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024
MAX_MANIFEST = 256 * 1024
MAX_PIXELS = 16_000_000
Image.MAX_IMAGE_PIXELS = MAX_PIXELS
warnings.simplefilter("error", Image.DecompressionBombWarning)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_plan(plan):
    require(isinstance(plan, dict), "Plan must be a JSON object")
    require(isinstance(plan.get("name"), str) and plan["name"].strip(), "name must be nonempty")
    blur = plan.get("blur", 10)
    require(type(blur) is int and 0 <= blur <= 25, "blur must be an integer in 0..25")
    items = plan.get("items")
    require(isinstance(items, list) and 1 <= len(items) <= 24, "items must contain 1..24 images")
    starts = set()
    for item in items:
        require(isinstance(item, dict), "Each item must be an object")
        require(isinstance(item.get("file"), str) and item["file"].strip(), "file must be nonempty")
        require(isinstance(item.get("label"), str) and item["label"].strip(), "label must be nonempty")
        start = item.get("start")
        require(isinstance(start, str) and re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", start),
                "start must be HH:mm, 00:00..23:59")
        require(start not in starts, f"Duplicate start time: {start}")
        starts.add(start)
        require("anchor" not in item or item["anchor"] in ("light-start", "dark-start"), "Invalid anchor")
    return blur, items


def parse_size(value):
    require(re.fullmatch(r"[1-9]\d*x[1-9]\d*", value), "size must be WIDTHxHEIGHT")
    size = tuple(map(int, value.split("x")))
    require(size[0] * size[1] <= MAX_PIXELS, "Target exceeds 16 million pixels")
    return size


def render(source, size):
    with Image.open(source) as image:
        require(image.width * image.height <= MAX_PIXELS, f"Image exceeds 16 million pixels: {source}")
        require(getattr(image, "n_frames", 1) == 1, f"Animated images are not supported: {source}")
        image = ImageOps.exif_transpose(image)
        original = image.size
        if abs(image.width / image.height / (size[0] / size[1]) - 1) > 0.01:
            print(f"Crop: {source.name}: {original} -> {size}", file=sys.stderr)
        if max(size[0] / image.width, size[1] / image.height) > 1:
            print(f"Upscale: {source.name}: {original} -> {size}", file=sys.stderr)
        # Alpha composite before JPEG conversion; all other source metadata is omitted.
        rgba = image.convert("RGBA")
        rgb = Image.new("RGB", rgba.size, "white")
        rgb.paste(rgba, mask=rgba.getchannel("A"))
        fitted = ImageOps.fit(rgb, size, method=Image.Resampling.LANCZOS)
        data = io.BytesIO()
        fitted.save(data, "JPEG", quality=95)
        return data.getvalue()


def check(path):
    """Validate our stricter generated-package contract, without extracting files."""
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        require(len(names) == len(set(names)), "Duplicate ZIP entry")
        require("manifest.json" in names, "Missing root manifest.json")
        require(archive.getinfo("manifest.json").file_size <= MAX_MANIFEST, "Manifest exceeds 256 KiB")
        manifest = json.loads(archive.read("manifest.json"))
        blur, items = validate_plan(manifest)
        require(manifest.get("format") == FORMAT, "Invalid package format")
        require(type(manifest.get("version")) is int and manifest["version"] == 1, "Unsupported version")
        files = [item["file"] for item in items]
        require(len(set(files)) == len(files), "Duplicate image reference")
        require(all(re.fullmatch(r"\d{2}\.jpg", name) for name in files), "Expected root NN.jpg images")
        require(set(names) == {"manifest.json", *files}, "Missing images or unexpected ZIP entries")
        require(sum(archive.getinfo(name).file_size for name in files) <= MAX_TOTAL, "Images exceed 512 MiB")
        dimensions = set()
        for name in files:
            require(archive.getinfo(name).file_size <= MAX_IMAGE, f"Image exceeds 64 MiB: {name}")
            with Image.open(io.BytesIO(archive.read(name))) as image:
                require(image.format == "JPEG" and image.mode == "RGB", f"Expected RGB JPEG: {name}")
                require(image.width * image.height <= MAX_PIXELS, f"Image exceeds 16 million pixels: {name}")
                image.load()  # Decode pixels as well as the header; rejects corrupt/truncated data.
                dimensions.add(image.size)
        require(len(dimensions) == 1, "Images must use the same dimensions")
        return {"name": manifest["name"], "images": len(items), "size": list(dimensions.pop()), "blur": blur}


def build(plan_path, output, size):
    plan_path, output = Path(plan_path).resolve(), Path(output).absolute()
    require(output.suffix.lower() == ".zip", "Output must end in .zip")
    require(not output.exists(), f"Output already exists: {output}")
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    blur, items = validate_plan(plan)
    manifest = {"format": FORMAT, "version": 1, "name": plan["name"].strip(), "blur": blur, "items": []}
    # Stage/validate before creating the destination; a failed build leaves no partial ZIP.
    with tempfile.TemporaryDirectory(prefix="wallpaper-shift-") as work:
        staged = Path(work) / "set.zip"
        with zipfile.ZipFile(staged, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            total = 0
            for index, item in enumerate(items, 1):
                source = (plan_path.parent / item["file"]).resolve()
                require(source.is_file(), f"Image not found: {source}")
                data = render(source, size)
                total += len(data)
                require(len(data) <= MAX_IMAGE and total <= MAX_TOTAL, "Image byte limit exceeded")
                filename = f"{index:02d}.jpg"
                archive.writestr(filename, data)
                entry = {"file": filename, "start": item["start"], "label": item["label"].strip()}
                if "anchor" in item:
                    entry["anchor"] = item["anchor"]
                manifest["items"].append(entry)
            data = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
            require(len(data) <= MAX_MANIFEST, "Manifest exceeds 256 KiB")
            archive.writestr("manifest.json", data)
        result = check(staged)
        output.parent.mkdir(parents=True, exist_ok=True)
        destination = output.open("xb")
        try:
            with destination, staged.open("rb") as source:
                shutil.copyfileobj(source, destination)
        except BaseException:
            output.unlink(missing_ok=True)
            raise
    return {"output": str(output), **result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    builder = commands.add_parser("build")
    builder.add_argument("--plan", type=Path, required=True)
    builder.add_argument("--output", type=Path, required=True)
    builder.add_argument("--size", default="1440x3120")
    checker = commands.add_parser("check")
    checker.add_argument("package", type=Path)
    args = parser.parse_args()
    try:
        result = build(args.plan, args.output, parse_size(args.size)) if args.command == "build" else check(args.package)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile, Image.DecompressionBombError,
            Image.DecompressionBombWarning) as error:
        parser.exit(1, f"Error: {error}\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
