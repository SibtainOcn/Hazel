"""
Self-tests for the Hazel string extraction and translation work.

Pure Python 3, no dependencies. Run it from the repository root.

    python tools/check.py resources
    python tools/check.py file app/src/main/java/com/hazel/android/ui/screens/download/FormatSheet.kt
    python tools/check.py progress
    python tools/check.py translations
    python tools/check.py all app/src/main/.../FormatSheet.kt

Exit code 0 means the check passed. Anything else means it did not, and no further
file may be touched until it is 0 again.
"""

import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# ---------------------------------------------------------------- plumbing

ROOT = Path(__file__).resolve().parents[1]
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
RES_DIR = ROOT / "app/src/main/res"
SRC_DIR = ROOT / "app/src/main/java"
ALLOWLIST = ROOT / "tools/literal-allowlist.txt"

_failed = False


def fail(msg):
    global _failed
    _failed = True
    print("FAIL: " + msg)


def ok(msg):
    print("ok: " + msg)


def section(title):
    print("\n--- " + title + " ---")


def run(args, stdout_only=False):
    """Run a command at the repository root and hand back (code, output)."""
    try:
        p = subprocess.run(
            args, cwd=ROOT, capture_output=True, text=True, shell=False,
            encoding="utf-8", errors="replace",
        )
        if stdout_only:
            return p.returncode, p.stdout or ""
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except FileNotFoundError:
        return 127, "command not found: " + " ".join(args)


def gradle(task):
    exe = "gradlew.bat" if os.name == "nt" else "./gradlew"
    return run([str(ROOT / exe), task, "--console=plain", "-q"])


def git(*args):
    # stdout only. Git writes line ending warnings to stderr on Windows, and those
    # would otherwise be read back as if they were filenames.
    return run(["git"] + list(args), stdout_only=True)


def kt_files():
    return sorted(SRC_DIR.rglob("*.kt"))


def read(path):
    return Path(path).read_text(encoding="utf-8", errors="replace")


# ---------------------------------------------------------------- resources

# Anything that looks like an Android format specifier.
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdfx]|%%")
# Everything a translator must copy through untouched.
MARKUP = re.compile(r"</?(?:b|i|u|a|xliff:g)\b[^>]*>")


def parse_source():
    """Every entry in values/strings.xml, in file order."""
    root = ET.parse(STRINGS).getroot()
    entries = {}
    for el in root:
        if el.tag not in ("string", "plurals"):
            continue
        name = el.get("name")
        translatable = el.get("translatable", "true") != "false"
        if el.tag == "string":
            entries[name] = ("string", el.text or "", translatable)
        else:
            items = {i.get("quantity"): (i.text or "") for i in el.findall("item")}
            entries[name] = ("plurals", items, translatable)
    return entries


