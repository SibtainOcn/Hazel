# Testing

## Running the tests

```bash
./gradlew :app:testDebugUnitTest
```

The whole suite is plain JVM code and finishes in seconds. An HTML report lands in
`app/build/reports/tests/testDebugUnitTest/index.html`, and the machine-readable results CI
reads are in `app/build/test-results/`.

To run one class or one test:

```bash
./gradlew :app:testDebugUnitTest --tests '*LinkKeyTest'
```

## What is covered, and why only this

Two things are tested, and both were chosen for the same reason: they are pure functions
whose failures are silent. Nothing on screen says the wrong answer was given, so without a
test the bug ships and is noticed weeks later as "it downloaded the same video twice" or
"that site stopped working".

| Suite | Covers | The failure it catches |
|---|---|---|
| `LinkKeyTest` | `LinkKey`, reducing a link to the media it points at | Two spellings of one video stop matching, so the metadata cache misses every time and a repeat download is never caught |
| `MediaProbeParseTest` | `MediaProbe.parse`, reading a yt-dlp payload into a `MediaInfo` | A source that reports less than YouTube does stops producing usable formats, and the link simply looks broken |

The parser tests are fed saved payloads, not live ones. A test that reaches a real site
fails when that site changes, which says nothing about this code and trains everyone to
ignore a red build.

## Adding tests

Put them under `app/src/test/java/…`, mirroring the package of what they cover. CI picks up
new files with no configuration: the totals it reports are read from the results the run
produced.

Two rules are worth holding to as the suite grows:

**Unit tests stay pure JVM.** No emulator, no Android framework, no network. That is what
keeps the check to seconds at ten times this many tests. Something that genuinely needs a
device belongs in `app/src/androidTest/` and in its own opt-in workflow, not in the check
that gates every pull request.

**Test the behaviour, not the shape.** Name the test after the thing a user would notice
breaking. `a timestamp does not make it a different video` survives a rewrite of the
function; `canonical returns lowercase` does not.

## What CI runs

`.github/workflows/ci.yml` runs on every pull request and on every push to `main`. Tests and
the build run as separate jobs, so they start together and neither waits on the other. A
pull request builds one unsigned debug APK; only a push to `main` builds release.

`.github/workflows/release.yml` runs the same tests before it packages anything, and stops
the release if they fail.
