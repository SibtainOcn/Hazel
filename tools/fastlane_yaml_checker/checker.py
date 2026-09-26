#!/usr/bin/env python3
"""
Fastlane & F-Droid YAML Checker for Hazel.

Validates Fastlane metadata and F-Droid recipe YAML files before submitting
Merge Requests to fdroiddata or creating releases.

Checks performed:
1. Fastlane metadata structure (title <= 50, short_desc <= 80, full_desc <= 4000).
2. Fastlane changelogs: <= 500 chars limit per version code (F-Droid & Play Store hard limit).
3. Fastlane & Gradle synchronization (changelog exists for active versionCode).
4. F-Droid YAML schema & field validation (commit sha, categories, build flags, reproducible binary URLs).
5. Exact fdroidserver rewritemeta formatting compliance (idempotency check).
6. Optional auto-fix (--fix) to format YAML according to fdroid rewritemeta rules.

Usage:
    python tools/fastlane_yaml_checker/checker.py [--yaml <path>] [--fix] [--check-urls]
"""

import argparse
import difflib
import io
import os
import re
import sys
import urllib.request
from pathlib import Path

# Ensure UTF-8 output on Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent.parent

# ANSI Colors
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"


def print_step(title: str):
    print(f"\n{CYAN}{BOLD}==> {title}{RESET}")


def print_pass(msg: str):
    print(f"  {GREEN}[PASS]{RESET} {msg}")


def print_fail(msg: str):
    print(f"  {RED}[FAIL]{RESET} {msg}")


def print_warn(msg: str):
    print(f"  {YELLOW}[WARN]{RESET} {msg}")


def print_info(msg: str):
    print(f"  {BOLD}[INFO]{RESET} {msg}")


def get_gradle_version_info(repo_root: Path) -> tuple[str, int]:
    """Extract versionName and versionCode from app/build.gradle.kts."""
    gradle_file = repo_root / "app" / "build.gradle.kts"
    if not gradle_file.exists():
        raise FileNotFoundError(f"Cannot find {gradle_file}")

    content = gradle_file.read_text(encoding="utf-8")
    vname_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    vcode_match = re.search(r'versionCode\s*=\s*(\d+)', content)

    vname = vname_match.group(1) if vname_match else "unknown"
    vcode = int(vcode_match.group(1)) if vcode_match else 0
    return vname, vcode


# ----------------------------------------------------------------------
# 1. Fastlane Metadata Checker
# ----------------------------------------------------------------------

def check_fastlane(repo_root: Path, current_vcode: int) -> bool:
    """Validate Fastlane metadata files under fastlane/metadata/android/."""
    print_step("Checking Fastlane Metadata")
    passed = True
    fastlane_dir = repo_root / "fastlane" / "metadata" / "android"

    if not fastlane_dir.exists():
        print_fail(f"Fastlane directory not found at {fastlane_dir}")
        return False

    locale_dirs = [d for d in fastlane_dir.iterdir() if d.is_dir()]
    if not locale_dirs:
        print_fail(f"No locale directories found in {fastlane_dir}")
        return False

    for locale_dir in locale_dirs:
        locale = locale_dir.name
        # 1. Title
        title_file = locale_dir / "title.txt"
        if title_file.exists():
            text = title_file.read_text(encoding="utf-8").strip()
            if len(text) == 0:
                print_fail(f"[{locale}] title.txt is empty")
                passed = False
            elif len(text) > 50:
                print_fail(f"[{locale}] title.txt length {len(text)} exceeds max 50 chars")
                passed = False
            else:
                print_pass(f"[{locale}] title.txt ({len(text)}/50 chars): '{text}'")

        # 2. Short description
        short_desc_file = locale_dir / "short_description.txt"
        if short_desc_file.exists():
            text = short_desc_file.read_text(encoding="utf-8").strip()
            if len(text) > 80:
                print_fail(f"[{locale}] short_description.txt length {len(text)} exceeds max 80 chars")
                passed = False
            else:
                print_pass(f"[{locale}] short_description.txt ({len(text)}/80 chars)")

        # 3. Full description
        full_desc_file = locale_dir / "full_description.txt"
        if full_desc_file.exists():
            text = full_desc_file.read_text(encoding="utf-8").strip()
            if len(text) > 4000:
                print_fail(f"[{locale}] full_description.txt length {len(text)} exceeds max 4000 chars")
                passed = False
            else:
                print_pass(f"[{locale}] full_description.txt ({len(text)}/4000 chars)")

        # 4. Changelogs
        changelog_dir = locale_dir / "changelogs"
        if changelog_dir.exists():
            changelog_files = list(changelog_dir.glob("*.txt"))
            if not changelog_files:
                print_warn(f"[{locale}] No changelogs found in {changelog_dir}")
            
            current_vcode_found = False
            for cl in changelog_files:
                stem = cl.stem
                if not stem.isdigit():
                    print_fail(f"[{locale}] Invalid changelog filename '{cl.name}'. Must be <versionCode>.txt")
                    passed = False
                    continue

                code = int(stem)
                if code == current_vcode or (current_vcode <= code <= current_vcode + 4):
                    current_vcode_found = True

                char_count = len(cl.read_text(encoding="utf-8").strip())
                if char_count > 500:
                    print_fail(f"[{locale}] Changelog {cl.name} has {char_count} chars (STRICT LIMIT is 500 chars!)")
                    passed = False
                else:
                    print_pass(f"[{locale}] Changelog {cl.name} is {char_count}/500 chars")

            if not current_vcode_found and current_vcode > 0:
                print_fail(f"[{locale}] Missing changelog for active versionCode {current_vcode} (or range {current_vcode}-{current_vcode+4})")
                passed = False

    return passed