def check_resources():
    section("well formed xml")
    try:
        parse_source()
        ok("values/strings.xml parses")
    except ET.ParseError as e:
        fail("values/strings.xml is not well formed XML: %s" % e)
        return

    raw = read(STRINGS)
    names = re.findall(r'name="([A-Za-z_0-9]+)"', raw)

    section("duplicate keys")
    dupes = sorted({n for n in names if names.count(n) > 1})
    if dupes:
        fail("duplicate keys, the last one silently wins: " + ", ".join(dupes))
    else:
        ok("no duplicate keys")

    section("key naming")
    bad_names = []
    for n in set(names):
        if n != n.lower():
            bad_names.append(n + " (not lower_snake_case)")
        # A trailing number is only a problem when it stands in for a meaning, as in
        # text_1 or guide_step_3. A number that is itself the meaning, such as the 1080
        # in a resolution, is a name, so only short sequence-shaped numbers are flagged.
        elif (re.fullmatch(r"(text|string|label|item|msg|option|button)_\d+", n)
              or re.search(r"_\d{1,2}$", n)):
            bad_names.append(n + " (positional, say what it means)")
    if bad_names:
        for b in sorted(bad_names):
            fail("key name: " + b)
    else:
        ok("names are lower_snake_case and say what they mean")

    defined = set(names)
    used = set()
    call_sites = {}
    for f in kt_files():
        body = read(f)
        for m in re.finditer(r"R\.(?:string|plurals)\.([A-Za-z_0-9]+)", body):
            used.add(m.group(1))
            call_sites.setdefault(m.group(1), []).append(f)
    for f in RES_DIR.rglob("*.xml"):
        for m in re.finditer(r"@(?:string|plurals)/([A-Za-z_0-9]+)", read(f)):
            used.add(m.group(1))
    manifest = ROOT / "app/src/main/AndroidManifest.xml"
    if manifest.exists():
        for m in re.finditer(r"@(?:string|plurals)/([A-Za-z_0-9]+)", read(manifest)):
            used.add(m.group(1))

    section("dangling references")
    dangling = sorted(used - defined)
    if dangling:
        fail("used in code, missing from strings.xml: " + ", ".join(dangling))
    else:
        ok("every R.string and R.plurals reference resolves")

    section("orphan keys")
    # The signature of writing strings.xml from a plan rather than from the source file.
    orphans = sorted(defined - used)
    if orphans:
        fail("defined but referenced nowhere, %d of them:" % len(orphans))
        for o in orphans:
            print("      " + o)
        print("   -> either wire them up now, or delete them and add them back when you")
        print("      convert the file that actually contains the text")
    else:
        ok("no orphan keys")

    section("escaping")
    trouble = []
    for m in re.finditer(r"<string[^>]*>(.*?)</string>", raw, re.S):
        value = m.group(1)
        line = raw[: m.start()].count("\n") + 1
        if re.search(r"(?<!\\)'", value):
            trouble.append("line %d: unescaped apostrophe, write \\'" % line)
        if re.search(r"&(?!amp;|lt;|gt;|quot;|apos;|#)", value):
            trouble.append("line %d: bare ampersand, write &amp;" % line)
    if trouble:
        for t in trouble:
            fail(t)
    else:
        ok("apostrophes and ampersands escaped")

    entries = parse_source()

    section("plurals")
    bad_plural = False
    for name, (kind, value, _) in entries.items():
        if kind != "plurals":
            continue
        cats = set(value)
        if "other" not in cats:
            fail("plural %s has no 'other', Android needs it in every language" % name)
            bad_plural = True
        if "one" not in cats:
            fail("plural %s has no 'one', English defines it" % name)
            bad_plural = True
        if not any("%d" in t or "%1$d" in t for t in value.values()):
            fail("plural %s never shows the count, so it did not need to be a plural" % name)
            bad_plural = True
    if not bad_plural:
        ok("plurals well formed")

    section("placeholder call sites")
    bad_call = False
    for name, (kind, value, _) in entries.items():
        if kind != "string" or not re.search(r"%\d+\$", value):
            continue
        for f in call_sites.get(name, []):
            if re.search(r"stringResource\(R\.string\.%s\)" % name, read(f)):
                fail("%s holds a placeholder but %s calls it with no argument, "
                     "the user would see the raw token" % (name, f.name))
                bad_call = True
    if not bad_call:
        ok("keys with placeholders are called with arguments")

    section("sentence assembly")
    # A translated word dropped into a sentence slot. Grammatical in English, wrong
    # in most other languages. One key per whole sentence instead.
    bad_asm = False
    for f in kt_files():
        body = read(f)
        for m in re.finditer(r"stringResource\(\s*R\.string\.([A-Za-z_0-9]+)\s*,\s*stringResource\(", body):
            fail("%s builds a sentence out of another translated string in %s, "
                 "write one key per whole sentence" % (m.group(1), f.name))
            bad_asm = True
    if not bad_asm:
        ok("no sentence built from translated fragments")


# ---------------------------------------------------------------- one file

LITERAL = re.compile(
    # \b keeps writeText(" from matching the bare Text(" case. Notification setters are
    # listed by name because that text is user visible and \b would otherwise hide it.
    #
    # \s* rather than a literal space everywhere a bracket or an equals sign is followed
    # by the text: \s matches a newline, which is what lets a call written over several
    # lines be seen at all.
    r'\bText\(\s*"|contentDescription\s*=\s*"|\btext\s*=\s*"|\btitle\s*=\s*"'
    r'|\blabel\s*=\s*"|\bhint\s*=\s*"|\bplaceholder\s*=\s*"|supportingText|Toast\.makeText'
    r'|setContentText\(\s*"|setContentTitle\(\s*"|setTicker\(\s*"|addAction\([^,]*,\s*"'
    # This app's own composables name their visible text with these, and a row's
    # description or an action's value is read exactly as a title is.
    r'|\bdescription\s*=\s*"|\bsubtitle\s*=\s*"|\bvalue\s*=\s*"|\bmessage\s*=\s*"'
)


