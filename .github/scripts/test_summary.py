#!/usr/bin/env python3
"""Turn the JUnit XML a Gradle test run leaves behind into a run summary.

Every number here is read from the reports the run just produced. Nothing is written
down in advance, so adding a test file changes the totals on the next run with no edit
to this script or to the workflow that calls it.

Usage: test_summary.py [results-dir ...]
"""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DEFAULT_DIRS = ["app/build/test-results"]


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


def main() -> int:
    roots = sys.argv[1:] or DEFAULT_DIRS
    suites, failures = collect(roots)
    summary = render(suites, failures)

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

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