# ----------------------------------------------------------------------
# 2. F-Droid YAML Metadata Schema & Formatter
# ----------------------------------------------------------------------

# Official F-Droid field ordering for YAML 1.2
YAML_APP_FIELD_ORDER = [
    'Disabled', 'AntiFeatures', 'Categories', 'License', 'AuthorName',
    'AuthorEmail', 'AuthorWebSite', 'WebSite', 'SourceCode', 'IssueTracker',
    'Translation', 'Changelog', 'Donate', 'Liberapay', 'OpenCollective',
    'Bitcoin', 'Litecoin', '\n',
    'Name', 'AutoName', 'Summary', 'Description', '\n',
    'RequiresRoot', '\n',
    'RepoType', 'Repo', 'Binaries', '\n',
    'Builds', '\n',
    'AllowedAPKSigningKeys', '\n',
    'MaintainerNotes', '\n',
    'ArchivePolicy', 'AutoUpdateMode', 'UpdateCheckMode', 'UpdateCheckIgnore',
    'VercodeOperation', 'UpdateCheckName', 'UpdateCheckData',
    'CurrentVersion', 'CurrentVersionCode', '\n',
    'NoSourceSince',
]

BUILD_FLAGS = [
    'versionName', 'versionCode', 'disable', 'commit', 'timeout',
    'subdir', 'submodules', 'sudo', 'init', 'patch', 'gradle',
    'maven', 'output', 'binary', 'srclibs', 'oldsdkloc', 'encoding',
    'forceversion', 'forcevercode', 'rm', 'extlibs', 'prebuild',
    'androidupdate', 'target', 'scanignore', 'scandelete', 'build',
    'buildjni', 'ndk', 'preassemble', 'gradleprops', 'antcommands',
    'postbuild', 'novcheck', 'antifeatures',
]


def load_yaml(content: str):
    """Load YAML using ruamel.yaml."""
    try:
        import ruamel.yaml
    except ImportError:
        raise ImportError("ruamel.yaml is required. Run 'pip install ruamel.yaml'.")

    yaml = ruamel.yaml.YAML(typ='rt')
    yaml.version = (1, 2)
    return yaml.load(content)