def load_allowlist():
    """file:line -> reason, for literals deliberately left alone."""
    entries = {}
    if not ALLOWLIST.exists():
        ALLOWLIST.write_text("", encoding="utf-8")
        return entries
    for raw_line in read(ALLOWLIST).splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(":", 2)
        if len(parts) < 3:
            continue
        path, num, reason = parts[0].strip(), parts[1].strip(), parts[2].strip()
        if num.isdigit() and reason:
            entries[(path.replace("\\", "/"), int(num))] = reason
    return entries


def literal_lines(path):
    """
    Every line a literal hit is reported at, whether or not it is dispositioned.

    One place decides this, because two places disagreed: the hit list matched across the
    whole file while the stale allowlist check re-read the single line on its own, so a
    call written over several lines was reported at a line the second check then said held
    nothing. An entry could not be written that satisfied both.
    """
    text = read(path)
    lines = text.splitlines()
    found = set()
    for m in LITERAL.finditer(text):
        i = text.count("\n", 0, m.start()) + 1
        line = lines[i - 1].strip() if i - 1 < len(lines) else ""
        if line.startswith("//"):
            continue
        # An empty string is a reset, not text. Nobody translates "".
        if re.search(r'=\s*""\s*[,)]?\s*$', line):
            continue
        found.add(i)
    return found, lines


def literals_in(path, allow):
    """
    Literal hits in one file that nobody has dispositioned.

    The whole file is searched rather than each line on its own. A Compose call almost
    never fits on one line, and reading line by line saw only the calls whose text happened
    to sit beside the opening bracket. Everything written the ordinary way, with the text
    under the call, was invisible: a screen of hardcoded labels could sit at zero and look
    finished. The hit is reported at the line the call opens on, which is the line an
    allowlist entry names.
    """
    rel = str(Path(path).relative_to(ROOT)).replace("\\", "/") if Path(path).is_absolute() else str(path).replace("\\", "/")
    found, lines = literal_lines(path)
    hits = [
        (i, lines[i - 1].strip())
        for i in sorted(found)
        if (rel, i) not in allow
    ]
    return rel, hits


def check_file(target):
    path = Path(target)
    if not path.is_absolute():
        path = ROOT / target
    if not path.exists():
        fail("no such file: " + str(target))
        return
    rel = str(path.relative_to(ROOT)).replace("\\", "/")
    strings_rel = str(STRINGS.relative_to(ROOT)).replace("\\", "/")
    allow_rel = str(ALLOWLIST.relative_to(ROOT)).replace("\\", "/")

    section("blast radius")
    # Only this file, strings.xml and the allowlist may differ from the last commit. The
    # allowlist is on the list because the loop says a literal is either extracted or
    # dispositioned: refusing the second half of that would leave no legal way to finish a
    # file that has one. Commit each file once it is green, so this check keeps meaning
    # something.
    _, out = git("diff", "--name-only")
    _, out2 = git("diff", "--cached", "--name-only")
    changed = sorted({c.strip() for c in (out + out2).splitlines() if c.strip()})
    stray = [c for c in changed if c not in (rel, strings_rel, allow_rel)]
    if stray:
        for s in stray:
            fail("changed a file this task did not ask for: " + s)
        print("   -> commit the files you already finished, then re-run")
    else:
        ok("only %s and strings.xml changed" % path.name)

    section("imports added by this change")
    # An import nothing uses is the cheapest sign of pattern matching over reading.
    _, diff = git("diff", "--", rel)
    body = read(path)
    bad_import = False
    for line in diff.splitlines():
        if not line.startswith("+import "):
            continue
        imported = line[len("+import "):].strip().rstrip(";")
        simple = imported.split(".")[-1]
        if simple == "*":
            continue
        uses = len(re.findall(r"\b%s\b" % re.escape(simple), body))
        if uses <= 1:
            fail("import added but never used: " + imported)
            bad_import = True
    if not bad_import:
        ok("every added import is used")

    section("code deleted")
    # Extraction replaces text. It does not remove behaviour.
    _, numstat = git("diff", "--numstat", "--", rel)
    added = deleted = 0
    for line in numstat.splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[0].isdigit() and parts[1].isdigit():
            added, deleted = int(parts[0]), int(parts[1])
    if deleted > added * 2 + 10:
        fail("deleted %d lines and added %d, read the whole diff before continuing"
             % (deleted, added))
    else:
        ok("diff is a replacement, not a rewrite (%d added, %d deleted)" % (added, deleted))

    allow = load_allowlist()

    section("residual literals")
    _, hits = literals_in(path, allow)
    if hits:
        fail("literals neither extracted nor dispositioned, %d of them:" % len(hits))
        for num, text in hits:
            print("      %d: %s" % (num, text[:110]))
        print("   -> extract each one, OR append a line to tools/literal-allowlist.txt:")
        print("      %s:<line>: <why this text never reaches a user>" % rel)
    else:
        ok("every literal extracted or dispositioned")

    section("stale allowlist entries")
    # An allowlist line that no longer matches anything is a reason nobody rechecked.
    # Asked of the same function that produced the hit list, so an entry that satisfies
    # one check cannot fail the other.
    found, _ = literal_lines(path)
    stale = False
    for (afile, anum), reason in sorted(allow.items()):
        if afile != rel:
            continue
        if anum not in found:
            fail("allowlist line no longer matches anything: %s:%d" % (afile, anum))
            stale = True
    if not stale:
        ok("allowlist entries all still apply")


