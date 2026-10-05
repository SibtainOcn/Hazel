#!/usr/bin/env python3
"""
Test Harness: the queue's run state survives the screen, and a run ends with its counts.

Verifies:
1. Clearing the home screen, or a link shared in while a run is going (a failed card
   reopened from the queue screen goes through the share sheet), keeps the run's record:
   the Running tab and the notification keep agreeing. Nothing resets the state to blank.
2. The run moves only `active`, never `info`. `info` is the result the open sheet is on;
   moved by the run, a sheet opened on a search result became whichever playlist item had
   just started, and its Download queued that item again instead of the result.
3. The run says it is downloading for every item and gives the item a card if the list has
   none, so the Running tab holds whatever is in hand.
4. The home sheet's Download names its own link, and the home list follows `active`.
5. Cancel and pause paths ask whether a run owns the queue, not only the screen's flag.
6. A run of several links that did not all save ends with one summary notification giving
   the saved, failed and cancelled counts, opening the queue; single links and fully saved
   runs keep their own notifications. The strings exist in every language.
7. Nothing earlier is undone: startBatch still adds without a duplicate check.
8. Pause all holds the whole run until Resume, kept across a restart; a card's own Pause
   pauses that link and lets the rest go on. A paused card shows a pause glyph and nothing on
   it moves. Cancelling a held queue reports every count.

Run:
    python tools/tests/test_queue_run_state.py
"""

import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SRC = REPO_ROOT / "app/src/main/java/com/hazel/android"
VM = SRC / "download/DownloadViewModel.kt"
NOTIFY = SRC / "download/DownloadNotificationHelper.kt"
HOME = SRC / "ui/screens/download/DownloadScreen.kt"
QUEUE = SRC / "ui/screens/queue/QueueScreen.kt"
NAV = SRC / "ui/navigation/AppNavigation.kt"
RES = REPO_ROOT / "app/src/main/res"

PASS_COUNT = 0
FAIL_COUNT = 0


def check(name, got, expected):
    global PASS_COUNT, FAIL_COUNT
    if got == expected:
        PASS_COUNT += 1
        print(f"  PASS  {name}")
    else:
        FAIL_COUNT += 1
        print(f"  FAIL  {name}")
        print(f"        Expected: {expected!r}")
        print(f"        Got:      {got!r}")


def check_true(name, condition):
    check(name, bool(condition), True)


def block(text, start, length):
    at = text.find(start)
    return text[at:at + length] if at >= 0 else ""