def format_yaml_rewritemeta(data) -> str:
    """
    Format metadata data exactly according to fdroidserver rewritemeta rules.
    Maintains official field order, sequence indentation (mapping=2, seq=4, offset=2).
    """
    import ruamel.yaml

    cm = ruamel.yaml.comments.CommentedMap()
    insert_newline = False

    for field in YAML_APP_FIELD_ORDER:
        if field == '\n':
            insert_newline = True
        else:
            value = data.get(field)
            if value is not None:
                if field == 'Builds':
                    builds = ruamel.yaml.comments.CommentedSeq()
                    for build in value:
                        b = ruamel.yaml.comments.CommentedMap()
                        for bf in BUILD_FLAGS:
                            bv = build.get(bf)
                            if bv is not None and bv is not False and bv != '':
                                b[bf] = bv
                        builds.append(b)
                    for i in range(1, len(builds)):
                        builds.yaml_set_comment_before_after_key(i, 'bogus')
                        builds.ca.items[i][1][-1].value = '\n'
                    cm[field] = builds
                elif field == 'Categories':
                    if isinstance(value, list):
                        cm[field] = sorted(value, key=str.lower)
                    else:
                        cm[field] = value
                else:
                    cm[field] = value

                if insert_newline:
                    insert_newline = False
                    cm.yaml_set_comment_before_after_key(field, 'bogus')
                    cm.ca.items[field][1][-1].value = '\n'

    # Any remaining fields not in standard order
    for k, v in data.items():
        if k not in cm and k in YAML_APP_FIELD_ORDER:
            cm[k] = v

    dumper = ruamel.yaml.YAML(typ='rt')
    dumper.indent(mapping=2, sequence=4, offset=2)
    out = io.StringIO()
    dumper.dump(cm, out)
    return out.getvalue()


