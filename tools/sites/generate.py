"""
Writes the link rules yt-dlp uses to tell which sites it can read, for Web search to
keep only the results Hazel can download.

Every yt-dlp extractor carries a regular expression, _VALID_URL, that a link must match
for that extractor to take it. This reads them from a yt-dlp source tree, rewrites each
from Python's regex dialect into Java's, and writes one per line to
app/src/main/assets/ytdlp_sites.txt as `<extractor>\t<pattern>`.

Left out, so that a match means a real site yt-dlp knows:
  - the generic extractor and any other rule that would take an arbitrary page;
  - extractors yt-dlp marks as not working.

A conditional group, which Java's regex has no form of, is loosened to allow any of its
branches; the part of the rule naming the site is kept exact.

Run it whenever the bundled yt-dlp is updated:

    python tools/sites/generate.py <path to a yt-dlp source tree>

With no path it uses the installed yt_dlp package.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "ytdlp_sites.txt")

# Pages no single site's rule should take. A rule that matches any of these takes
# arbitrary links, so it says nothing about whether yt-dlp knows the site.
CATCH_ALL_PROBES = [
    "https://example.com/watch/abc123.html",
    "https://some-random-site.org/video/12345",
    "http://a.b/c",
]


class Unsupported(Exception):
    pass


def strip_verbose(p):
    """Drops the whitespace and comments a (?x) pattern ignores, outside character classes."""
    out, i, in_class = [], 0, False
    while i < len(p):
        c = p[i]
        if c == "\\" and i + 1 < len(p):
            out.append(p[i:i + 2])
            i += 2
            continue
        if in_class:
            if c == "]":
                in_class = False
            out.append(c)
        elif c == "[":
            in_class = True
            out.append(c)
            # A ] straight after [ or [^ is a literal, not the end.
            if p[i + 1:i + 2] == "^":
                out.append("^")
                i += 1
            if p[i + 1:i + 2] == "]":
                out.append("]")
                i += 1
        elif c == "#":
            while i < len(p) and p[i] != "\n":
                i += 1
            continue
        elif c.isspace():
            pass
        else:
            out.append(c)
        i += 1
    return "".join(out)


QUANT = re.compile(r"\{(\d*)(,?)(\d*)\}")


def to_java(p):
    """Rewrites one Python pattern into the Java dialect, or raises Unsupported."""
    flags = re.match(r"^\(\?([aiLmsux]+)\)", p)
    if flags and "x" in flags.group(1):
        rest = flags.group(1).replace("x", "")
        p = ("(?%s)" % rest if rest else "") + strip_verbose(p[flags.end():])
    elif "(?x)" in p:
        p = strip_verbose(p.replace("(?x)", ""))
    elif "(?x:" in p:
        # Verbose for the group it opens, which in yt-dlp's rules is the whole rule.
        p = strip_verbose(p.replace("(?x:", "(?:"))

    out, names, group, i, in_class = [], {}, 0, 0, False
    while i < len(p):
        c = p[i]
        if c == "\\" and i + 1 < len(p):
            nxt = p[i + 1]
            out.append("\\z" if nxt == "Z" else p[i:i + 2])
            i += 2
            continue
        if in_class:
            if c == "]":
                in_class = False
                out.append(c)
            elif c in "[&":
                # A literal in Python's classes; a nested class or an intersection in Java's.
                out.append("\\" + c)
            else:
                out.append(c)
            i += 1
            continue
        if c == "[":
            in_class = True
            out.append(c)
            if p[i + 1:i + 2] == "^":
                out.append("^")
                i += 1
            if p[i + 1:i + 2] == "]":
                out.append("\\]")
                i += 1
            i += 1
            continue
        if c == "(":
            conditional = re.match(r"\(\?\((\w+)\)", p[i:])
            if conditional:
                # Java has no "(?(group)then|else)". Every branch is allowed instead, and
                # nothing at all: a looser rule, but only past the site's own address,
                # which is what decides the site.
                out.append("(?:|")
                i += conditional.end()
                continue
            named = re.match(r"\(\?P<(\w+)>", p[i:])
            backref = re.match(r"\(\?P=(\w+)\)", p[i:])
            if named:
                # Java's group names take no underscores, so groups become numbered.
                group += 1
                names[named.group(1)] = group
                out.append("(")
                i += named.end()
                continue
            if backref:
                if backref.group(1) not in names:
                    raise Unsupported("back reference before its group")
                out.append("(?:\\%d)" % names[backref.group(1)])
                i += backref.end()
                continue
            if p[i + 1:i + 2] != "?":
                group += 1
            out.append(c)
            i += 1
            continue
        if c == "{":
            q = QUANT.match(p, i)
            if q and (q.group(1) or q.group(3)) and out:
                low, comma, high = q.group(1) or "0", q.group(2), q.group(3)
                out.append("{%s%s%s}" % (low, comma, high))
                i = q.end()
                continue
            out.append("\\{")
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def main():
    if len(sys.argv) > 1:
        sys.path.insert(0, os.path.abspath(sys.argv[1]))
    from yt_dlp.extractor import gen_extractor_classes
    from yt_dlp.version import __version__

    lines, skipped = [], {"generic": 0, "not working": 0, "catch-all": 0, "unsupported": 0}
    for ie in gen_extractor_classes():
        name = ie.IE_NAME
        if name == "generic":
            skipped["generic"] += 1
            continue
        if not ie._WORKING:
            skipped["not working"] += 1
            continue
        valid = ie._VALID_URL
        if not valid:
            continue
        for pattern in (valid if isinstance(valid, (list, tuple)) else [valid]):
            compiled = re.compile(pattern)
            if any(compiled.match(url) for url in CATCH_ALL_PROBES):
                skipped["catch-all"] += 1
                continue
            try:
                java = to_java(pattern)
            except Unsupported:
                skipped["unsupported"] += 1
                continue
            if "\n" in java or "\t" in java:
                skipped["unsupported"] += 1
                continue
            lines.append("%s\t%s" % (name, java))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("# yt-dlp %s link rules, written by tools/sites/generate.py. Do not edit.\n" % __version__)
        f.write("\n".join(lines) + "\n")
    print("yt-dlp %s: %d rules written to %s" % (__version__, len(lines), os.path.relpath(OUT, ROOT)))
    print("left out: " + ", ".join("%d %s" % (n, k) for k, n in skipped.items()))


if __name__ == "__main__":
    main()
