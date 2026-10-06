#!/usr/bin/env python3
"""
Test Harness: downloads asked for back to back, from Instant, from the app, or both.

Verifies:
1. Every way a link reaches the queue goes through startBatch, which adds it under the
   queue's lock and either joins the run in flight or starts one.
2. A run closes in one step under that same lock: it stops the service and reports the run
   over only when nothing new is waiting, and lets go only after that. A link that arrives
   while a run is closing is handed to a new run instead of being stopped under it.
   A model replays a new link arriving at every point of a run's close, for the old way
   (let go first, close after) and the new one, and checks the service is running and the
   new download shown as running whenever something is still owed.
3. Instant reads: the same link shared twice while it is being read is one read; different
   links each read under their own process id, so back-to-back shares do not refuse each
   other; a failed read lets go of the service only when nothing else needs it.
4. A link shared again after its read is queued again: that is intended, and nothing here
   removes duplicates.

Run:
    python tools/tests/test_queue_back_to_back.py
"""

import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VM = REPO_ROOT / "app/src/main/java/com/hazel/android/download/DownloadViewModel.kt"

PASS_COUNT = 0
FAIL_COUNT = 0


def check(name: str, got, expected):
    global PASS_COUNT, FAIL_COUNT
    if got == expected:
        PASS_COUNT += 1
        print(f"  PASS  {name}")
    else:
        FAIL_COUNT += 1
        print(f"  FAIL  {name}")
        print(f"        Expected: {expected!r}")
        print(f"        Got:      {got!r}")


def check_true(name: str, condition: bool):
    check(name, bool(condition), True)


def block(text: str, start: str, length: int) -> str:
    at = text.find(start)
    return text[at:at + length] if at >= 0 else ""


# ===========================================================================
# 1-2. A model of the run and of startBatch
# ===========================================================================

class Pipeline:
    def __init__(self):
        self.queue = []
        self.owner = None
        self.service = False
        self.downloading = False
        self.runs = []

    # startBatch: add and check under the lock, then start a run if none holds it.
    def start_batch_locked(self, link):
        self.queue.append(link)
        return self.owner is not None

    def start_batch_rest(self, already_running):
        if already_running:
            return
        self.service = True
        self.downloading = True
        self.run_queue()

    def run_queue(self):
        if self.owner is not None:
            return
        token = object()
        self.owner = token
        self.service = True
        self.downloading = True
        self.runs.append(token)

    def take_next(self):
        return self.queue.pop(0) if self.queue else None


def close_steps(p, token, new_way):
    """The steps a run takes once it finds the queue empty, as callables."""
    if new_way:
        def close():
            more = bool(p.queue)
            if not more:
                p.service = False
                p.downloading = False
            if p.owner is token:
                p.owner = None
            if more:
                p.run_queue()
        return [close]

    def let_go():
        if p.owner is token:
            p.owner = None

    def stop_service():
        p.service = False

    def finish():
        p.downloading = False

    return [let_go, stop_service, finish]


def replay(new_way, arrive_at):
    """A run finishing its last link while a new link arrives before step [arrive_at]."""
    p = Pipeline()
    p.run_queue()
    token = p.owner
    p.take_next()  # the last link has just finished; the queue is empty
    steps = close_steps(p, token, new_way)
    arrived = False
    for i, step in enumerate(steps):
        if i == arrive_at:
            p.start_batch_rest(p.start_batch_locked("new"))
            arrived = True
        step()
    if not arrived:
        p.start_batch_rest(p.start_batch_locked("new"))
    owed = bool(p.queue) or p.owner is not None
    return p, owed


def test_model():
    print("\n--- Suite 1: A link arriving while a run closes ---")
    for new_way in (False, True):
        name = "new" if new_way else "old"
        steps = 1 if new_way else 3
        broken = []
        for arrive_at in range(steps + 1):
            p, owed = replay(new_way, arrive_at)
            fine = (not owed) or (p.service and p.downloading and p.owner is not None)
            if not fine:
                broken.append(arrive_at)
        if new_way:
            check("New close: the service runs and the download shows at every arrival point", broken, [])
        else:
            check_true("Old close (let go first) stopped a run under a new link, as found", broken)

    # The run's last save racing a link added and written down meanwhile. Writes take
    # turns; the old save wrote a copy taken before its turn, the new one reads at its turn.
    def save_race(read_at_write):
        memory, stored = ["paused"], ["paused"]
        copy = list(memory)                 # the old save's copy, taken first
        memory.append("new")                 # startBatch adds it in memory...
        stored = stored + ["new"]            # ...and its write takes its turn first
        stored = list(memory) if read_at_write else copy
        return "new" in stored
    check("Old last save wrote over a link written down meanwhile, as found", save_race(False), False)
    check("New last save keeps it", save_race(True), True)

    p = Pipeline()
    p.run_queue()
    running = p.start_batch_locked("second")
    p.start_batch_rest(running)
    check("A link added during a run joins it rather than starting another", (running, len(p.runs)), (True, 1))
    check("Shared again, a link is queued again (intended)",
          (p.start_batch_locked("second"), p.queue.count("second")), (True, 2))