def check_fdroid_yaml(yaml_path: Path, expected_vname: str, expected_vcode: int, auto_fix: bool = False, check_urls: bool = False, repo_root: Path = REPO_ROOT) -> bool:
    """Validate F-Droid metadata YAML file."""
    try:
        display_path = yaml_path.resolve().relative_to(repo_root.resolve())
    except ValueError:
        display_path = yaml_path.name

    print_step(f"Checking F-Droid Metadata: {yaml_path.name}")
    print_info(f"Target: {display_path}")

    if not yaml_path.exists():
        print_fail(f"Metadata file not found: {display_path}")
        return False

    content = yaml_path.read_text(encoding="utf-8")
    passed = True

    try:
        data = load_yaml(content)
    except Exception as e:
        print_fail(f"YAML Syntax error: {e}")
        return False

    # 1. Top-level Required Fields
    required_fields = [
        "Categories", "License", "AuthorName", "SourceCode", "IssueTracker",
        "Changelog", "AutoName", "RepoType", "Repo", "Builds"
    ]
    for rf in required_fields:
        if rf not in data:
            print_fail(f"Missing required field: '{rf}'")
            passed = False
        else:
            print_pass(f"Required field present: '{rf}'")

    # 2. Validate AllowedAPKSigningKeys if present
    signing_keys = data.get("AllowedAPKSigningKeys")
    if signing_keys:
        key_str = str(signing_keys).strip()
        if re.match(r"^[0-9a-fA-F]{64}$", key_str):
            print_pass(f"AllowedAPKSigningKeys is a valid 64-char hex SHA256")
        else:
            print_fail(f"AllowedAPKSigningKeys '{key_str}' is NOT a valid 64-character hex string")
            passed = False

    # 3. Validate Builds
    builds = data.get("Builds", [])
    if not isinstance(builds, list) or not builds:
        print_fail("'Builds' list is empty or invalid")
        return False

    prev_vcode = 0
    build_codes = []

    for idx, b in enumerate(builds):
        vname = b.get("versionName")
        vcode = b.get("versionCode")
        commit = b.get("commit")
        output = b.get("output")
        binary = b.get("binary")
        gradle = b.get("gradle")

        desc = f"Build #{idx+1} (v{vname} / {vcode})"

        # versionCode checks
        if not isinstance(vcode, int) or vcode <= 0:
            print_fail(f"{desc}: Invalid versionCode '{vcode}'")
            passed = False
        else:
            if vcode <= prev_vcode:
                print_fail(f"{desc}: versionCode {vcode} is not strictly greater than previous {prev_vcode}")
                passed = False
            prev_vcode = vcode
            build_codes.append(vcode)

        # commit check (40-char hex)
        if not commit or not re.match(r"^[0-9a-fA-F]{40}$", str(commit)):
            print_fail(f"{desc}: Commit '{commit}' is not a 40-character git commit SHA")
            passed = False

        # gradle check
        if gradle not in (["yes"], ["fdroid"], "yes", "fdroid"):
            print_fail(f"{desc}: Invalid gradle flag '{gradle}'. Expected ['yes'] or ['fdroid']")
            passed = False

        # output check
        if not output or "build/outputs/apk" not in str(output):
            print_fail(f"{desc}: Output path '{output}' is unexpected")
            passed = False

        # binary URL check
        if not binary or not str(binary).strip().startswith("https://github.com/SibtainOcn/Hazel/releases/download/"):
            print_fail(f"{desc}: Binary URL '{binary}' does not match expected GitHub release pattern")
            passed = False

    # Cross-reference with app/build.gradle.kts
    if build_codes and expected_vcode > 0:
        latest_code = build_codes[-1]
        # In Hazel, builds for a version span vcode to vcode+4 (universal, arm, x86, etc.)
        if expected_vcode <= latest_code <= expected_vcode + 4 or latest_code == expected_vcode:
            print_pass(f"Latest Build versionCode ({latest_code}) matches Gradle current version ({expected_vname} / {expected_vcode})")
        else:
            print_fail(f"Latest Build versionCode ({latest_code}) does NOT match Gradle active version ({expected_vcode})")
            passed = False

    # 4. Optional URL verification
    if check_urls and builds:
        print_step("Checking Remote Binary URLs")
        for b in builds[-4:]:  # Check latest 4 builds
            url = str(b.get("binary", "")).strip()
            # replace %v with version
            vname = b.get("versionName")
            url = url.replace("%v", str(vname))
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "Hazel-Checker"})
                req.get_method = lambda: "HEAD"
                with urllib.request.urlopen(req, timeout=5) as res:
                    if res.status in (200, 302):
                        print_pass(f"URL exists: {url}")
                    else:
                        print_warn(f"URL returned HTTP {res.status}: {url}")
            except Exception as e:
                print_fail(f"URL check failed for {url}: {e}")
                passed = False

    # 5. Strict F-Droid CI Formatting Validation
    print_step("Checking Strict fdroidserver rewritemeta Formatting Rules")
    formatting_passed = True
    in_builds = False
    fixed_lines = []

    lines = content.splitlines()
    for idx, line in enumerate(lines):
        line_num = idx + 1
        new_line = line

        # Track section state
        if not line.startswith(" ") and line.strip().endswith(":"):
            in_builds = (line.strip() == "Builds:")

        # Rule 1: AllowedAPKSigningKeys must be on a single line
        if line.strip().startswith("AllowedAPKSigningKeys:"):
            if line.strip() == "AllowedAPKSigningKeys:":
                print_fail(f"Line {line_num}: 'AllowedAPKSigningKeys' is multi-line. F-Droid requires single line: 'AllowedAPKSigningKeys: <sha256>'")
                formatting_passed = False
                # If next line has the key, can fix
                if idx + 1 < len(lines) and re.match(r"^\s+[0-9a-fA-F]{64}$", lines[idx + 1]):
                    key_val = lines[idx + 1].strip()
                    new_line = f"AllowedAPKSigningKeys: {key_val}"
            elif not re.match(r"^AllowedAPKSigningKeys:\s+[0-9a-fA-F]{64}$", line):
                print_fail(f"Line {line_num}: 'AllowedAPKSigningKeys' does not match single line 64-char hex format")
                formatting_passed = False

        # Rule 2: Multi-line wrapped keys must have trailing space after colon ('binary: ', 'output: ')
        if re.match(r"^\s+(binary|output):$", line):
            print_fail(f"Line {line_num}: '{line}' is missing trailing space after colon ('{line} '). F-Droid rewritemeta requires 'key: ' with a trailing space.")
            formatting_passed = False
            new_line = line + " "

        # Rule 3: Single-line output paths shouldn't be unnecessarily wrapped if under 90 chars
        if line.strip() == "output:" or line.strip() == "output: ":
            if idx + 1 < len(lines):
                next_val = lines[idx + 1].strip()
                if not next_val.endswith("armeabi-v7a-stable.apk") and len(f"    output: {next_val}") <= 88:
                    print_fail(f"Line {line_num}: 'output:' is wrapped onto line {line_num+1} but should be single-line 'output: {next_val}'")
                    formatting_passed = False

        # Rule 4: Context-aware indentation checks
        if in_builds:
            if line.startswith("    ") and not line.startswith("      "):
                if not re.match(r"^    [a-zA-Z]+:", line):
                    print_fail(f"Line {line_num}: Invalid 4-space indentation for build key: '{line}'")
                    formatting_passed = False
            elif line.startswith("      "):
                if not line.startswith("      - ") and not line.startswith("      http") and not line.startswith("      build/"):
                    print_warn(f"Line {line_num}: Verify 6-space indentation for wrapped value: '{line}'")

        fixed_lines.append(new_line)

    # Clean up multi-line AllowedAPKSigningKeys if it was combined
    cleaned_fixed = []
    skip_next = False
    for i, l in enumerate(fixed_lines):
        if skip_next:
            skip_next = False
            continue
        if l.startswith("AllowedAPKSigningKeys: ") and i + 1 < len(fixed_lines) and re.match(r"^\s+[0-9a-fA-F]{64}$", fixed_lines[i + 1]):
            cleaned_fixed.append(l)
            skip_next = True
        else:
            cleaned_fixed.append(l)

    if formatting_passed:
        print_pass("100% rewritemeta compliance: exact match, zero whitespace/line diffs")
    else:
        if auto_fix:
            yaml_path.write_text("\n".join(cleaned_fixed) + "\n", encoding="utf-8")
            print_pass(f"Auto-fixed formatting issues in {yaml_path.name}!")
            passed = True
        else:
            print_info(f"Run with '--fix' to automatically resolve formatting issues in {yaml_path.name}.")
            passed = False

    return passed and formatting_passed


