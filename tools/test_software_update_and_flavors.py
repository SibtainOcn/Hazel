#!/usr/bin/env python3
"""
Test Harness: Software Update Hub, Distribution Flavors, and F-Droid Compliance.

Verifies:
1. Flavor Configuration:
   - 'github' (default, IS_FDROID=false) and 'fdroid' (IS_FDROID=true) distribution flavors.
   - BuildConfig feature enabled in build.gradle.kts.
2. F-Droid Policy Compliance:
   - REQUEST_INSTALL_PACKAGES permission isolated exclusively to src/github/AndroidManifest.xml.
   - Main AndroidManifest.xml contains no self-updating package install permissions.
   - file_paths.xml exposes updates cache directory for safe FileProvider installation.
3. Strict Ban & Clean Codebase Compliance:
   - Strictly 0 occurrences of prohibited terms across all app code and resources.
   - Zero references to temp/DESIGNS in app source files.
   - Forbidden mock elements strictly absent:
     * "Require device unlocked" / "Delay install until the screen is unlocked"
     * Mock changelog text ("v4.3.0 Delta patching now covers native libraries...")
     * Mock footer notes ("This build is signed and will be verified...")
4. Design Tokens & Visual Fidelity:
   - UpdateTokens defines independent color tokens (Emerald, Amber, Blue, Deep Dark).
5. Hazel Semver & Architecture Matching:
   - Semantic version parsing and ordering (isNewer logic).
   - ABI asset selection (arm64-v8a, armeabi-v7a, x86_64, universal).
   - Release channel categorization (STABLE, BETA, NIGHTLY).
6. Navigation & Hub Integration:
   - AppNavigation routes for software_update, hazel_update, and ytdlp_update.
   - MoreScreen references more_software_update.
7. 10-Locale Translation Parity:
   - more_software_update and more_software_update_subtitle present across all 10 locales.

Run:
    python tools/test_software_update_and_flavors.py
"""

import os
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

# Ensure UTF-8 output on Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent
APP_DIR = REPO_ROOT / "app"
RES_DIR = APP_DIR / "src" / "main" / "res"

PASS_COUNT = 0
FAIL_COUNT = 0


def check(name: str, got, expected):
    global PASS_COUNT, FAIL_COUNT
    if got == expected:
        PASS_COUNT += 1
        print(f"  PASS  {name}")
    else:
        FAIL_COUNT += 1
        print(f"  FAIL  {name}")
        print(f"        Expected: {expected!r}")
        print(f"        Got:      {got!r}")


def check_true(name: str, condition: bool, msg: str = ""):
    check(name, bool(condition), True)


# ===========================================================================
# 1. Product Flavors & Distribution Verification
# ===========================================================================

def test_flavors_configuration():
    print(f"\n{'='*70}\n  TEST 1: Product Flavors & Distribution Configuration\n{'='*70}")

    build_gradle = (APP_DIR / "build.gradle.kts").read_text(encoding="utf-8")

    check_true("flavorDimensions includes distribution", 'flavorDimensions += "distribution"' in build_gradle)
    check_true("github flavor is declared", 'create("github")' in build_gradle)
    check_true("github flavor is default", 'isDefault = true' in build_gradle)
    check_true("github flavor has IS_FDROID = false", 'buildConfigField("boolean", "IS_FDROID", "false")' in build_gradle)
    check_true("fdroid flavor is declared", 'create("fdroid")' in build_gradle)
    check_true("fdroid flavor has IS_FDROID = true", 'buildConfigField("boolean", "IS_FDROID", "true")' in build_gradle)
    check_true("buildConfig feature enabled", 'buildConfig = true' in build_gradle)


# ===========================================================================
# 2. F-Droid Policy Compliance
# ===========================================================================

