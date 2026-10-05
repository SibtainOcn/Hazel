# CI and releases

A map of Hazel's workflows for maintainers. Each workflow file explains its own steps in
comments; this page says what runs when, what it may touch, and how to act on it.
Contributors only need [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Branches

```
feat/xyz  --(PR, squash)-->  nightly  --(PR, merge commit)-->  main  --(tag vX.Y.Z)-->  release
```

- `main` and `nightly` take changes through pull requests only.
- Required checks: **Unit tests** and **String resources**.
- Merge `nightly` into `main` with a merge commit, never squash or rebase, so the two keep
  one history and a release can be reverted as one commit: `git revert -m 1 <merge-sha>`.

## Workflows

| File | Runs on | Does |
|---|---|---|
| [ci.yml](ci.yml) | Pull requests into `main`, `nightly`, `feat/**`; pushes to `main`; manual | String checks, unit tests with both Python harnesses, APK build |
| [release.yml](release.yml) | A `v*` tag pushed; manual with an existing tag | Tests, then builds every architecture signed and publishes the GitHub release |
| [pages.yml](pages.yml) | Pushes to `main` touching the website or its sources; pull requests touching the website | Builds and checks the website, checks outside links, deploys to GitHub Pages |

A push to `nightly` does not run CI by itself. Every change reaches it through a pull request,
which is checked, and `nightly` is the head of the release pull request, so a push run would
check the same commit twice.

### CI jobs

- **String resources:** `tools/strings/check.py`. Translation coverage is reported in the job
  summary, not enforced.
- **Unit tests:** `tools/tests/test_artist_metadata.py`,
  `tools/tests/test_release_regression_harness.py`, then `:app:testDebugUnitTest`.
  [scripts/test_summary.py](../scripts/test_summary.py) writes the counts to the job summary
  and posts them as the **Test results** check, e.g. `348/348 passed · Artist 52/52 ·
  Regression 167/167`, with each failing test flagged on its file.
- **Build APK:** debug on pull requests (unsigned, universal). Release on a push to `main`
  (signed, arm64-v8a uploaded for 3 days).

Test results is informational. Do not make it a required check: pull requests from forks
cannot post it, so it would block them.

## Cutting a release

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`, and move the changelog's
   Unreleased section under the new version.
2. Run `./gradlew :app:generateFastlaneChangelogs` so the five store changelogs exist.
3. Merge into `main`, then tag that commit:
   ```bash
   git tag v1.2.0
   git push origin v1.2.0
   ```
4. `release.yml` stops if the tag disagrees with `versionName`, a store changelog is missing,
   or no keystore is configured. A tag with a suffix (`v1.2.0-beta.1`) publishes a pre-release.

To undo a bad release, revert on `main` and ship a new version. Never move or reuse a tag that
was published.

## Secrets

| Secret | Holds |
|---|---|
| `KEYSTORE_BASE64` | The release keystore, base64 encoded |
| `KEYSTORE_PASSWORD` | Its store password |
| `KEY_ALIAS` | The signing key alias |
| `KEY_PASSWORD` | The signing key password |

Only two jobs read them: **Build APK** on a push to `main`, and **Build and publish** in
`release.yml`. Pull requests, forks included, never get them and build unsigned. The keystore
is written to the runner's temp folder and deleted when the job ends, pass or fail. Never
commit a keystore or `signing.properties`; `.gitignore` blocks both.

## Permissions

Workflows default to read-only. Write access is given per job, only where it is needed:

| Job | Gets |
|---|---|
| Unit tests (CI) | `checks: write`, to post Test results |
| Build and publish (release) | `contents: write`, to create the release |
| Deploy (pages) | `pages: write`, `id-token: write` |

## Third-party actions

Actions from outside GitHub (`softprops/action-gh-release`, `lycheeverse/lychee-action`) are
pinned to a full commit, with the version as a comment:

```yaml
uses: softprops/action-gh-release@efb35369e0ad2afab669f228072c1b0d510eae64 # v3.0.3
```

A tag can be moved to other code; a commit cannot. Dependabot ([dependabot.yml](../dependabot.yml))
checks actions monthly and opens a pull request that updates both the commit and the comment.
Read the action's release notes before merging it. Add any new third-party action pinned the
same way, and never give it a job that holds secrets unless it has to.

## Pull requests from forks

- A first-time contributor's run waits for **Approve and run workflows** on the pull request.
  Read the diff first, especially anything under `.github/` or `tools/`.
- Tests and builds run as usual, without secrets and with a read-only token.
- The Test results check cannot be posted, so the counts are only in the run's summary
  (Details, then Summary). This does not fail the run.
- To run again, use **Re-run jobs** on the run, or push to the branch.
