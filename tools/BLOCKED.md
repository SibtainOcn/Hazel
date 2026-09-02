# Blocked on a decision

Text that cannot be moved into `strings.xml` without changing something other than the
text. Extraction replaces words and nothing else, so each of these is recorded here rather
than improvised, and each is its own change once somebody decides how.

Nothing here is a bug introduced by the extraction. Every entry describes something that
was already true in the Kotlin and only became visible when the words were pulled out of
it.

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