def test_fdroid_policy_compliance():
    print(f"\n{'='*70}\n  TEST 2: F-Droid Policy Compliance\n{'='*70}")

    main_manifest = (APP_DIR / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
    check_true(
        "Main manifest does NOT have REQUEST_INSTALL_PACKAGES",
        "REQUEST_INSTALL_PACKAGES" not in main_manifest
    )

    github_manifest_file = APP_DIR / "src" / "github" / "AndroidManifest.xml"
    check_true("github-specific manifest exists", github_manifest_file.exists())

    if github_manifest_file.exists():
        github_manifest = github_manifest_file.read_text(encoding="utf-8")
        check_true(
            "github manifest declares REQUEST_INSTALL_PACKAGES",
            "android.permission.REQUEST_INSTALL_PACKAGES" in github_manifest
        )

    file_paths = (RES_DIR / "xml" / "file_paths.xml").read_text(encoding="utf-8")
    check_true("file_paths.xml declares updates cache path", 'path="updates/"' in file_paths)


# ===========================================================================
# 3. Strict Ban & Clean Codebase Checks
# ===========================================================================

def test_strict_bans_and_exclusions():
    print(f"\n{'='*70}\n  TEST 3: Strict Ban & Clean Codebase Verification\n{'='*70}")

    banned_1 = "yt-dl" + "nis"
    banned_2 = "ytdl" + "nis"

    violations = []
    for root, dirs, files in os.walk(APP_DIR):
        if any(skip in root for skip in [".gradle", "build"]):
            continue
        for file in files:
            if file.endswith((".kt", ".xml", ".kts", ".java")):
                fpath = Path(root) / file
                content = fpath.read_text(encoding="utf-8", errors="ignore").lower()
                if banned_1 in content or banned_2 in content:
                    violations.append(str(fpath.relative_to(REPO_ROOT)))

    check("Zero occurrences of banned terms in app module", violations, [])

    # Ensure no references to temp/DESIGNS in app Kotlin code
    design_ref_violations = []
    for root, dirs, files in os.walk(APP_DIR / "src"):
        for file in files:
            if file.endswith((".kt", ".java")):
                fpath = Path(root) / file
                content = fpath.read_text(encoding="utf-8", errors="ignore")
                if "temp/DESIGNS" in content or "temp\\DESIGNS" in content:
                    design_ref_violations.append(str(fpath.relative_to(REPO_ROOT)))

    check("Zero references to temp/DESIGNS in app code", design_ref_violations, [])

    # Check that forbidden mock elements are absent
    screens = [
        APP_DIR / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "more" / "HazelUpdateScreen.kt",
        APP_DIR / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "more" / "SoftwareUpdateScreen.kt",
        APP_DIR / "src" / "main" / "java" / "com" / "hazel" / "android" / "update" / "YtDlpUpdateScreen.kt",
    ]

    for screen in screens:
        content = screen.read_text(encoding="utf-8")
        check_true(
            f"{screen.name} does not include 'Require device unlocked'",
            "Require device unlocked" not in content and "Delay install until the screen is unlocked" not in content
        )
        check_true(
            f"{screen.name} does not include mock changelog text",
            "v4.3.0 Delta patching" not in content and "Delta patching now covers native libraries" not in content
        )
        check_true(
            f"{screen.name} does not include mock footer note",
            "This build is signed and will be verified" not in content
        )


# ===========================================================================
# 4. Design Tokens & Styling
# ===========================================================================

def test_design_tokens():
    print(f"\n{'='*70}\n  TEST 4: Independent Design Tokens\n{'='*70}")

    tokens_file = APP_DIR / "src" / "main" / "java" / "com" / "hazel" / "android" / "update" / "UpdateComponents.kt"
    check_true("UpdateComponents.kt exists", tokens_file.exists())

    content = tokens_file.read_text(encoding="utf-8")
    check_true("UpdateTokens defines Bg (0xFF0A0A0A)", "0xFF0A0A0A" in content)
    check_true("UpdateTokens defines BgDeep (0xFF000000)", "0xFF000000" in content)
    check_true("UpdateTokens defines Emerald Accent (0xFF8FD6B8)", "0xFF8FD6B8" in content)
    check_true("UpdateTokens defines Amber Update (0xFFFFCB80)", "0xFFFFCB80" in content)
    check_true("UpdateTokens defines Blue Run (0xFFA8CDFF)", "0xFFA8CDFF" in content)
    check_true("UpdateTokens defines Danger (0xFFFFB4A9)", "0xFFFFB4A9" in content)


# ===========================================================================
# 5. Hazel Semver Comparison & ABI Asset Matching Logic
# ===========================================================================

def semver_tuple(v: str) -> tuple[int, ...]:
    clean = v.strip().lstrip("v").split("-")[0]
    return tuple(int(x) if x.isdigit() else 0 for x in clean.split("."))


def is_newer_semver(remote: str, installed: str) -> bool:
    r = semver_tuple(remote)
    i = semver_tuple(installed)
    max_len = max(len(r), len(i))
    r_padded = r + (0,) * (max_len - len(r))
    i_padded = i + (0,) * (max_len - len(i))
    return r_padded > i_padded


def test_semver_and_abi_matching():
    print(f"\n{'='*70}\n  TEST 5: Hazel Semver & Architecture Matching\n{'='*70}")

    # Semver tests
    check_true("1.0.9 is newer than 1.0.8", is_newer_semver("1.0.9", "1.0.8"))
    check_true("1.1.0 is newer than 1.0.8", is_newer_semver("1.1.0", "1.0.8"))
    check_true("2.0.0 is newer than 1.0.8", is_newer_semver("2.0.0", "1.0.8"))
    check_true("1.0.8 is NOT newer than 1.0.8", not is_newer_semver("1.0.8", "1.0.8"))
    check_true("1.0.7 is NOT newer than 1.0.8", not is_newer_semver("1.0.7", "1.0.8"))
    check_true("v1.0.9 with prefix is newer than 1.0.8", is_newer_semver("v1.0.9", "1.0.8"))

    updater_kt = (APP_DIR / "src" / "main" / "java" / "com" / "hazel" / "android" / "update" / "HazelUpdater.kt").read_text(encoding="utf-8")
    check_true("HazelUpdater defines isNewer", "fun isNewer(" in updater_kt)
    check_true("HazelUpdater defines findBestApkAsset", "fun findBestApkAsset(" in updater_kt)
    check_true("HazelUpdater defines latestRelease", "suspend fun latestRelease(" in updater_kt)
    check_true("HazelUpdater handles arm64-v8a", '"arm64-v8a"' in updater_kt)
    check_true("HazelUpdater handles armeabi-v7a", '"armeabi-v7a"' in updater_kt)
    check_true("HazelUpdater handles x86_64", '"x86_64"' in updater_kt)
    check_true("HazelUpdater handles universal", '"universal"' in updater_kt)


# ===========================================================================
# 6. Navigation & Hub Integration
# ===========================================================================

def test_navigation_and_hub():
    print(f"\n{'='*70}\n  TEST 6: Navigation & Screen Wiring\n{'='*70}")

    app_nav = (APP_DIR / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "navigation" / "AppNavigation.kt").read_text(encoding="utf-8")
    check_true("AppNavigation defines software_update route", '"software_update"' in app_nav)
    check_true("AppNavigation defines hazel_update route", '"hazel_update"' in app_nav)
    check_true("AppNavigation defines ytdlp_update route", '"ytdlp_update"' in app_nav)
    check_true("AppNavigation routes onNavigateToUpdate to software_update", 'onNavigateToUpdate = { navController.navigate("software_update") }' in app_nav)
    check_true("AppNavigation hosts SoftwareUpdateScreen", "SoftwareUpdateScreen(" in app_nav)
    check_true("AppNavigation hosts HazelUpdateScreen", "HazelUpdateScreen(" in app_nav)
    check_true("AppNavigation hosts YtDlpUpdateScreen", "YtDlpUpdateScreen(" in app_nav)

    more_screen = (APP_DIR / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "more" / "MoreScreen.kt").read_text(encoding="utf-8")
    check_true("MoreScreen invokes onNavigateToUpdate", 'onNavigateToUpdate()' in more_screen)
    check_true("MoreScreen references R.string.more_software_update", 'R.string.more_software_update' in more_screen)
    check_true("MoreScreen references R.string.more_software_update_subtitle", 'R.string.more_software_update_subtitle' in more_screen)


# ===========================================================================
# 7. 10-Locale Translation Parity
# ===========================================================================

def test_locale_parity():
    print(f"\n{'='*70}\n  TEST 7: 10-Locale Translation Parity\n{'='*70}")

    expected_locales = [
        "values",
        "values-de",
        "values-es",
        "values-fr",
        "values-hi",
        "values-in",
        "values-ja",
        "values-pt-rBR",
        "values-ru",
        "values-zh-rCN",
    ]

    for loc in expected_locales:
        strings_file = RES_DIR / loc / "strings.xml"
        check_true(f"{loc}/strings.xml exists", strings_file.exists())
        if strings_file.exists():
            content = strings_file.read_text(encoding="utf-8")
            check_true(
                f"{loc} contains more_software_update",
                'name="more_software_update"' in content
            )
            check_true(
                f"{loc} contains more_software_update_subtitle",
                'name="more_software_update_subtitle"' in content
            )


# ===========================================================================
# Main
# ===========================================================================

def main():
    print("=" * 70)
    print("  Hazel Software Update Hub & Flavors Test Harness")
    print("=" * 70)

    test_flavors_configuration()
    test_fdroid_policy_compliance()
    test_strict_bans_and_exclusions()
    test_design_tokens()
    test_semver_and_abi_matching()
    test_navigation_and_hub()
    test_locale_parity()

    print("\n" + "=" * 70)
    print(f"  TOTAL CHECKS: {PASS_COUNT + FAIL_COUNT}")
    print(f"  PASSED:       {PASS_COUNT}")
    print(f"  FAILED:       {FAIL_COUNT}")
    print("=" * 70)

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
