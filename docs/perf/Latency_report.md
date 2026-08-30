> **Status:** Current — 2026-08-30

# Hazel - link-reading latency: findings and fixes


Measured on the connected device (Realme RMX3151, Helio G85), yt-dlp **2026.08.19**,
over the session's live network. Instrumentation was temporary and has been removed.

---

## 1. Where the time actually went

A metadata read was taking **6.5–9 s** before a card appeared. Decomposed by running
progressively less work in the same engine:

| stage | time | what it covers |
|---|---|---|
| `--version` | **2160 ms** | process spawn + `import yt_dlp`, no network at all |
| `--simulate --print title` | 5905 ms | the above + full extraction |
| `--dump-single-json` | 6513 ms | the above + serialising the format list |

So every single invocation costs a **fixed ~2.2 s before it does anything**, then ~3.7 s of
extraction, then ~0.6 s of JSON. A warm process measured the same as a cold one (6.66 s vs
6.78 s), confirming this is per-invocation cost, not first-run cost.

## 2. Hypotheses tested and rejected

Each of these was measured, not reasoned about, and none of them was the cause:

| hypothesis | result | verdict |
|---|---|---|
| Payload too large (641 KB) | `skip=translated_subs` → 6251 ms vs 6272 ms baseline | **rejected** - no effect |
| Captions bloat the dump | `skip=translated_subs,hls,dash` → 5702 ms, payload 641→590 KB | marginal, not the cause |
| Too many YouTube player clients | `player_client=tv` and `tv,web_safari` both **fail**: "The page needs to be reloaded" | **rejected** - unusable |
| Bundled yt-dlp is stale | reported `2026.08.19`, current | **rejected** |
| Our yt-dlp cache is not working | `--no-cache-dir` → **19298 ms** vs 6079 ms | rejected - cache already saves ~13 s |
| The new listing options are slower | A/B same URL: OLD `--no-playlist` 9880 ms vs NEW `--flat-playlist --lazy-playlist` 7884 ms | **rejected** - new is not slower |

Note on the A/B: the identical call measured 7.9 s and 26 s minutes apart, so this device's
network varies by ~3x. Single measurements here are not trustworthy; only paired ones are.

## 3. The regression reported mid-session

Latency appeared to *increase* partway through. Cause: **the measurement harness was inside
the builds being tested.** `ProbeBenchmark`, a NewPipe comparison call, and later
`LatencyCheck` each ran extra full probes before the real one - 4–6 additional invocations,
roughly 30–80 s per fetch. It was instrumentation, not the feature work. All of it is now
deleted.

## 4. The actual finding

**The extraction was being done twice.** Once to build the format sheet, then again from
scratch when the download started. Measured end to end:

| path | time to first byte |
|---|---|
| download re-extracting (previous behaviour) | **5807 ms** |
| download replaying the probe's JSON (`--load-info-json`) | **3163 ms** |

3163 ms is essentially the 2.2 s engine start plus ~1 s. The extraction is gone.

## 5. What was changed

| change | effect |
|---|---|
| `--load-info-json` replays the probe's payload into the download | **−2.6 s on every download** |
| `InfoCache` keeps parsed metadata for 30 min | re-pasted link skips the read entirely (**−6.5 s**) |
| `InfoCache` keeps the raw JSON for 60 min | feeds the above; short enough that signed URLs stay valid |
| `LinkKey` canonicalisation | `youtu.be/ID` and `youtube.com/watch?v=ID` now share a cache key |
| Playlist entries resolve formats lazily | a 300-entry playlist costs 1 read, not 300 |

Before: paste → probe 6.5 s → sheet → download re-extract 5.8 s ≈ **12.3 s to first byte**.
After: paste → probe 6.5 s → sheet → download 3.2 s ≈ **9.7 s**, and a repeat link ≈ **3.2 s**.

### A bug this surfaced

The first cache implementation never hit. `LinkKey.canonical` keyed on the raw host, so
`youtu.be/ID` → `youtu.be/ID` but `youtube.com/watch?v=ID` → `youtube.com/ID`. Two keys for
one video. It now keys on the *service* (`youtube/ID`), which fixes the cache and the
duplicate detection that depends on the same comparison.

## 6. What was deliberately not done

- **NewPipe for single-video metadata.** Measured at 1677–2524 ms against yt-dlp's ~6 s, so
  it is genuinely faster - but it cannot supply formats, because its ids are itags and
  yt-dlp names multi-audio streams differently (`251-drc`, `251-0`). A format picked from
  them can fail at download time, after the user has chosen it. It is used for listing only.
- **`player_client` overrides.** They fail outright on this yt-dlp version.
- The remaining ~6 s first read is the floor for yt-dlp on this hardware.

---

## Files changed

### New
| file | purpose |
|---|---|
| `download/InfoCache.kt` | metadata + raw-JSON cache behind both speed fixes |
| `util/LinkKey.kt` | canonical link identity, for the cache and duplicate detection |
| `download/extractor/LinkContents.kt` | one item vs many, as a type |
| `download/extractor/LinkResolver.kt` | picks the reader, falls back to yt-dlp silently |
| `download/extractor/NewPipeLister.kt` | listing-only extractor, never formats or downloads |
| `download/extractor/NewPipeDownloader.kt` | its HTTP transport over the app's OkHttp |
| `ui/screens/download/AlreadyDownloadedDialog.kt` | the repeat-download warning, shared by both screens |

### Modified
| file | change |
|---|---|
| `download/MediaProbe.kt` | added `listContents` (`--flat-playlist --lazy-playlist`), entry parsing, cache writes; removed dead `probeAll`/`probeGroup` |
| `download/DownloadViewModel.kt` | playlist expansion, lazy per-item formats, `--load-info-json` with stale-payload retry |
| `download/DownloadNotificationHelper.kt` | title shortening, media title on completion |
| `data/SettingsRepository.kt` | listing-source setting |
| `ui/screens/download/DownloadScreen.kt` | processing state, format resolution on sheet open, share-only duplicate check |
| `ui/screens/download/SearchScreen.kt` | duplicate warning moved here, before the read |
| `ui/screens/download/FormatSheet.kt` | full-height open; re-selects once formats arrive |
| `ui/screens/download/FormatSelectionSheet.kt` | full-height open |
| `ui/screens/more/FetchSettingsScreen.kt` | listing-source rows |
| `ui/components/Shimmer.kt` | angled two-layer processing sweep |
| `ui/theme/Color.kt`, `ui/theme/Theme.kt` | warm light palette, explicit container tones |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | NewPipeExtractor v0.26.5 (latest release) |

### Removed after measuring
`download/ProbeBenchmark.kt`, `download/LatencyCheck.kt`, all `HazelPerf` logging, the
`SystemClock` timing calls, and the temporary `applicationIdSuffix` on the debug build type.