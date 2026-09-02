# Blocked on a decision

Text that cannot be moved into `strings.xml` without changing something other than the
text. Extraction replaces words and nothing else, so each of these is recorded here rather
than improvised, and each is its own change once somebody decides how.

Nothing here is a bug introduced by the extraction. Every entry describes something that
was already true in the Kotlin and only became visible when the words were pulled out of
it.

---

## The generic format rows carry their label as text

**Where** `app/src/main/java/com/hazel/android/download/MediaProbe.kt`, `BEST_VIDEO` and
`BEST_AUDIO`.

**What it does now**

```kotlin
private val BEST_VIDEO = MediaFormat(
    formatId = "best",
    label = "Best quality",
    ...
)
```

`Best quality` and `Best audio` are the rows a source gets when it reported no usable format
list, so both are read by users, and both are English in the Kotlin.

**Why it cannot be moved as it stands** `MediaFormat.label` is a plain `String`, and for
every real format it holds text derived from what the engine reported. Only these two
constants hold English, so the field cannot simply become a resource id.

Resolving them through `HazelApp.instance.getString(...)` was tried and reverted. It
compiles and it works on a device, but `MediaProbe`'s parser is covered by pure JVM unit
tests, `HazelApp.instance` is a `lateinit` that no such test initialises, and nine tests
failed with `UninitializedPropertyAccessException`. The parser is context free on purpose;
that is what makes it testable, and it is worth more than these two strings.

**What it needs** Either a nullable `@StringRes labelRes` on `MediaFormat` that the six
display sites prefer over `label` when it is set, or the two rows built by the caller, which
does have a `Context`. Both are design decisions about a model class rather than text moves.

**Until then** the two literals stay, and `progress` reports 2 rather than 0. They are not
in the allowlist, because an allowlist entry has to say the text never reaches a user and
that would be untrue.

---

## `ActionRow` builds its content description out of a translated label

**Where** `app/src/main/java/com/hazel/android/ui/screens/more/DirectShareScreen.kt`,
the `ActionRow` composable.

**What it does now**

```kotlin
contentDescription = stringResource(R.string.direct_share_change, label)
```

with `direct_share_change` holding `Change %1$s`, and every caller passing a label that is
itself a `stringResource`: `Chapters`, `Subtitles`, `SponsorBlock`, `Cookies`,
`Filename template`, `Audio language`.

**Why it is wrong** The screen reader says "Change Chapters" by dropping one translated
word into an English sentence frame. That works in English and in very little else: German
puts the verb last, and a language with grammatical case needs the noun inflected for the
slot it lands in. This is the failure the extraction rules call sentence assembly, and it
is why `check.py audit` prints every placeholder for a human to trace.

It was already there before the extraction, written as `"Change $label"`. Moving it into a
resource neither caused it nor fixed it.

**What it needs** One whole sentence per row, which means `ActionRow` taking the
description as its own argument rather than deriving it:

```kotlin
ActionRow(
    label = stringResource(R.string.direct_share_chapters),
    changeDescription = stringResource(R.string.direct_share_change_chapters),
    ...
)
```

Six new keys, one per caller, and a signature change on `ActionRow`. The signature is what
makes it a separate change: the extraction rules forbid altering a composable's parameters
on the way past, because a diff that both moves text and changes shape is a diff nobody can
review for either.
