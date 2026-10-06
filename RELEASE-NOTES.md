### Downloads & Instant
- **One-word titles fixed:** a title like "Flickermood", or a one-word author, was saved as "NA". Now it's saved correctly.
- **Instant can save audio again:** a new "Instant saves as" setting lets you pick Video or Audio only.
- **Instant downloads show on Home:** before, an Instant download only appeared in the queue. Now its cards show on the home screen too.
- **Lost queue items fixed:** a link added just as a run was ending could start a second run or disappear after a restart. That's fixed.

### Failed downloads (redesigned)
- Failed cards now show **Error Log** and **Retry** on the card.
- Tapping a failed card opens that link's download sheet.
- Errors now show in a true red instead of a pale pink.

### Format sheet
- **Best/Worst order:** Best is always at the top and Worst at the bottom, for both video and audio.
- **NewPipe notice:** Sheet now explains why the list doesn't change for signed-in sites (NewPipe can't use your login)
- **New setting:** choose which tab (Audio or Video) the download sheet opens on.

### UI
- A **draggable scrollbar** on the Home, Queue and Downloads lists.
- The sign-in page opens with a loader instead of a black screen.
- Settings choice dialogs are smaller and confirm with **OK**.
- The share menu shows "Instant" instead of "Hazel Hazel Instant".
- The empty home screen no longer shows a header line as if it were scrolled.
- Sheets kept ignoring taps after the link dialog closed. That's fixed.
- **Long-press a remembered search** to copy or remove it.

### Queue & run
- **Running tab no longer goes empty mid-playlist** when you clear Home or share a link during a run.
- **Links added during a run now join the queue.** A search result downloaded while a playlist ran used to vanish.
- **One summary when a run stops:** "Downloaded: 34 · Failed: 0 · Cancelled: 40"
- **Pause all really pauses everything.** Nothing new starts until you tap Resume, even after a restart.
- **Tap a paused card to resume it.** It shows how much is downloaded, measured from the files on disk, never 0%.
- **Resumed downloads go first** and stay on the Running tab.
- **Change a queued link's format:** tap a waiting card, or use Details, to open its own sheet and change the choice. You can even restart the current download with a new format.
- **Retry all failed** added to the queue menu.
- **Failed cards get a Link and logs button**
- **Home button works** after opening the queue from a notification.

### Download sheet
- The Link and Incognito buttons moved next to the Audio/Video tabs, so the sheet is one row shorter.
- **Keyboard opens over the sheet** instead of squashing it. The sheet lifts only enough to keep the field you're typing in visible.
- **Instant reads have their own limit** setting.

### Speed
- Screens with long queues or big history no longer re-read stored data on every redraw, so they scroll and update more smoothly.

### Settings & docs
- New **Official site** row in Documentation.
- **What's new** opens in-app with the offered or installed version's changelog;
- What's new is styled like the website's changelog, and documentation pages get the draggable scrollbar.
- Documentation pages now render Markdown.