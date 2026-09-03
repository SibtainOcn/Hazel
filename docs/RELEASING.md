# Shipping a release

Every release moves the same pieces in the same order, and most of them fail silently when
they are wrong. A version code that did not move produces APKs Android refuses to install
over the last ones. A store changelog named after the old code leaves the release blank on
F-Droid. A byte that differs between two builds of the same tag stops F-Droid publishing
the app at all, with no error on this side.

So the order below is not a suggestion. It is the order in which a mistake is still cheap
to fix.

## The one command

```powershell
.\tools\release.ps1 -Version 1.0.9 -DryRun   # look first
.\tools\release.ps1 -Version 1.0.9           # then do it
```

That script bumps `versionCode` by 100 and rewrites `versionName` in
`app/build.gradle.kts`, promotes the `## [Unreleased]` section of `CHANGELOG.md` to the new
version, regenerates the store changelogs, commits, tags `v1.0.9` and pushes. It refuses to
start if the working tree is dirty or the tag already exists.

Do not hand-edit the version. If you already did, do not run the script afterwards or it
bumps a second time.

Before running it, write the release notes under `## [Unreleased]` in `CHANGELOG.md`. The
`### Store` block of that section is what appears on the F-Droid listing, so it is written
for a person choosing whether to install the app. Anything outside that block stays in the
repository for people reading the history.

## What has to be true before the tag

The release workflow checks these and stops rather than publishing something wrong, but
knowing them saves a failed run.

- `versionName` and `versionCode` are literals in `app/build.gradle.kts`. F-Droid reads them
  with a regular expression instead of running Gradle, so anything computed is invisible to
  it and its update check finds no version at all.
- The tag matches `versionName`. Tag `v1.0.9` on a commit that says `1.0.8` means either the
  wrong commit was tagged or the bump was forgotten.
- Five store changelog files exist, named after every code the release publishes: the base
  code and the four architecture offsets. A missing one is silent on F-Droid.
- The version code moves by 100. The last two digits belong to the architecture.

## Version codes

One release publishes five APKs, and each needs its own code. The base code carries the
release and the offsets carry the architecture:

| ABI | offset | 1.0.8 |
|-----|--------|-------|
| universal | 0 | 500 |
| armeabi-v7a | 1 | 501 |
| x86 | 2 | 502 |
| x86_64 | 3 | 503 |
| arm64-v8a | 4 | 504 |

The order is the choice a device makes: it installs the highest code it can run, so each
64-bit entry sits above the 32-bit build it could also run, and universal sits at the bottom
as the fallback for a device none of the others fit. The same offsets appear in three
places and must agree: `abiVersionCodes` in `app/build.gradle.kts`, the table in
`tools/release.ps1`, and `VercodeOperation` in the F-Droid recipe.

## Rules that keep builds reproducible

F-Droid rebuilds the app from source and compares the result against the APK published on
GitHub. Every byte has to match. These are the ways that has broken so far, each of which
cost a release:

- **No dependency metadata in the APK.** The Android Gradle plugin writes a block describing
  the build machine into the APK signing block. `dependenciesInfo { includeInApk = false }`
  turns it off. The scanner rejects any APK that carries it.
- **Do not let the build strip `libdatastore_shared_counter.so`.** The plugin strips debug
  symbols from bundled native libraries using whichever NDK the machine happens to have, and
  the result differs between NDK versions. `keepDebugSymbols` in the `packaging` block ships
  it exactly as the dependency provides it.
- **Never pass `-PVERSION_NAME` on the release path.** The version is already in the file.
  Passing it again is how a build here and a build on the F-Droid server end up disagreeing
  while both look correct.
- **Never move a published tag.** A tag names the bytes that were released under it. If the
  release is wrong, the fix is a new version, not a new meaning for an old one.

## After the tag is pushed

The release workflow builds and publishes the five APKs to the GitHub release. Watch it:

```bash
gh run list --workflow=Release --limit 1
```

Then verify the published bytes rather than trusting the source change. Download one APK and
walk its signing block. The IDs that should be there are `0x7109871a` (signature scheme v2)
and `0x42726577` (padding). The one that must not be there is `0x504b4453`, the dependency
metadata block. Also hash the signer certificate and compare it against
`AllowedAPKSigningKeys` in the F-Droid recipe, because an APK signed with a different key is
rejected no matter how clean it is.

## Updating the F-Droid recipe

The recipe lives in a fork of fdroiddata, not in this repository, and it is only updated
after the GitHub release exists. The jobs there download the URLs in the `binary:` fields,
so pushing the recipe first means they fetch a release that is not there yet.

For each of the five build entries: set `versionName`, `versionCode` and `commit: v<version>`,
then update `CurrentVersion` and `CurrentVersionCode` to the highest code. Two details that
are easy to lose:

- Every `binary:` key needs a trailing space before the newline. The `fdroid rewritemeta` job
  reformats the file and fails on any difference, and a folded value is how it writes a long
  URL. Editors that trim trailing whitespace on save will break this.
- Keep the branch to a single commit rebased on upstream master, and force-push it. The
  merge request is easier to review and the maintainers ask for it anyway.

Then let the pipeline run before opening or updating the merge request. Nine jobs, all of
which must pass: `fdroid build`, `check apk`, `check source code`, `fdroid lint`,
`fdroid rewritemeta`, `checkupdates`, `schema validation`, `git redirect`,
`tools check scripts`.

## When something fails

The job log is the whole story, and the useful part is usually one line among thousands.
Fetch the raw log rather than reading it in the browser:

```bash
curl -sL "https://gitlab.com/<user>/fdroiddata/-/jobs/<job id>/raw" | grep -iE "error|critical|problem"
```

`rewritemeta` failures are always formatting and the log prints the exact diff it wants.
`check apk` failures are about the published bytes, which means the fix is a new release
rather than an edit to the recipe. A reproducibility failure names the file that differed,
and that file is the clue: it is nearly always something the build environment touched
rather than something the code changed.