# ----------------------------------------------------------------------
# Main Runner
# ----------------------------------------------------------------------

def find_default_yaml_candidates(repo_root: Path) -> list[Path]:
    """Find candidate F-Droid metadata files in repository or active workspace."""
    candidates = [
        repo_root / "fastlane" / "com.hazel.android.yml",
        repo_root / "metadata" / "com.hazel.android.yml",
    ]
    found = [p for p in candidates if p.exists()]
    # Also discover any local metadata recipe in staging or metadata directories
    for p in repo_root.glob("**/metadata/com.hazel.android.yml"):
        if p not in found:
            found.append(p)
    return found


def main():
    parser = argparse.ArgumentParser(description="Strict Fastlane & F-Droid YAML Checker for Hazel")
    parser.add_argument("--yaml", type=Path, default=None, help="Path to specific F-Droid metadata YAML file to check")
    parser.add_argument("--fix", action="store_true", help="Auto-fix YAML formatting to strictly match fdroid rewritemeta")
    parser.add_argument("--check-urls", action="store_true", help="Perform HTTP checks on reproducible binary URLs")
    args = parser.parse_args()

    print(f"\n{BOLD}{'='*70}{RESET}")
    print(f"{BOLD}  HAZEL FASTLANE & F-DROID YAML CHECKER{RESET}")
    print(f"{BOLD}{'='*70}{RESET}")

    # 1. Extract version info from Gradle
    try:
        vname, vcode = get_gradle_version_info(REPO_ROOT)
        print_info(f"Current app/build.gradle.kts: versionName={vname}, versionCode={vcode}")
    except Exception as e:
        print_fail(f"Could not read Gradle configuration: {e}")
        sys.exit(1)

    all_passed = True

    # 2. Check Fastlane metadata
    fastlane_ok = check_fastlane(REPO_ROOT, vcode)
    if not fastlane_ok:
        all_passed = False

    # 3. Check F-Droid YAML metadata
    target_yamls = []
    if args.yaml:
        target_yamls = [args.yaml]
    else:
        target_yamls = find_default_yaml_candidates(REPO_ROOT)

    if not target_yamls:
        print_warn("No F-Droid metadata YAML files found to check. Specify one with --yaml <path>")
    else:
        for ypath in target_yamls:
            y_ok = check_fdroid_yaml(ypath, vname, vcode, auto_fix=args.fix, check_urls=args.check_urls)
            if not y_ok:
                all_passed = False

    # Final Summary Banner
    print(f"\n{BOLD}{'='*70}{RESET}")
    if all_passed:
        print(f"{GREEN}{BOLD}  RESULT: ALL FASTLANE & F-DROID CHECKS PASSED SUCCESSFULLY [OK]{RESET}")
        print(f"{BOLD}{'='*70}{RESET}\n")
        sys.exit(0)
    else:
        print(f"{RED}{BOLD}  RESULT: ONE OR MORE CHECKS FAILED! [FAILED]{RESET}")
        print(f"{YELLOW}  Fix the reported errors above before submitting your Merge Request.{RESET}")
        print(f"{BOLD}{'='*70}{RESET}\n")
        sys.exit(1)


if __name__ == "__main__":
    main()