# ===========================================================================
# 2. The code holds to the model
# ===========================================================================

def test_code():
    print("\n--- Suite 2: DownloadViewModel ---")
    vm = VM.read_text(encoding="utf-8")

    start = block(vm, "    fun startBatch(", 3500)
    check_true("startBatch adds and checks the run under the queue's lock",
               "val alreadyRunning = synchronized(queue) {\n            queue.addAll(queued)\n            runOwner != null\n        }" in start)
    check_true("Joining a run shows the new links as waiting",
               "batch = s.batch + plans.map {" in start)
    check_true("startBatch adds no duplicate check (intended)",
               "queue.none" not in start and "distinctBy" not in start)

    # The whole of runQueue, up to the function after it, rather than a fixed length that a
    # longer run loop outgrows.
    run_at = vm.find("    private fun runQueue(")
    run = vm[run_at:vm.find("    private fun holdForResume(", run_at)] if run_at >= 0 else ""
    take = block(run, "val next = synchronized(queue) {", 220)
    check_true("Taking the next link does not let the run go", take and "runOwner" not in take)
    close = block(run, "val more = synchronized(queue) {", 500)
    check_true("The run closes under the queue's lock", close)
    check_true("It stops and reports only when nothing is waiting",
               "val more = !isBatchCancelled && queue.any { !it.paused }" in close
               and close.find("if (!more) {") < close.find("DownloadService.stop(app)") < close.find("finishBatch(app)"))
    check_true("It lets go only after closing",
               close.find("finishBatch(app)") < close.find("if (runOwner === token) runOwner = null"))
    check_true("A link that arrived while closing gets a new run",
               "if (more) runQueue(app, resumed = false)" in run)
    check_true("The run lets go if it ends any other way",
               "job.invokeOnCompletion {\n            synchronized(queue) { if (runOwner === token) runOwner = null }" in vm)
    check("Nothing else lets the run go", vm.count("runOwner = null"), 2)
    check_true("The run's last save reads the queue when it writes, not before",
               "DownloadQueueRepository.saveCurrent(app) {" in run
               and "DownloadQueueRepository.save(app, remaining)" not in run)
    check_true("The save comes before the run lets go",
               run.find("DownloadQueueRepository.saveCurrent(app)") < run.find("val more = synchronized(queue) {"))
    repo = (REPO_ROOT / "app/src/main/java/com/hazel/android/data/DownloadQueueRepository.kt").read_text(encoding="utf-8")
    save = block(repo, "suspend fun saveCurrent(", 400)
    check_true("saveCurrent asks for the queue inside the write",
               save.find("context.dataStore.edit { prefs ->") < save.find("val items = current()"))
    check_true("Adding a link written down meanwhile merges rather than replaces",
               "val merged = existing + items.filterNot { it.url in known }" in repo)


# ===========================================================================
# 3. Instant reads
# ===========================================================================

def test_instant():
    print("\n--- Suite 3: Instant shares back to back ---")
    vm = VM.read_text(encoding="utf-8")
    instant = block(vm, "    fun instantDownload(", 5000)
    check_true("The same link shared twice while read is one read",
               "val first = synchronized(instantReads) { instantReads.add(url) }" in instant
               and "if (!first) return" in instant)
    check_true("Each link reads under its own process id",
               '"${MediaProbe.PROBE_PROCESS_ID}_instant_${LinkKey.digest(url)}"' in instant)
    check_true("Instant reads apart from the home screen's read", "fetchJob" not in instant)
    check_true("Instant downloads through startBatch", "startBatch(app, plans, options, saveDirs = dirs)" in instant)
    check_true("A failed read lets the service go only when nothing else needs it",
               "if (failure != null && idle && synchronized(queue) { runOwner == null && queue.isEmpty() })" in instant)
    check_true("Its read is let go of whatever happens",
               "instantReads.remove(url)" in instant and "} finally {" in instant)


def main():
    print("=" * 70)
    print("  Hazel Back-to-Back Downloads Test Harness")
    print("=" * 70)

    test_model()
    test_code()
    test_instant()

    print("\n" + "=" * 70)
    print(f"  TOTAL CHECKS: {PASS_COUNT + FAIL_COUNT}")
    print(f"  PASSED:       {PASS_COUNT}")
    print(f"  FAILED:       {FAIL_COUNT}")
    print("=" * 70)

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
