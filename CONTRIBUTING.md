# Contributing to Hazel

Thanks for helping. Hazel is a front end for yt-dlp on Android, used by a lot of people
who never read this page, so every change is judged by one question: does it make the app
better for them without breaking anything they rely on.

| I want to | Go to |
|---|---|
| Report a bug or ask for a feature | [Issues](#issues) |
| Translate Hazel | [Translations](#translations) |
| Change the code | [Pull requests](#pull-requests) |
| Edit the website or user guide | [Website and documentation](#website-and-documentation) |
| Report a security problem | [SECURITY.md](SECURITY.md), never a public issue |

## Issues

Search open **and closed** issues first. If nothing covers it, pick a template and fill in
every field. Issues missing the required details may be closed without review.

- **A site fails to download** (HTTP 403, sign-in walls, "unsupported URL"): update yt-dlp
  in **Settings > Engine** first. If it still fails, it is almost always a yt-dlp extractor
  problem and belongs on the [yt-dlp tracker](https://github.com/yt-dlp/yt-dlp/issues).
- **Bugs**: include the Hazel version, Android version, the link (if public), your custom
  options, and the steps. A short screen recording helps a lot.
- **Features**: Hazel exposes what yt-dlp can do. Requests for things yt-dlp itself does
  not support cannot be accepted.
- **Questions and ideas**: use [Discussions](https://github.com/SibtainOcn/Hazel/discussions).

## Translations

Translations are done on **Weblate**, not by pull request. Weblate opens its own pull
request with the new translations, and it goes through the same checks as any other.

- You can translate any amount. A language is merged at any percentage, and anything not
  yet translated shows in English.
- A language is offered in the app's language picker once it reaches **80%**.
- Copy placeholders (`%1$s`, `%d`, `%(title)s`) and markup (`<b>`, `<a href="...">`)
  exactly as they are, and keep links unchanged. CI rejects a translation that changes them.
- Want a new language? Ask on Weblate or open a Discussion.

**Adding English text in a code change?** Add it to `app/src/main/res/values/strings.xml`
only. Do not edit the `values-*` folders; the translators will pick it up. CI does not fail
because a new string is untranslated.

## Pull requests

### Before you write code

For anything larger than a small fix, open or comment on an issue first and describe what
you plan to change. A pull request nobody agreed to may be closed, however good it is.

### Building

Fork the repository, clone your fork, and open it in the latest Android Studio. JDK 17.

```bash
./gradlew :app:assembleDebug
```

### Rules for the change

- **One change per pull request.** A fix and an unrelated cleanup are two pull requests.
- **Branch names:** `<type>/<short-description>`, for example `fix/queue-retry-crash`.
  Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`.
- **Commit messages:** a short imperative subject, for example "Fix crash when retrying a
  paused download". Explain the why in the body if it is not obvious.
- **Text the user reads** goes in `values/strings.xml`, never as a literal in Kotlin. See
  [tools/README.md](tools/README.md) for the rules and the checker.
- **User-visible changes** get a line under `## [Unreleased]` in `CHANGELOG.md`.
- **Match the code around you**: naming, structure, comment style. No new dependency
  without discussing it first; each one has to build reproducibly for F-Droid.
- **Never commit** keystores, passwords, tokens, cookies or personal data.

### Checks to run before opening it

Run these from the repository root. CI runs the same, and a pull request with a red check
is not reviewed until it is green.

```bash
python tools/strings/check.py resources       # strings.xml integrity
python tools/strings/check.py translations    # translations correct, coverage report
./gradlew :app:testDebugUnitTest              # unit tests
./gradlew :app:assembleDebug                  # it builds
python tools/tests/test_all.py                # everything above plus the feature harnesses
```

Then install it on a phone or emulator and try what you changed. For anything on screen,
attach before and after screenshots to the pull request.

### What happens after you open it

1. CI runs: string checks, unit tests, and a full APK build.
2. A maintainer reviews. Expect questions; answer them in the thread and push fixes to the
   same branch.
3. Once approved and green, a maintainer merges it, usually as a squash.

Pull requests that go quiet for a long time after review comments may be closed. You are
welcome to reopen them.

## Website and documentation

The website, user guide and FAQ live in `pages/hazel-pages`. The donate page at
`pages/index.html` is separate and stays as it is, because F-Droid links to it.

- **Where to edit:** page content is in `pages/hazel-pages/src` (`index.html`,
  `guide.html`, `faq.html`, `support.html`), and styles and scripts are in
  `pages/hazel-pages/static`. The shared header and footer are in `tools/site/build_site.py`.
- **What fills itself in:** the version (from `app/build.gradle.kts`), the app languages
  (from `locales_config.xml`), the "What's new" line (from the newest section of
  `CHANGELOG.md`) and the screenshots (from `fastlane`). Don't type these into the pages.
- **Build and preview:** run `python tools/site/build_site.py`, then open
  `pages/hazel-pages/_build/index.html`. The build fails on long dashes, arrows, ellipses or
  bullet characters, on unfilled placeholders, and on local links, images or `#anchors`
  that point nowhere. CI also checks outside links.
- **Keep the docs in step:** a pull request that changes something users see (a setting, a
  menu name, a new feature) should update the matching part of `guide.html` or `faq.html`
  in the same pull request.
- **Writing style:** plain, friendly language, with no technical terms where an everyday
  word works.

## Using AI tools

AI-assisted contributions are welcome, on the same terms as any other.

1. **You understand every line.** You are accountable for all of it. Do not submit code
   you cannot explain in review.
2. **You tested it yourself.** Built, run and checked locally. CI is not a testing ground
   for code nobody has run.
3. **You say so.** Tick the AI box in the pull request and name the parts it wrote.
4. **It is yours to license.** No copied third-party code; you must have the right to
   release it under the [Hazel License](LICENSE).
5. **It reads like Hazel.** Strip the boilerplate, redundant methods and excess comments AI
   tends to add, and match the existing style.

## License

By contributing, you agree that your contribution is released under the
[Hazel License](LICENSE).