# ---------------------------------------------------------------- progress

def check_progress():
    """Progress measured rather than claimed."""
    allow = load_allowlist()
    section("user visible literals still in the code")
    total = 0
    rows = []
    for f in kt_files():
        rel, hits = literals_in(f, allow)
        if hits:
            rows.append((rel.replace("app/src/main/java/com/hazel/android/", ""), len(hits)))
            total += len(hits)
    width = max([len(r[0]) for r in rows], default=10)
    for name, n in sorted(rows, key=lambda r: -r[1]):
        print("  %-*s %4d" % (width, name, n))
    print("\n  %-*s %4d" % (width, "TOTAL", total))
    print("\nThe file you just converted must read 0, and no other number may have moved.")


# ---------------------------------------------------------------- translations

# CLDR cardinal categories each language actually defines. Emitting one outside this
# set is dead weight Android never selects; omitting one leaves a count unhandled.
CLDR = {
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "bn": {"one", "other"},
    "de": {"one", "other"},
    "es": {"one", "many", "other"},
    "fa": {"one", "other"},
    "fr": {"one", "many", "other"},
    "hi": {"one", "other"},
    "in": {"other"},
    "it": {"one", "many", "other"},
    "iw": {"one", "two", "many", "other"},
    "ja": {"other"},
    "ko": {"other"},
    "pl": {"one", "few", "many", "other"},
    "pt": {"one", "many", "other"},
    "ru": {"one", "few", "many", "other"},
    "th": {"other"},
    "tr": {"one", "other"},
    "ur": {"one", "other"},
    "vi": {"other"},
    "zh": {"other"},
}


