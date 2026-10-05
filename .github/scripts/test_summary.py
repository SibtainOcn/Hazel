#!/usr/bin/env python3
"""Turn the JUnit XML a Gradle test run leaves behind into a run summary.

Every number here is read from the reports the run just produced. Nothing is written
down in advance, so adding a test file changes the totals on the next run with no edit
to this script or to the workflow that calls it.

With --check-run, it also writes the body of a GitHub check run to the given file: the
counts as its title, this summary as its text, and each failing test as an annotation. The
workflow posts that file, so the counts show on the pull request's checks list.

Usage: test_summary.py [--harness NAME=LOG ...] [--check-run FILE] [results-dir ...]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DEFAULT_DIRS = ["app/build/test-results"]
TEST_SOURCES = Path("app/src/test")

# The harnesses end with a line such as "Results: 52/52 passed, 0 failed".
HARNESS_COUNT = re.compile(r"(\d+)/(\d+)\s+(?:tests\s+)?passed", re.IGNORECASE)

# GitHub takes at most 50 annotations in one request.
MAX_ANNOTATIONS = 50


class Suite:
    def __init__(self, name: str) -> None:
        self.name = name
        self.tests = 0
        self.failures = 0
        self.errors = 0
        self.skipped = 0
        self.time = 0.0

    @property
    def bad(self) -> int:
        return self.failures + self.errors

    @property
    def passed(self) -> int:
        return self.tests - self.bad - self.skipped


def collect(roots: list[str]) -> tuple[list[Suite], list[tuple[str, str, str]]]:
    suites: list[Suite] = []
    failures: list[tuple[str, str, str]] = []

    for root in roots:
        for path in sorted(Path(root).rglob("TEST-*.xml")):
            try:
                tree = ET.parse(path)
            except ET.ParseError:
                continue

            node = tree.getroot()
            suite = Suite(node.get("name", path.stem))
            suite.tests = int(node.get("tests", 0))
            suite.failures = int(node.get("failures", 0))
            suite.errors = int(node.get("errors", 0))
            suite.skipped = int(node.get("skipped", 0))
            suite.time = float(node.get("time", 0.0) or 0.0)
            suites.append(suite)

            for case in node.iter("testcase"):
                for problem in list(case.findall("failure")) + list(case.findall("error")):
                    failures.append(
                        (
                            case.get("classname", suite.name),
                            case.get("name", "?"),
                            (problem.get("message") or problem.text or "").strip(),
                        )
                    )

    return suites, failures


def short_name(fqcn: str) -> str:
    return fqcn.rsplit(".", 1)[-1]


def render(suites: list[Suite], failures: list[tuple[str, str, str]]) -> str:
    total = sum(s.tests for s in suites)
    bad = sum(s.bad for s in suites)
    skipped = sum(s.skipped for s in suites)
    passed = total - bad - skipped
    seconds = sum(s.time for s in suites)

    if not suites:
        return "## Unit tests\n\nNo test reports were produced.\n"

    heading = "All green" if bad == 0 else f"{bad} failing"
    lines = [
        f"## Unit tests: {heading}",
        "",
        f"**{total}** tests in **{len(suites)}** suites, finished in **{seconds:.1f}s**",
        "",
        "| Passed | Failed | Skipped | Total |",
        "|---:|---:|---:|---:|",
        f"| {passed} | {bad} | {skipped} | {total} |",
        "",
        "<details><summary>Per suite</summary>",
        "",
        "| Suite | Tests | Passed | Failed | Skipped | Time |",
        "|---|---:|---:|---:|---:|---:|",
    ]

    for suite in sorted(suites, key=lambda s: s.name):
        lines.append(
            f"| `{short_name(suite.name)}` | {suite.tests} | {suite.passed} | "
            f"{suite.bad} | {suite.skipped} | {suite.time:.2f}s |"
        )

    lines += ["", "</details>", ""]

    if failures:
        lines += ["### What failed", ""]
        for classname, name, message in failures:
            first_line = message.splitlines()[0] if message else "no message"
            lines.append(f"- **{short_name(classname)}** &rsaquo; {name}")
            lines.append(f"  <br>`{first_line[:300]}`")
        lines.append("")

    return "\n".join(lines) + "\n"


def read_harness(spec: str) -> tuple[str, int, int] | None:
    """Reads "NAME=LOG" into (name, passed, total), or None when the log has no count."""
    name, _, log = spec.partition("=")
    try:
        text = Path(log).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    matches = HARNESS_COUNT.findall(text)
    if not matches:
        return None
    passed, total = matches[-1]
    return name.strip(), int(passed), int(total)


def source_path(classname: str) -> str | None:
    """Finds the test file a class lives in, for an annotation. None when it cannot."""
    outer = classname.split("$", 1)[0]
    relative = outer.replace(".", "/")
    for ext in (".kt", ".java"):
        for lang in ("java", "kotlin"):
            candidate = TEST_SOURCES / lang / (relative + ext)
            if candidate.exists():
                return candidate.as_posix()
    return None


def check_run(
    suites: list[Suite],
    failures: list[tuple[str, str, str]],
    harnesses: list[tuple[str, int, int]],
    summary: str,
) -> dict:
    total = sum(s.tests for s in suites)
    bad = sum(s.bad for s in suites)
    skipped = sum(s.skipped for s in suites)
    passed = total - bad - skipped
    seconds = sum(s.time for s in suites)

    harness_bad = any(p < t for _, p, t in harnesses)
    green = bool(suites) and bad == 0 and not harness_bad

    if suites:
        title = f"{passed}/{total - skipped} passed"
        if bad:
            title += f", {bad} failed"
        if skipped:
            title += f" · {skipped} skipped"
        title += f" · {seconds:.0f}s"
    else:
        title = "No test reports were produced"
    for name, p, t in harnesses:
        title += f" · {name.split()[0]} {p}/{t}"

    text = summary
    if harnesses:
        text += "\n## Harnesses\n\n| Harness | Passed | Total |\n|---|---:|---:|\n"
        text += "".join(f"| {n} | {p} | {t} |\n" for n, p, t in harnesses)

    annotations = []
    for classname, name, message in failures[:MAX_ANNOTATIONS]:
        path = source_path(classname)
        if path is None:
            continue
        annotations.append(
            {
                "path": path,
                "start_line": 1,
                "end_line": 1,
                "annotation_level": "failure",
                "title": f"{short_name(classname)} > {name}"[:255],
                "message": (message or "no message")[:4000],
            }
        )

    output = {"title": title[:255], "summary": text[:65000]}
    if annotations:
        output["annotations"] = annotations

    return {
        "name": "Test results",
        "head_sha": os.environ.get("HEAD_SHA") or os.environ.get("GITHUB_SHA", ""),
        "status": "completed",
        "conclusion": "success" if green else "failure",
        "output": output,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--harness", action="append", default=[], metavar="NAME=LOG")
    parser.add_argument("--check-run", metavar="FILE")
    parser.add_argument("roots", nargs="*")
    args = parser.parse_args()

    roots = args.roots or DEFAULT_DIRS
    suites, failures = collect(roots)
    summary = render(suites, failures)
    harnesses = [h for h in map(read_harness, args.harness) if h is not None]

    print(summary)

    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as handle:
            handle.write(summary)

    output_file = os.environ.get("GITHUB_OUTPUT")
    if output_file:
        total = sum(s.tests for s in suites)
        bad = sum(s.failures + s.errors for s in suites)
        with open(output_file, "a", encoding="utf-8") as handle:
            handle.write(f"total={total}\n")
            handle.write(f"failed={bad}\n")

    if args.check_run:
        body = check_run(suites, failures, harnesses, summary)
        Path(args.check_run).write_text(json.dumps(body), encoding="utf-8")
        print(f"Check run: {body['output']['title']}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
