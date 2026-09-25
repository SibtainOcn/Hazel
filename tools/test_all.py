#!/usr/bin/env python3
"""
Master Test Runner for Hazel.

Executes all verification and test suites locally with a single command:
1. Release versioning check (GitHub public repo release vs Gradle inside version)
2. Resource integrity (tools/check.py resources)
3. Translation parity across 10 locales (tools/check.py translations)
4. String literal extraction progress (tools/check.py progress)
5. Artist metadata test harness (tools/test_artist_metadata.py)
6. Release readiness & regression harness (tools/test_release_regression_harness.py)
7. Controls & multi-source harness (tools/test_download_controls_and_sources.py)
8. JVM unit tests (gradlew :app:testDebugUnitTest)

Usage:
    python tools/test_all.py
Exit:
    0 if all verification and test suites pass, 1 if any suite fails.
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

# Ensure UTF-8 output on Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent
IS_WINDOWS = sys.platform.startswith("win")
GRADLE_CMD = [str(REPO_ROOT / "gradlew.bat")] if IS_WINDOWS else [str(REPO_ROOT / "gradlew")]
GITHUB_REPO = "SibtainOcn/Hazel"


def parse_semver(v: str) -> tuple[int, ...]:
    """Parse a semantic version string like 'x.x.x' into a tuple of ints for comparison."""
    clean = v.strip().lstrip("v").split("-")[0]
    parts = []
    for p in clean.split("."):
        try:
            parts.append(int(p))
        except ValueError:
            parts.append(0)
    return tuple(parts)


def check_versioning() -> tuple[bool, str]:
    """
    Check versioning: automatically checks the latest released version via the
    public GitHub repository.

    READ-ONLY: Does not change or modify any files.
    """
    print(f"\n{'='*70}")
    print(f"  CHECKING: Release Versioning (GitHub Public Repo & Gradle Inside)")
    print(f"{'='*70}")

    gradle_file = REPO_ROOT / "app" / "build.gradle.kts"
    changelog_file = REPO_ROOT / "CHANGELOG.md"

    # 1. Read Gradle inside version (versionName & versionCode)
    if not gradle_file.is_file():
        msg = "app/build.gradle.kts not found!"
        print(f">>> [FAIL] {msg}")
        return False, msg

    gradle_text = gradle_file.read_text(encoding="utf-8")
    m_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
    m_code = re.search(r'versionCode\s*=\s*(\d+)', gradle_text)

    if not m_name or not m_code:
        msg = "Could not find literal versionName and versionCode in app/build.gradle.kts"
        print(f">>> [FAIL] {msg}")
        return False, msg

    gradle_version_name = m_name.group(1).strip()
    gradle_version_code = int(m_code.group(1).strip())

    # 2. Check latest release from public GitHub repository automatically
    github_release_tag = None
    github_release_name = None
    try:
        url = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "Hazel-Test-Runner",
                "Accept": "application/vnd.github.v3+json",
            },
        )
        with urllib.request.urlopen(req, timeout=6) as resp:
            if resp.status == 200:
                data = json.loads(resp.read().decode("utf-8"))
                github_release_tag = data.get("tag_name", "").strip()
                github_release_name = data.get("name", "").strip()
    except Exception as e:
        # Network timeout or offline - fall back to local git tags
        pass

    # 3. Local git tags fallback / cross-check
    local_tag_version = None
    try:
        tag_out = subprocess.check_output(
            ["git", "tag", "-l", "--sort=v:refname", "v*"],
            cwd=REPO_ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip().splitlines()
        if tag_out:
            local_tag_version = tag_out[-1].strip().lstrip("v")
    except Exception:
        pass

    # 4. CHANGELOG.md inspection
    last_cl_version = None
    has_unreleased = False
    if changelog_file.is_file():
        cl_text = changelog_file.read_text(encoding="utf-8")
        cl_versions = re.findall(r"^##\s*\[(\d+\.\d+\.\d+[^\]]*)\]", cl_text, re.MULTILINE)
        if cl_versions:
            last_cl_version = cl_versions[0].strip()

        m_unreleased = re.search(r"^##\s*\[Unreleased\](.*?)(?=^##\s*\[|\Z)", cl_text, re.MULTILINE | re.DOTALL)
        if m_unreleased:
            content = m_unreleased.group(1).strip()
            if any(marker in content for marker in ["###", "-", "*"]):
                has_unreleased = True

    # Determine latest released version
    if github_release_tag:
        last_released = github_release_tag.lstrip("v")
        source = f"GitHub public repo ({GITHUB_REPO})"
    elif local_tag_version:
        last_released = local_tag_version
        source = "Local git tag"
    elif last_cl_version:
        last_released = last_cl_version
        source = "CHANGELOG.md"
    else:
        last_released = "0.0.0"
        source = "Unknown"

    print(f"  Source                  : {source}")
    if github_release_tag:
        print(f"  GitHub latest release   : {github_release_tag} ({github_release_name or 'Release'})")
    if local_tag_version:
        print(f"  Local git latest tag    : v{local_tag_version}")
    print(f"  CHANGELOG latest release: {last_cl_version or 'None'}")
    print(f"  Unreleased notes        : {'Present in CHANGELOG.md' if has_unreleased else 'Empty'}")
    print(f"  Gradle inside version   : {gradle_version_name} (versionCode = {gradle_version_code})")

    # Compare Gradle inside version with last released version
    gradle_semver = parse_semver(gradle_version_name)
    released_semver = parse_semver(last_released)

    is_updated = gradle_semver > released_semver

    if not is_updated:
        # Print exact message requested
        print(f"\n>>> [NOTICE] version NOT update you must update for the newer version relase")
        print(f"    (Gradle inside version '{gradle_version_name}' is not newer than latest released '{last_released}')")
        return False, "version NOT update you must update for the newer version relase"
    else:
        print(f"\n>>> [OK] Version is updated: {gradle_version_name} > {last_released} (versionCode = {gradle_version_code})")
        return True, f"Version updated ({gradle_version_name} > {last_released})"


def run_step(name: str, cmd: list[str]) -> bool:
    print(f"\n{'='*70}")
    print(f"  RUNNING: {name}")
    print(f"  COMMAND: {' '.join(cmd)}")
    print(f"{'='*70}")

    start_time = time.time()
    result = subprocess.run(cmd, cwd=REPO_ROOT)
    elapsed = time.time() - start_time

    if result.returncode == 0:
        print(f"\n>>> [OK] {name} passed in {elapsed:.1f}s")
        return True
    else:
        print(f"\n>>> [FAIL] {name} failed with exit code {result.returncode} ({elapsed:.1f}s)")
        return False


def main():
    py = sys.executable

    print("=" * 70)
    print("  Hazel Master Test Suite")
    print("=" * 70)

    # Step 1: Versioning check (Read-only check against GitHub public repo & Gradle)
    version_updated, version_msg = check_versioning()

    # Step 2..8: Execution of all verification and test suites
    steps = [
        ("String Resources Check", [py, "tools/check.py", "resources"]),
        ("10-Locale Translation Parity", [py, "tools/check.py", "translations"]),
        ("Literal Extraction Progress", [py, "tools/check.py", "progress"]),
        ("Artist Metadata Harness", [py, "tools/test_artist_metadata.py"]),
        ("Home Search UX & Data Preservation Harness", [py, "tools/test_home_search_ux_and_preservation.py"]),
        ("Release Regression Harness", [py, "tools/test_release_regression_harness.py"]),
        ("Controls & Multi-Source Harness", [py, "tools/test_download_controls_and_sources.py"]),
        ("Gradle JVM Unit Tests", GRADLE_CMD + [":app:testDebugUnitTest", "--console=plain"]),
    ]

    results = []

    for name, cmd in steps:
        success = run_step(name, cmd)
        results.append((name, success))
        if not success:
            # Stop on first failure
            break

    print("\n" + "=" * 70)
    print("  TEST RUN SUMMARY")
    print("=" * 70)
    all_passed = True
    for name, passed in results:
        status = "PASSED" if passed else "FAILED"
        print(f"  [{status}] {name}")
        if not passed:
            all_passed = False

    if len(results) < len(steps):
        skipped = len(steps) - len(results)
        print(f"  [SKIPPED] {skipped} remaining step(s) due to previous failure")
        all_passed = False

    print("-" * 70)
    if version_updated:
        print(f"  [VERSION] {version_msg}")
    else:
        print(f"  [VERSION NOTICE] {version_msg}")
    print("=" * 70)

    if all_passed:
        if not version_updated:
            print("  ALL TEST SUITES PASSED!")
            print("  Remember: version NOT update you must update for the newer version relase")
            print("  (Run tools/release.ps1 <new_version> when ready to cut the release)")
        else:
            print("  ALL SUITES PASSED AND VERSION IS READY FOR RELEASE!")
        print("=" * 70)
        return 0
    else:
        print("  SOME SUITES FAILED! Fix errors before cutting a release.")
        print("=" * 70)
        return 1


if __name__ == "__main__":
    sys.exit(main())