def check_translations():
    source = parse_source()
    expected = {n for n, (_, _, t) in source.items() if t}
    untranslatable = {n for n, (_, _, t) in source.items() if not t}

    folders = sorted(p for p in RES_DIR.glob("values-*") if (p / "strings.xml").exists())
    if not folders:
        section("translations")
        print("no values-* folders yet, nothing to check")
        return

    for folder in folders:
        code = folder.name[len("values-"):]
        lang = code.split("-")[0]
        section(folder.name)
        path = folder / "strings.xml"

        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            fail("%s is not well formed XML: %s" % (folder.name, e))
            continue

        got = {}
        for el in root:
            if el.tag not in ("string", "plurals"):
                continue
            if el.tag == "string":
                got[el.get("name")] = ("string", el.text or "")
            else:
                got[el.get("name")] = (
                    "plurals",
                    {i.get("quantity"): (i.text or "") for i in el.findall("item")},
                )

        missing = sorted(expected - set(got))
        extra = sorted(set(got) - expected)
        leaked = sorted(set(got) & untranslatable)

        if missing:
            fail("%s is missing %d keys: %s" % (folder.name, len(missing), ", ".join(missing[:12])))
        if extra:
            fail("%s invented keys that are not in the source: %s" % (folder.name, ", ".join(extra[:12])))
        if leaked:
            fail("%s translated keys marked translatable=false: %s" % (folder.name, ", ".join(leaked)))
        if not (missing or extra or leaked):
            ok("key set matches the source exactly, %d entries" % len(got))

        bad = False
        for name in sorted(expected & set(got)):
            skind, svalue, _ = source[name]
            gkind, gvalue = got[name]

            if skind != gkind:
                fail("%s: %s is a <%s> in the source but a <%s> here" % (folder.name, name, skind, gkind))
                bad = True
                continue

            src_texts = [svalue] if skind == "string" else list(svalue.values())
            got_texts = [gvalue] if gkind == "string" else list(gvalue.values())

            want = sorted(set(PLACEHOLDER.findall(" ".join(src_texts))))
            for t in got_texts:
                have = sorted(set(PLACEHOLDER.findall(t)))
                if want and have != want:
                    fail("%s: %s placeholders changed, source has %s and this has %s"
                         % (folder.name, name, want or "none", have or "none"))
                    bad = True
                    break

            want_markup = sorted(MARKUP.findall(" ".join(src_texts)))
            have_markup = sorted(MARKUP.findall(" ".join(got_texts)))
            if want_markup != have_markup:
                fail("%s: %s inline markup changed, source has %s and this has %s"
                     % (folder.name, name, want_markup or "none", have_markup or "none"))
                bad = True

            for t in got_texts:
                if re.search(r"(?<!\\)'", t):
                    fail("%s: %s has an unescaped apostrophe, write \\'" % (folder.name, name))
                    bad = True
                    break

            if gkind == "plurals":
                legal = CLDR.get(lang)
                cats = set(gvalue)
                if "other" not in cats:
                    fail("%s: plural %s has no 'other'" % (folder.name, name))
                    bad = True
                if legal:
                    illegal = sorted(cats - legal)
                    absent = sorted(legal - cats)
                    if illegal:
                        fail("%s: plural %s uses categories %s that %s does not define"
                             % (folder.name, name, illegal, lang))
                        bad = True
                    if absent:
                        fail("%s: plural %s is missing %s, which %s does define"
                             % (folder.name, name, absent, lang))
                        bad = True
                else:
                    print("   note: no CLDR table for '%s', plural categories unchecked" % lang)

        if not bad:
            ok("placeholders, markup, escaping and plural categories all match")


# ---------------------------------------------------------------- audit a commit

def _unescape(value):
    """An Android resource value, back to the plain text a Kotlin literal held."""
    out = value
    for a, b in (("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", '"'),
                 ("&apos;", "'"), ("\\'", "'"), ('\\"', '"'), ("\\n", "\n"),
                 ("\\u00b7", "·")):
        out = out.replace(a, b)
    return out


def check_audit(commit):
    """
    Every key a commit added must hold text that was already in the Kotlin file that
    commit converted. This is the one thing the rest of the gate cannot see: a key can
    be perfectly named, wired up and compiling, and still hold text the model reworded
    or invented. Extraction moves text. It does not write it.
    """
    code, out = git("show", "--name-only", "--format=", commit)
    if code != 0:
        fail("no such commit: " + commit)
        return
    files = [f.strip() for f in out.splitlines() if f.strip().endswith(".kt")]
    if not files:
        fail("commit %s changed no Kotlin file, nothing to audit" % commit)
        return

    # The source as it stood before the commit, with adjacent literal concatenation
    # collapsed so a sentence split over three lines still matches.
    before = ""
    for kt in files:
        _, src = git("show", "%s~1:%s" % (commit, kt))
        before += src + "\n"
    # Splitting an interpolated sentence into whole sentences is the correct fix, not a
    # rewording, so the before text is also read with each branch of a two way
    # interpolation chosen. Without this, doing the right thing looks like drift.
    branch = re.compile(r'\$\{if \([^)]*\) "([^"]*)" else "([^"]*)"\}')
    forms = [before, branch.sub(lambda m: m.group(1), before),
             branch.sub(lambda m: m.group(2), before)]
    joined = " ".join(re.sub(r"\s+", " ", re.sub(r'"\s*\+\s*"', "", f)) for f in forms)

    _, diff = git("show", commit, "--", str(STRINGS.relative_to(ROOT)).replace("\\", "/"))
    added_strings = re.findall(r'^\+\s*<string name="([a-z_0-9]+)"[^>]*>(.*?)</string>',
                               diff, re.M)
    added_items = re.findall(r'^\+\s*<item quantity="[a-z]+">(.*?)</item>', diff, re.M)

    section("commit %s, %s" % (commit[:9], ", ".join(Path(f).name for f in files)))
    print("keys added: %d strings, %d plural items" % (len(added_strings), len(added_items)))

    verbatim, placeholders, absent = 0, [], []
    for name, value in added_strings:
        text = _unescape(value)
        if re.search(r"%\d+\$|%[sdf]", text):
            placeholders.append((name, value))
            continue
        probe = re.sub(r"\s+", " ", text).strip()
        if probe and probe in joined:
            verbatim += 1
        else:
            absent.append((name, value))

    print("verbatim from the source: %d of %d" % (verbatim, len(added_strings) - len(placeholders)))

    if absent:
        fail("%d keys hold text that is not in the file this commit converted:" % len(absent))
        for n, v in absent:
            print("      %-40s %r" % (n, v))
        print("   -> either the text was reworded, or it was taken from somewhere else.")
        print("      Extraction copies character for character.")
    else:
        ok("every plain key was copied from the source")

    if placeholders:
        section("needs your eyes, placeholders cannot be matched verbatim")
        for n, v in placeholders:
            print("   %-40s %s" % (n, v))
        print("\n   Check each against the concatenation it replaced. The wording either side")
        print("   of the placeholder must be unchanged, and the value in the slot must be a")
        print("   runtime value: a filename, a size, a version, a hostname.")
        print("   Trace the argument back to its call site before you accept it. If what")
        print("   arrives in the slot is itself a stringResource, the sentence is being")
        print("   assembled from translated words and needs one key per whole sentence.")


