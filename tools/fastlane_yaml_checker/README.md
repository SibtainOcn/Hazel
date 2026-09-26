# Fastlane & F-Droid YAML Checker

Automated verification and linting suite for Android Fastlane metadata and F-Droid recipe files (`com.hazel.android.yml`).

## Purpose

Prevents CI failures in F-Droid Merge Request pipelines (such as `fdroid rewritemeta`, schema validation errors, or store changelog length rejections) and Google Play publishing errors before commits are created or pushed.

## Features & Checks

### 1. Fastlane Store Listings & Metadata
- **Titles & Descriptions**:
  - `title.txt`: Must not be empty, max 50 characters (Play Store & F-Droid standard).
  - `short_description.txt`: Max 80 characters.
  - `full_description.txt`: Max 4000 characters.
- **Changelog Enforcements**:
  - `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
  - **Strict character limit**: Every changelog file must be **<= 500 characters** (F-Droid and Google Play hard limit).
  - **Version Code Synchronization**: Verifies that a changelog exists corresponding to the active `versionCode` in `app/build.gradle.kts`.

### 2. F-Droid Recipe YAML (`com.hazel.android.yml`)
- **Required Top-Level Fields**: Validates presence of `Categories`, `License`, `AuthorName`, `SourceCode`, `IssueTracker`, `Changelog`, `AutoName`, `RepoType`, `Repo`, `Builds`.
- **Builds Verification**:
  - `versionName`: Semantic version format.
  - `versionCode`: Strictly increasing positive integers.
  - `commit`: Valid 40-character git commit SHA.
  - `gradle`: Allowed build flags (`['yes']` or `['fdroid']`).
  - `output`: Matches Gradle artifact path conventions.
  - `binary`: Matches reproducible GitHub release asset URL structure.
- **Gradle Alignment**: Ensures the latest build entry in the YAML matches the active `versionName` and `versionCode` in `app/build.gradle.kts`.
- **Strict `fdroid rewritemeta` Formatting**:
  - Single-line `AllowedAPKSigningKeys: <sha256>`.
  - Single-line `output:` paths for architecture builds.
  - Preserves trailing space on multi-line keys (`binary: \n https://...`).
  - Idempotent YAML indentation (2-space mappings, 4-space build flags, 6-space wrapped sequences).

## Usage

Run the checker from the repository root:

```bash
# Run all default checks (Fastlane store metadata + in-repo F-Droid recipes)
python tools/fastlane_yaml_checker/checker.py

# Check a specific F-Droid recipe YAML file
python tools/fastlane_yaml_checker/checker.py --yaml fastlane/com.hazel.android.yml

# Automatically format and fix formatting discrepancies in-place
python tools/fastlane_yaml_checker/checker.py --yaml path/to/com.hazel.android.yml --fix

# Verify reproducible binary download URLs live against GitHub Releases
python tools/fastlane_yaml_checker/checker.py --check-urls
```

## Exit Status

- `0`: All Fastlane metadata and F-Droid recipe checks passed.
- `1`: One or more validation checks failed. Diagnostic errors and suggested fixes are printed to stdout.