def function(text, start):
    """The body of the function starting at [start], by brace matching."""
    at = text.find(start)
    if at < 0:
        return ""
    open_at = text.find("{", at)
    depth = 0
    for i in range(open_at, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[at:i + 1]
    return text[at:]


# ===========================================================================
# 0. A model of the reported runs
# ===========================================================================

class Model:
    """The screen state, the run and the sheet, as the old and the new code treat them."""

    def __init__(self, new_way):
        self.new_way = new_way
        self.state = {"isDownloading": False, "active": None, "info": None, "batch": {}}
        self.queue = []
        self.owner = False
        self.disk = []

    def start_run(self, links):
        self.queue += links
        self.disk += links
        for link in links:
            self.state["batch"][link] = "QUEUED"
        self.owner = True
        self.state["isDownloading"] = True

    def next_item(self):
        link = self.queue.pop(0)
        if self.new_way:
            self.state["isDownloading"] = True
            self.state["batch"].setdefault(link, "QUEUED")
        else:
            self.state["info"] = link
        self.state["active"] = link
        if link in self.state["batch"]:
            self.state["batch"][link] = "DOWNLOADING"
        return link

    def settle(self, link, outcome):
        if link in self.state["batch"]:
            self.state["batch"][link] = outcome
        self.disk.remove(link)

    def clear_screen(self):
        in_hand = self.owner or any(v in ("DOWNLOADING", "PAUSED", "QUEUED") for v in self.state["batch"].values())
        if self.new_way and in_hand:
            self.state["info"] = None
        else:
            self.state = {"isDownloading": False, "active": None, "info": None, "batch": {}}

    def running_tab(self):
        s = self.state
        return [s["active"]] if s["active"] and s["isDownloading"] else []

    def open_sheet(self, link):
        self.state["info"] = link

    def press_download(self):
        """The home sheet's Download, which queues what the sheet is on."""
        target = self.sheet_link if self.new_way else self.state["info"]
        if target not in self.disk:
            self.disk.append(target)
        self.queue.append(target)
        return target


def test_model():
    print("\n--- Suite 0: the reported runs, old and new ---")
    for new_way in (False, True):
        label = "New" if new_way else "Old"
        m = Model(new_way)
        playlist = [f"p{i}" for i in range(1, 8)]
        m.start_run(playlist)
        m.settle(m.next_item(), "DONE")
        m.settle(m.next_item(), "FAILED")
        # A failed card reopened from the queue shares its link in, which cleared the screen.
        m.clear_screen()
        current = m.next_item()
        tab = m.running_tab()
        if new_way:
            check(f"{label}: the Running tab shows the item after a failure and a clear", tab, [current])
        else:
            check(f"{label}: the Running tab went empty mid run, as reported", tab, [])

        # A search result's sheet is opened, the run moves on, Download is pressed.
        m.open_sheet("search-result")
        m.sheet_link = "search-result"
        m.settle(current, "DONE")
        m.next_item()
        queued = m.press_download()
        waiting = [link for link in m.disk if link != m.state["active"]]
        if new_way:
            check(f"{label}: the search result is queued and shows as waiting",
                  (queued, "search-result" in waiting), ("search-result", True))
        else:
            check(f"{label}: the playlist item was queued instead and nothing new showed, as reported",
                  (queued != "search-result", "search-result" in waiting), (True, False))


# ===========================================================================
# 1-5. The code holds to the model
# ===========================================================================

def test_view_model():
    print("\n--- Suite 1: DownloadViewModel ---")
    vm = VM.read_text(encoding="utf-8")

    check("Nothing resets the state to blank directly", vm.count("_state.value = DownloadState()"), 0)
    for name in ("fun clearUrl()", "fun clearResults()", "fun resetState()"):
        check_true(f"{name} keeps the run", "_state.update { it.keepingRun() }" in function(vm, name))
    check_true("fetchShare clears through clearResults",
               "clearResults()" in function(vm, "fun fetchShare("))

    keep = function(vm, "internal fun DownloadState.keepingRun(running: Boolean)")
    check_true("keepingRun asks whether a run owns the queue", "running ||" in keep)
    check_true("keepingRun keeps a paused, waiting or downloading list",
               all(s in keep for s in ("BatchState.DOWNLOADING", "BatchState.PAUSED", "BatchState.QUEUED")))
    for field in ("isDownloading = isDownloading", "active = active", "progress = progress",
                  "totalBytes = totalBytes", "isProcessing = isProcessing",
                  "processingSteps = processingSteps", "processingStep = processingStep",
                  "waitingForWifi = waitingForWifi", "saveFallback = saveFallback", "batch = batch"):
        check_true(f"keepingRun carries {field.split(' =')[0]}", field in keep)
    check_true("keepingRun drops the screen's own fields",
               not re.search(r"\b(results|info|searchQuery|errorLog|url)\s*=", keep))
    check_true("With nothing in hand it is a blank state", "if (!inHand) return DownloadState()" in keep)
    check_true("The member asks the queue's lock",
               "private fun isRunning(): Boolean = synchronized(queue) { runOwner != null }" in vm)

    run = function(vm, "private fun runQueue(")
    item = block(run, "val opening = if (progressFloor > 0f)", 1200)
    check_true("The run never moves the sheet's info", not re.search(r"\binfo\s*=\s*plan\.info", run))
    check_true("The run moves active", "active = plan.info," in item)
    check_true("The run says it is downloading for every item", "isDownloading = true," in item)
    check_true("The run gives its item a card", "markBatch(plan.info.url, BatchState.DOWNLOADING, title = plan.title)" in run)

    mark = block(vm, "internal fun List<BatchItem>.marking(", 500)
    check_true("marking adds only with a title and only when unknown",
               "if (title != null && none { it.url == url })" in mark)
    check_true("markBatch still takes a settled link off the written-down queue",
               "DownloadQueueRepository.remove(HazelApp.instance, url)" in function(vm, "private fun markBatch("))

    check_true("Cancelling the item in hand asks the run too",
               "(_state.value.isDownloading || isRunning()) && activeInfo?.url == url" in function(vm, "fun cancelItem("))
    check_true("Cancel all discards only a held download when no run owns the queue",
               "if (!_state.value.isDownloading && !isRunning()) {\n            discardHeldDownload()" in vm)

    start = function(vm, "fun startBatch(")
    check_true("startBatch is unchanged: no duplicate check (intended)",
               "queue.none" not in start and "distinctBy" not in start)
    check("Nothing new lets the run go", vm.count("runOwner = null"), 2)


def test_summary():
    print("\n--- Suite 2: the end of a run ---")
    vm = VM.read_text(encoding="utf-8")
    notify = NOTIFY.read_text(encoding="utf-8")

    finish = function(vm, "private fun finishBatch(")
    check_true("finishBatch counts the run", "val counts = RunCounts.of(current.batch)" in finish)
    check_true("The summary comes before every other end notice",
               0 <= finish.find("counts.wantsSummary") < finish.find("showCancelled(context)")
               < finish.find("trueFailed > 0 && done == 0"))
    check_true("The summary names who cancelled", "cancelledByUser = isBatchCancelled" in finish)

    counts = function(vm, "internal data class RunCounts(")
    check_true("A summary is for several links that did not all save",
               "total > 1 && (failed > 0 || cancelled > 0)" in counts)
    check_true("A cancel is not counted as a failure",
               "it.error != CANCELLED_REASON" in counts and "it.error == CANCELLED_REASON" in counts)
    check_true("The cancelled reason is the one the run writes",
               'internal const val CANCELLED_REASON = "Cancelled"' in vm
               and 'markBatch(plan.info.url, BatchState.FAILED, "Cancelled")' in vm)

    show = function(notify, "fun showRunSummary(")
    check_true("The summary is one translated sentence with three counts",
               "getString(R.string.notification_run_summary, done, failed, cancelled)" in show)
    for key in ("notification_run_cancelled_title", "notification_run_failed_title", "notification_run_finished_title"):
        check_true(f"The summary heading uses {key}", f"R.string.{key}" in show)
    check_true("It replaces the progress notification", "cancelProgress(context)" in show)
    check_true("It goes where the end of a run goes", "notify(COMPLETE_NOTIFICATION_ID" in show)
    check_true("A user's cancel is never loud", "!cancelledByUser" in show)
    check_true("Tapping it opens the queue",
               "putExtra(MainActivity.EXTRA_NAVIGATE_TO, QUEUE_ROUTE)" in show
               and 'private const val QUEUE_ROUTE = "queue"' in notify)
    nav = NAV.read_text(encoding="utf-8")
    check_true("The queue route exists", 'data object Queue : Screen("queue"' in nav)
    deep = block(nav, "LaunchedEffect(pendingRoute) {", 1200)
    check_true("A tab opened from a notification is opened the way the bottom bar opens it",
               "if (bottomNavItems.any { it.route == route })" in deep
               and "popUpTo(navController.graph.findStartDestination().id)" in deep
               and "restoreState = true" in deep and "launchSingleTop = true" in deep)
    check_true("Other routes are still pushed as screens",
               "} else {\n                navController.navigate(route)\n            }" in deep)
    check_true("Its request code is its own",
               "context, 4," in show and not re.search(r"getActivity\(\s*context,\s*4,", notify.replace(show, "")))

    key_re = re.compile(r'<string name="(notification_run_(?:cancelled_title|failed_title|finished_title|summary))">([^<]*)</string>')
    folders = sorted(p for p in RES.iterdir() if p.is_dir() and p.name.startswith("values")
                     and (p / "strings.xml").exists())
    for folder in folders:
        found = dict(key_re.findall((folder / "strings.xml").read_text(encoding="utf-8")))
        check(f"{folder.name} has all four summary strings", len(found), 4)
        summary = found.get("notification_run_summary", "")
        check(f"{folder.name} summary keeps its three counts",
              sorted(re.findall(r"%\d\$d", summary)), ["%1$d", "%2$d", "%3$d"])


def test_screens():
    print("\n--- Suite 3: the screens ---")
    home = HOME.read_text(encoding="utf-8")
    check_true("The home list puts the active download first",
               "val active = state.active?.url?.takeIf { state.isDownloading }" in home)
    check_true("The home list scrolls to the active download", "val activeUrl = state.active?.url" in home)
    sheet = block(home, "    if (sheetVisible) {", 4000)
    start = block(sheet, "downloadViewModel.startDownload(", 600)
    check_true("The home sheet's Download names its own link", "info = info," in start)
    check_true("A choice made before the read still names its link",
               "info = info," in block(sheet, "downloadViewModel.downloadOnceFormatsRead(", 400))

    queue = QUEUE.read_text(encoding="utf-8")
    running = function(queue, "private fun runningItems(")
    check_true("The Running tab reads the run's active item",
               "state.active?.let" in running and "state.isDownloading || batchItem?.state == BatchState.PAUSED" in running)
    check_true("The Queued tab reads the written-down queue",
               "val waiting = queueList.filterNot { it.url in runningUrls || it.paused }" in queue)


def replay_pause(new_way, pause_all):
    """A queue of five, paused on the second item. Returns what started after the pause."""
    queue = ["a", "b", "c", "d", "e"]
    started, paused, hold = [], [], False
    while queue:
        if new_way and hold:
            break
        item = queue.pop(0)
        started.append(item)
        if item == "b":
            paused.append(item)
            if new_way and pause_all:
                hold = True
            # The run goes on while anything unpaused is waiting, unless the queue is held.
    after = started[started.index("b") + 1:]
    return after, paused


def test_pause():
    print("\n--- Suite 4: pausing a run ---")
    after, _ = replay_pause(new_way=False, pause_all=True)
    check("Old: Pause all paused one item and the run went on, as reported", after, ["c", "d", "e"])
    after, paused = replay_pause(new_way=True, pause_all=True)
    check("New: Pause all starts nothing after the item in hand", (after, paused), ([], ["b"]))
    after, _ = replay_pause(new_way=True, pause_all=False)
    check("New: a card's own Pause still lets the rest of the queue go on", after, ["c", "d", "e"])

    vm = VM.read_text(encoding="utf-8")
    pause_all = function(vm, "fun pauseAll()")
    check_true("Pause all holds the run", "holdRun = true" in pause_all)
    check_true("Pause all writes the hold down", "DownloadQueueRepository.setHeld(HazelApp.instance, true)" in pause_all)
    check_true("Pause all pauses the download in hand", "pauseDownload()" in pause_all)

    run = function(vm, "private fun runQueue(")
    check_true("A held run takes nothing more from the queue", "if (isBatchCancelled || holdRun) break" in run)
    check_true("A pause asked for before the engine starts is honoured before it starts",
               run.find("if (isPaused || holdRun) {") < run.find("executeYtDlp(") and run.find("if (isPaused || holdRun) {") > 0)
    check("Every pause in the run is recorded as one", run.count("holdForResume(next)"), run.count("pausedNow = true"))
    close = block(run, "val more = synchronized(queue) {", 700)
    check_true("A held run does not start another", "queue.any { !it.paused } && !holdRun" in close)
    check_true("A paused run is let go without being reported finished",
               "if (!isBatchCancelled && (pausedNow || holdRun)) settleHeld(app)" in close
               and "else finishBatch(app)" in close)

    settle = function(vm, "private fun settleHeld(")
    check_true("A held run stops showing as running", "isDownloading = false" in settle)
    check_true("Its paused link keeps its card", "it.state == BatchState.PAUSED" in settle)
    check_true("A hold with nothing paused still leaves a Resume in the shade", "showPaused(" in settle)

    for name in ("fun startBatch(", "fun resumeDownload()", "fun cancelAllDownloads()"):
        check_true(f"{name} releases the hold", "releaseHold()" in function(vm, name))
    check_true("A queue held when the app went away stays held",
               "DownloadQueueRepository.isHeld(app)" in function(vm, "private fun restoreQueue()"))
    discard = function(vm, "private fun discardHeldDownload()")
    check_true("Cancelling a held queue reports every count", "RunCounts.of(_state.value.batch)" in discard
               and "showRunSummary(" in discard and "showCancelled(app)" in discard)

    repo = (SRC / "data/DownloadQueueRepository.kt").read_text(encoding="utf-8")
    check_true("The hold is its own key", 'booleanPreferencesKey("download_queue_held")' in repo)
    check_true("The hold is removed rather than written false", "if (held) prefs[HELD_KEY] = true else prefs.remove(HELD_KEY)" in repo)

    receiver = (SRC / "download/DownloadActionReceiver.kt").read_text(encoding="utf-8")
    check_true("The notification's Pause pauses the queue", "ACTION_PAUSE -> viewModel?.pauseAll()" in receiver)

    queue = QUEUE.read_text(encoding="utf-8")
    menu = block(queue, "if (state.isDownloading) {", 900)
    check_true("The queue menu's Pause pauses the queue", "downloadViewModel.pauseAll()" in menu)
    check_true("A card's Pause pauses only its link", "onPause = downloadViewModel::pauseDownload" in queue)
    check_true("A paused link is never drawn as running",
               "state.isDownloading && batchItem?.state != BatchState.PAUSED" in function(queue, "private fun runningItems("))

    card = (SRC / "ui/components/MediaCards.kt").read_text(encoding="utf-8")
    glyph = block(card, "if (isPaused && !isDownloading) {", 700)
    check_true("A paused card shows a pause glyph over its artwork", "Icons.Filled.Pause" in glyph)
    check_true("The stage track only moves for a running download",
               "if (isDownloading && processingSteps.isNotEmpty()) {" in card
               and "if ((isDownloading || isPaused) && processingSteps.isNotEmpty())" not in card)


def main():
    print("=" * 70)
    print("  Queue run state & run summary harness")
    print("=" * 70)
    test_model()
    test_view_model()
    test_summary()
    test_screens()
    test_pause()
    print(f"\n  Summary: {PASS_COUNT}/{PASS_COUNT + FAIL_COUNT} tests PASSED, {FAIL_COUNT} FAILED")
    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