# ---------------------------------------------------------------- build

def lint_report():
    """
    Where the text report actually landed, or None.

    The location moved: it used to sit under app/build/reports, and newer plugin versions
    write it into the intermediates tree instead. Looking in one place meant the lint step
    reported "no report produced" and passed without checking anything, which is worse than
    failing. Both are tried, newest first, and anything matching the name is accepted.
    """
    candidates = [
        ROOT / "app/build/reports/lint-results-debug.txt",
        ROOT / "app/build/intermediates/lint_intermediate_text_report"
             / "debug/lintReportDebug/lint-results-debug.txt",
    ]
    found = [p for p in candidates if p.exists()]
    if not found:
        found = sorted((ROOT / "app/build").rglob("lint-results-debug.txt"))
    if not found:
        return None
    return max(found, key=lambda p: p.stat().st_mtime)


def check_build():
    section("assembleDebug")
    code, out = gradle("assembleDebug")
    if code == 0:
        ok("compiles")
    else:
        fail("assembleDebug")
        print(out[-4000:])

    section("unit tests")
    code, out = gradle("testDebugUnitTest")
    if code == 0:
        ok("unit tests pass")
    else:
        fail("unit tests")
        print(out[-4000:])

    section("android lint, resource rules only")
    gradle("lintDebug")
    report = lint_report()
    if report is None:
        print("no lint report produced, skipping")
        return
    rules = ("MissingTranslation", "ExtraTranslation", "StringFormat",
             "StringFormatMatches", "ImpliedQuantity", "MissingQuantity")
    hits = [l for l in read(report).splitlines() if any(r in l for r in rules)]
    if hits:
        fail("lint flagged resource problems:")
        for h in hits[:20]:
            print("      " + h.strip())
    else:
        ok("no resource lint findings")


# ---------------------------------------------------------------- entry

USAGE = """usage:
  python tools/check.py resources
  python tools/check.py file <path/to/File.kt>
  python tools/check.py progress
  python tools/check.py translations
  python tools/check.py build
  python tools/check.py all <path/to/File.kt>
  python tools/check.py audit <commit> [<commit> ...]
"""


def main(argv):
    if len(argv) < 2:
        print(USAGE)
        return 2
    cmd = argv[1]

    if cmd == "resources":
        check_resources()
    elif cmd == "progress":
        check_progress()
        return 0
    elif cmd == "translations":
        check_translations()
    elif cmd == "build":
        check_build()
    elif cmd == "audit":
        if len(argv) < 3:
            print(USAGE)
            return 2
        for c in argv[2:]:
            check_audit(c)
    elif cmd == "file":
        if len(argv) < 3:
            print(USAGE)
            return 2
        check_file(argv[2])
    elif cmd == "all":
        if len(argv) < 3:
            print(USAGE)
            return 2
        check_resources()
        check_file(argv[2])
        check_build()
    else:
        print(USAGE)
        return 2

    print("")
    if _failed:
        print("NOT DONE. Fix every FAIL above before touching another file.")
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
