"""Builds app/src/main/assets/gallery-dl.zip from the pinned wheels in requirements.txt.

The zip runs as `python gallery-dl.zip <args>` on the Python the app bundles. Every wheel is
pure Python and checked against its sha256 by pip. Entries are sorted and carry a fixed
timestamp, so the same pins always give the same file.

Usage, from the repository root:
    python tools/gallery-dl/build_payload.py
"""

import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "gallery-dl.zip"
PYTHON_VERSION = "3.12"
FIXED_TIME = (1980, 1, 1, 0, 0, 0)
MAIN = "import sys\nfrom gallery_dl import main\nsys.exit(main())\n"
SKIPPED_PARTS = {"__pycache__"}


def download(target: Path) -> None:
    subprocess.run(
        [
            sys.executable, "-m", "pip", "download",
            "--no-deps", "--only-binary=:all:", "--require-hashes",
            "--platform", "any", "--implementation", "py",
            "--python-version", PYTHON_VERSION,
            "-r", str(HERE / "requirements.txt"),
            "-d", str(target),
        ],
        check=True,
    )


def unpack(wheels: Path, target: Path) -> None:
    for wheel in sorted(wheels.glob("*.whl")):
        with zipfile.ZipFile(wheel) as archive:
            for name in archive.namelist():
                top = name.split("/", 1)[0]
                if top.endswith((".dist-info", ".data")):
                    continue
                if SKIPPED_PARTS.intersection(name.split("/")):
                    continue
                archive.extract(name, target)


def pack(source: Path, output: Path) -> None:
    files = sorted(p for p in source.rglob("*") if p.is_file())
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        entry = zipfile.ZipInfo("__main__.py", FIXED_TIME)
        entry.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(entry, MAIN)
        for path in files:
            entry = zipfile.ZipInfo(path.relative_to(source).as_posix(), FIXED_TIME)
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, path.read_bytes())


def main() -> int:
    with tempfile.TemporaryDirectory() as temp:
        temp_dir = Path(temp)
        wheels = temp_dir / "wheels"
        tree = temp_dir / "tree"
        download(wheels)
        unpack(wheels, tree)
        if not (tree / "gallery_dl" / "__init__.py").is_file():
            print("gallery_dl package missing from the wheels", file=sys.stderr)
            return 1
        partial = OUTPUT.with_suffix(".zip.part")
        pack(tree, partial)
        shutil.move(partial, OUTPUT)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size:,} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
