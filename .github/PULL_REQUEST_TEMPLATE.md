## What this changes

<!-- One or two sentences. What does the user (or the code) do differently after this? -->

Closes #

## Why

<!-- The problem this solves. Link the issue where it was discussed. -->

## Type

- [ ] Bug fix
- [ ] New feature
- [ ] Change to existing behaviour
- [ ] Refactor, no behaviour change
- [ ] Docs or website
- [ ] Build, CI or tooling

## How I tested it

<!-- Device or emulator, Android version, and the steps you took. -->

- Device / Android version:
- Steps:

- [ ] `python tools/strings/check.py resources` passes
- [ ] `python tools/strings/check.py translations` passes
- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] Installed and tried the change on a device or emulator

## Screenshots

<!-- Required for anything on screen: before and after. Delete this section otherwise. -->

| Before | After |
|---|---|
|  |  |

## Checklist

- [ ] It was discussed in an issue first (anything larger than a small fix)
- [ ] One change only, no unrelated edits
- [ ] New text is in `values/strings.xml`, with no literals in Kotlin and no edits to `values-*`
- [ ] User-visible changes have a line under `[Unreleased]` in `CHANGELOG.md`
- [ ] The user guide or FAQ is updated if users see something different
- [ ] No new dependency, or it was agreed in the issue
- [ ] No keystores, tokens, cookies or personal data in the diff

## AI use

- [ ] AI tools wrote a significant part of this. Which parts:

<!-- You stay responsible for every line. See CONTRIBUTING.md. -->
