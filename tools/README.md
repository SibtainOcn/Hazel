# Text in Hazel

Everything a person reads on screen lives in `app/src/main/res/values/strings.xml`, and
nowhere else. This file says how to keep it that way, how to check it, and how to add a
language.

Read the section that matches what you are doing. If you are an agent, read the whole
thing before writing anything, and read [`BLOCKED.md`](BLOCKED.md) as well.

| I want to | Go to |
|---|---|
| Add a screen, a button, a message | [Writing new text](#writing-new-text) |
| Move existing text out of Kotlin | [Job 1: extraction](#job-1-extraction) |
| Add Spanish, Hindi, anything | [Job 2: translation](#job-2-translation) |
| Know what the checker is telling me | [The checker](#the-checker) |
| Know why some literal is allowed to stay | [The allowlist](#the-allowlist) |

---

## Where things are

```
tools/
  check.py                 the gate: run it, believe it
  literal-allowlist.txt    literals deliberately left in the Kotlin, each with a reason
  BLOCKED.md               text that needs a code change before it can be extracted
  README.md                this file
app/src/main/res/
  values/strings.xml       the English source. Every key starts life here.
  values-<lang>/strings.xml  one per language, added in Job 2
```

`check.py` needs Python 3 and nothing else. Run it from the repository root.

---

## Writing new text

Three rules. They are short because almost every mistake is one of these.

### 1. No literal reaches a user

```kotlin
// no
Text("Download all")

// yes
Text(stringResource(R.string.download_download_all))
```

Outside a composable, where there is no `stringResource`, use a `Context`:

```kotlin
Toast.makeText(context, context.getString(R.string.search_nothing_to_paste), LENGTH_SHORT)
```

Name the key `screen_what_it_says`, lower snake case:

```
download_download_all      good
sponsor_hero_title         good
properties_saved_to        good
text_4                     rejected by the gate
downloadDownloadAll        rejected by the gate
```

One key per place the text appears. Two buttons that both say `Cancel` today get two keys,
because a translator may need them to differ and because one may change without the other.
The exception is one control drawn in two layouts of the same screen: the row menu and the
card menu on the downloads list share `download_pause`, because they are one menu and
letting them drift apart would be the bug.

### 2. A sentence is one key

Never build a sentence out of parts. Word order is not the same in every language, so a
frame with a hole in it is a frame that only fits English.

```kotlin
// no, the branch splits the sentence
"This source has no separate ${if (video) "video" else "audio"} stream."

// no, this just moves the seam into a placeholder
stringResource(R.string.no_stream, stringResource(R.string.video))

// yes, two whole sentences
if (video) stringResource(R.string.format_sheet_no_video_stream)
else stringResource(R.string.format_sheet_no_audio_stream)
```

A placeholder is for a value that is never translated: a filename, a byte count, a
hostname, a version.

```xml
<string name="download_instant_source">Instant · reading from %1$s</string>
```

`check.py audit` prints every key holding a placeholder so a person can check what arrives
in the slot. If the answer is another `stringResource`, it is sentence assembly and it
needs splitting.

### 3. A number in front of a noun is a plural

```xml
<plurals name="download_links">
    <item quantity="one">%d link</item>
    <item quantity="other">%d links</item>
</plurals>
```

```kotlin
pluralStringResource(R.plurals.download_links, count, count)
```

English needs `one` and `other`. Other languages need more, and the checker knows which
from the CLDR table it carries. Never write `"$n links"`.

### Escaping

XML, so a few characters need care:

| In the text | Write |
|---|---|
| `'` | `\'` |
| `&` | `&amp;` |
| `<` `>` | `&lt;` `&gt;` |
| `·` | `·` |

The checker fails on an unescaped apostrophe or a bare ampersand.

### What stays in the Kotlin

Log lines, exception messages, code comments, yt-dlp arguments, format ids, file
extensions, Compose animation labels, and anything else nobody reads. If the checker flags
one of these, put it in the allowlist with a reason.

---

## Job 1: extraction

Moving text that is already written out of Kotlin and into `strings.xml`. **One file at a
time.** The loop exists so that when something breaks, exactly one file can be responsible.

```
1. python tools/check.py progress          pick the file at the top
2. read that file, all of it, before writing anything
3. extract that file's text, and only that file's
4. python tools/check.py all <that file>
5. green -> commit that one file. red -> fix it. either way, do not start another.
6. python tools/check.py audit HEAD        after committing
```

### Copy, do not edit

If you cannot point at the line you copied it from, it does not go in `strings.xml`.
Same capitalisation, same punctuation, same spelling. If the source says `Save dir`, the
resource says `Save dir`, even though `Save directory` reads better. Improving the wording
is a different change and a different commit.

`audit` is the check that proves this: it reads back what a commit added and looks for each
value in the file that commit converted. `22 of 22 verbatim` means the words were moved.
Anything less means they were rewritten, and rewritten text is not extraction.

### Change nothing else

Extraction replaces text. It does not reorder code, rename anything, add an unused import,
or tidy something on the way past. The gate fails on an import nothing uses and on a diff
that deletes far more than it adds, because both are what pattern matching looks like.

If a string cannot be moved without changing a function signature, **stop and write it in
[`BLOCKED.md`](BLOCKED.md)**. Do not invent a `Context` parameter, do not change a
constructor. Those need a decision first and they are a separate pass.

### Commit message

Imperative, naming the file and nothing else:

```
Move the format sheet text into string resources
```

One file per commit, plus its `strings.xml` entries and any allowlist line it needed. That
is what keeps the blast radius check meaningful: it compares the working tree against the
last commit, so a clean baseline is what lets it catch an edit to a file nobody asked you
to touch.

---

## Job 2: translation

**Do not start until `progress` reads 0.** Translating while text is still being extracted
means translating a key set that is still moving, and every later extraction invalidates
part of the work.

### Adding a language

1. Create `app/src/main/res/values-<code>/strings.xml`. The code is the ISO 639-1 letters,
   with a region only where it matters: `values-es`, `values-hi`, `values-pt-rBR`.
   Android picks the file from the device language. No code changes, ever.
2. Copy every `<string>` and `<plurals>` from `values/strings.xml` and translate the text.
   Keep the `name` exactly. Do not add keys the source does not have, and do not translate
   anything marked `translatable="false"`.
3. Placeholders carry through untouched. `%1$s` stays `%1$s`, and it stays pointing at the
   same thing. Inline markup carries through too.
4. Plural categories are per language. Spanish needs `one`, `many`, `other`. Arabic needs
   six. Japanese needs only `other`. The checker knows the table and will say what is
   missing or surplus.
5. `python tools/check.py translations`

### What the translation check enforces

- the key set matches the source exactly, nothing missing and nothing invented
- no key marked `translatable="false"` was translated
- placeholders are the same set in every language
- inline markup is unchanged
- apostrophes are escaped
- plural categories are the ones the language actually defines

Android's own lint covers the overlapping half of this once `values-*` exists:
`MissingTranslation`, `ExtraTranslation`, `StringFormat`, `StringFormatMatches`,
`ImpliedQuantity`, `MissingQuantity`. `check.py build` runs lint and reports those.

### Right to left

Arabic, Hebrew, Farsi and Urdu mirror the layout: icons swap sides, alignment flips.
Compose does most of it if the layout used `Start`/`End` rather than `Left`/`Right`. It
still deserves a pass on a device before shipping.

---

## The checker

```
python tools/check.py resources          strings.xml integrity, seconds, no build
python tools/check.py file <path>        one file: blast radius, imports, literals
python tools/check.py progress           what is left, per file
python tools/check.py translations       every values-* against the source
python tools/check.py build              assembleDebug, unit tests, resource lint
python tools/check.py all <path>         resources + file + build
python tools/check.py audit <commit>     proves a commit copied rather than reworded
```

Exit code 0 is `PASS`. Anything else is `NOT DONE`.

**The gate decides whether a file is done. You do not.** Do not report a file finished
without the gate output. Do not tick anything by hand.

`resources` and `translations` run in CI on every pull request, as the **String resources**
job in `.github/workflows/ci.yml`. The remaining literal count is posted to the run summary
but does not fail the build, so the number is visible without blocking work.

### What each check is guarding against

| Check | The failure it exists to catch |
|---|---|
| duplicate keys | two entries, same name, the last one silently wins |
| dangling references | `R.string.x` with no `x`, which is a build break |
| orphan keys | keys written from a plan for a file nobody opened |
| escaping | an apostrophe that turns the value into a parse error |
| plurals | a plural with no `other`, which crashes in some language |
| placeholder call sites | a key with `%1$s` called with no argument, so the user sees the token |
| sentence assembly | a translated word dropped into a translated frame |
| blast radius | a commit that quietly edited a file it was not about |
| added imports | an import nothing uses, the cheapest sign of pattern matching |
| residual literals | a file called done with text still in it |
| audit | text that was reworded rather than moved |

---

## The allowlist

`literal-allowlist.txt`. One line per literal the checker finds that is deliberately not
extracted:

```
<file>:<line>: <why this text never reaches a user>
```

```
app/.../DownloadScreen.kt:444: Compose animation label, used by the tooling inspector only
app/.../DownloadScreen.kt:1067: two formatted byte counts joined by a slash, no words to translate
```

A reason must say why nobody reads it. **"Looks fine" is not a reason.** There is no third
option: every hit is either extracted or dispositioned, and the gate stays red until each
one is.

The line numbers are real line numbers, so they go stale when a file moves. The checker
reports an allowlist line that no longer matches anything, because an entry pointing at the
wrong line is a literal nobody is checking any more. Fix the number, do not delete the
entry, unless the literal is genuinely gone.

### What the checker cannot see

It matches text, so it has limits worth knowing:

- a literal behind a conditional, `value = if (x) "On" else "Off"`, is not matched
- text passed through a variable before it reaches a composable is not matched
- a custom composable with a parameter name nobody added to the pattern is not matched

`progress` reading 0 means nothing the pattern knows about is left. It does not mean the
file has been read. Read the file.

If you find a parameter name in this codebase that holds visible text and is not in the
`LITERAL` pattern in `check.py`, add it. That has already happened three times, and each
time the real count went up.
