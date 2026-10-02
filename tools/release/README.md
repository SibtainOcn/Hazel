# Cutting a release

> **For maintainers only.** Contributors do not need anything on this page.

`release.ps1` makes a new release. It changes the version, commits, creates the tag and
pushes. The tag starts the Release workflow on GitHub, which builds and publishes the
APKs. F-Droid finds the new tag by itself within a day or two.

## Steps before running it

1. **`CHANGELOG.md`**: the changes are listed under `## [Unreleased]`. The script renames
   that heading to the new version.
2. **Store changelogs**: five short text files in
   `fastlane/metadata/android/en-US/changelogs/`, written by hand. One file for each APK
   version code. Example: if the current `versionCode` is 900, the next release needs

   ```
   1000.txt  1001.txt  1002.txt  1003.txt  1004.txt
   ```

   - Same text in all five.
   - 1 to 500 characters each. F-Droid cuts anything longer.
   - Short lines, no PR numbers.
   - Commit them **before** running the script. F-Droid reads them from the commit the
     tag points to, not from the latest `main`.
3. **Clean working tree** on `main`, nothing uncommitted.

## Running it

```powershell
.\tools\release\release.ps1 -Version 1.1.12 -DryRun   # only shows the plan, changes nothing
.\tools\release\release.ps1 -Version 1.1.12           # makes the release
.\tools\release\release.ps1 -Version 1.1.12 -NoPush   # commit and tag only, no push
```

## When it stops

All checks run first. If one fails, the script stops and nothing in the repository is
changed.

| Check | Stops when |
|---|---|
| Tag | `v<version>` already exists. A published tag is never moved. |
| Store changelogs | One of the five files is missing, empty, or longer than 500 characters. |
| Translations | `tools/strings/check.py translations` fails: a broken placeholder, markup or link, a key that is not in English, bad escaping, wrong plural forms, or a listed language under 80%. |
| `CHANGELOG.md` | There is no `[Unreleased]` or `[<version>]` heading. |

A language that is not fully translated **does not stop the release**. It is shown as a
warning, and the app shows English for the missing text. To list every missing key as an
error, run:

```
python tools/strings/check.py translations --strict
```

## What it does, in order

1. Adds 100 to `versionCode` and sets `versionName` in `app/build.gradle.kts`.
2. Renames `[Unreleased]` in `CHANGELOG.md` to the new version with today's date.
3. Commits the build file, `CHANGELOG.md` and the store changelogs, then creates the tag
   `v<version>`.
4. Pushes `main`, then the tag.

## Languages in the app

A language appears in the app's language list (`app/src/main/java/com/hazel/android/util/AppLocale.kt`)
and in Android's per-app language setting (`app/src/main/res/xml/locales_config.xml`) only
when it is at least 80% translated. A new language can be merged at any percentage; the
app already uses whatever is translated. When the translations check prints
`note: xx is 85% translated and could be listed`, add that language to both files.

## Also in this folder

`fastlane_yaml_checker/` checks the store text and the F-Droid recipe. It has its own
README.
