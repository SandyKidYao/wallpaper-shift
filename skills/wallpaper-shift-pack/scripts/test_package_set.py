"""Run with: python -m unittest discover -s <skill-dir>/scripts -p 'test_*.py'."""

import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from PIL import Image

from package_set import build, check, parse_size


class PackageSetTest(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        self.root = Path(self.work.name)
        # Transparent, landscape source exercises crop, alpha flattening, and JPEG conversion.
        Image.new("RGBA", (200, 100), (255, 0, 0, 0)).save(self.root / "source.png")
        self.plan = {
            "name": "海边的一天", "blur": 10,
            "items": [{"file": "source.png", "start": start, "label": label}
                      for start, label in zip(
                          ["05:00", "07:30", "11:00", "14:00", "17:30", "19:30"],
                          ["黎明", "早晨", "正午", "午后", "日落", "夜晚"])],
        }
        self.plan_path = self.root / "plan.json"
        self.output = self.root / "海边的一天.zip"

    def make(self, size=(90, 195)):
        self.plan_path.write_text(json.dumps(self.plan, ensure_ascii=False), encoding="utf-8")
        with contextlib.redirect_stderr(io.StringIO()):
            return build(self.plan_path, self.output, size)

    def rewrite_zip(self, transform):
        with zipfile.ZipFile(self.output) as archive:
            entries = {name: archive.read(name) for name in archive.namelist()}
        transform(entries)
        with zipfile.ZipFile(self.output, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)

    def test_six_frame_package_preserves_schedule_and_decodes(self):
        self.plan["items"][0]["anchor"] = "light-start"
        result = self.make()
        self.assertEqual((6, [90, 195], "海边的一天"),
                         (result["images"], result["size"], result["name"]))
        with zipfile.ZipFile(self.output) as archive:
            manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual("wallpaper-shift.set", manifest["format"])
            self.assertEqual(self.plan["items"][0]["anchor"], manifest["items"][0]["anchor"])
            self.assertEqual([item["start"] for item in self.plan["items"]],
                             [item["start"] for item in manifest["items"]])
            self.assertEqual([item["label"] for item in self.plan["items"]],
                             [item["label"] for item in manifest["items"]])
            self.assertEqual(7, len(archive.namelist()))
            with Image.open(io.BytesIO(archive.read("01.jpg"))) as image:
                self.assertEqual((90, 195), image.size)
                self.assertEqual((255, 255, 255), image.getpixel((45, 90)))

    def test_exif_orientation_is_applied_and_metadata_removed(self):
        image = Image.new("RGB", (195, 90), "blue")
        exif = Image.Exif()
        exif[274] = 6  # Rotate 90 degrees clockwise on display.
        image.save(self.root / "oriented.jpg", exif=exif)
        self.plan["items"] = [{"file": "oriented.jpg", "start": "00:00", "label": "夜晚"}]
        self.make()
        with zipfile.ZipFile(self.output) as archive:
            with Image.open(io.BytesIO(archive.read("01.jpg"))) as result:
                self.assertEqual((90, 195), result.size)
                self.assertFalse(result.getexif())

    def test_invalid_plans_fail_without_creating_output(self):
        mutations = [
            lambda p: p.update(items=[]),
            lambda p: p.update(items=p["items"] * 5),
            lambda p: p.update(blur=26),
            lambda p: p.update(blur=True),
            lambda p: p["items"][0].update(start="24:00"),
            lambda p: p["items"][0].update(start="7:30"),
            lambda p: p["items"][0].update(start="07:30"),
            lambda p: p["items"][0].update(anchor="sunrise"),
            lambda p: p["items"][0].update(file="missing.png"),
        ]
        original = json.dumps(self.plan)
        for mutate in mutations:
            with self.subTest(mutation=mutate):
                self.plan = json.loads(original)
                mutate(self.plan)
                with self.assertRaises(ValueError):
                    self.make()
                self.assertFalse(self.output.exists())

    def test_existing_package_is_never_overwritten(self):
        self.make()
        original = self.output.read_bytes()
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.make()
        self.assertEqual(original, self.output.read_bytes())

    def test_checker_rejects_missing_corrupt_extra_and_mismatched_images(self):
        self.make()
        original = self.output.read_bytes()
        other_image = io.BytesIO()
        Image.new("RGB", (50, 100)).save(other_image, "JPEG")
        for transform in [
            lambda e: e.pop("01.jpg"),
            lambda e: e.update({"01.jpg": b"not an image"}),
            lambda e: e.update({"preview.jpg": e["01.jpg"]}),
            lambda e: e.update({"01.jpg": other_image.getvalue()}),
        ]:
            self.output.write_bytes(original)
            self.rewrite_zip(transform)
            with self.assertRaises((ValueError, OSError)):
                check(self.output)

    def test_target_size_limits(self):
        self.assertEqual((1440, 3120), parse_size("1440x3120"))
        for size in ("0x100", "100X200", "100000x100000", "-1x200"):
            with self.assertRaises(ValueError):
                parse_size(size)


if __name__ == "__main__":
    unittest.main()
