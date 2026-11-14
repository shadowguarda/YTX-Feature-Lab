# Implementation notes

## Settings search and Privacy (SettingsSearch.kt)
- **What:** a searchable index of settings (`SettingIndex`) and a privacy sheet.
- **Approach:** each entry has a title, a location and keywords; every typed word must match. Entries that live in dialogs are opened through `SettingTarget`; options that only exist inside the video player cannot be linked to, so they show where to find them instead of pretending to navigate.
- **Privacy sheet:** clearing uses a two-tap confirm. It clears `SearchHistory`, `Watched`, `ResumeStore` and the cache directories. The "leaves the device" list was written from the actual endpoints in the code (NewPipe Extractor, sponsor.ajay.app for SponsorBlock and DeArrow, returnyoutubedislikeapi.com when enabled, api.github.com for updates).
- **Dependencies:** none new.
- **Limits:** the index is hand-maintained; a new setting must be added to it. Not device-tested.

## YTX Tweaks (Tweaks.kt)
- Persisted `BoolTweak`/`IntTweak` objects read by feed filtering (`ContentFilters`), the video gesture code and the watch page.
- `Watched` keeps its own list because `ResumeStore` deliberately forgets finished videos.

## Ambient light (AmbientLight.kt, AmbientSettingsSheet.kt)
- Samples a 48x27 frame from the video TextureView, box-blurs it and paints it behind the page; every parameter is user-tunable. Adapted from MeeTube (MIT), credited in THIRD_PARTY_NOTICES.md.

## Discovery, ListenBrainz, frame step, queue restore
- **Discovery.kt:** `DiscoveryRanker` is pure logic (unit-tested in `DiscoveryRankerTest`): title words weighted by recency plus a channel bonus that fades when a channel is overplayed. `Discovery` stores up to 200 signals locally. Only reorders the related list; ties keep YouTube's order.
- **ListenBrainz.kt:** real HTTP submission (`/1/submit-listens`) with the user's token (kept in `SecureStore`), offline queue, retry. Limit: untested against the live service from this environment.
- **FrameStep.kt:** exact seeks of one frame (real frame rate when the stream reports it).
- **QueueStore.kt:** remembers the playing video and queue; the queue sheet offers to restore it.
- **Origin:** ideas came from two prototype archives (adv yt / yt). Their code was mostly demonstration state (fake cast devices, scrobbles never sent, fixed storage numbers), so it was not copied; these are new implementations of the viable ideas.

## Tweaks round 2 (2.9.0)
- All options live in `Tweaks` (persisted, observable) and are read at the point of use: `startPlayback` (default speed, skip first seconds, auto captions), `StreamSelection` via `Tweaks.cappedHeight` (mobile-data quality cap, uses `isActiveNetworkMetered`), `VideoRow` (thumbnail width, title lines, views, upload date, duration), the theme root (text and interface scale through `LocalDensity`), history stores (incognito and pausing gate `SearchHistory`, `ResumeStore`, `RecentMusic`, `Discovery`, `QueueStore`, `UsageStats`), and the video gesture handler.
- Limits: interface size changes density for the whole app, so very large text plus 110% interface size can crowd small screens; the mobile-data cap applies when the stream is resolved, not mid-playback; "Preload" off disables prefetching of feeds, related videos, Shorts and rows.

## Planning features: Watch plan, Insights, Focus guard, Autopilot, Time-fit
Files: `WatchPlan.kt`, `WatchLog.kt`, `FocusGuard.kt`, `ChapterAutopilot.kt`, `TimeFitQueue.kt`, shared widgets in `YtxLabUi.kt`. All are reached from Labs and all decisions live in pure `*Logic` objects covered by `YtxPlanningFeaturesTest`.
- Watch plan: `WatchPlanManager` (prefs `watch_plan`) schedules an inexact `AlarmManager` alarm per item; `WatchPlanReceiver` posts the notification. Alarms are re-armed in `Application.onCreate`, not on boot.
- Watch insights: `WatchSessionTracker` (started in `PlaybackService`) records playing time per video through a `Player.Listener`; sessions within 30 s of each other for the same video merge. Skipped when incognito or history pausing is on.
- Focus guard: `FocusGuardTicker` ticks every 5 s while the service runs; a day change resets the counters; two idle minutes count as a break.
- Chapter autopilot: `ChapterAutopilotEffect` runs in `MainActivity` and polls the position; a skipped chapter is remembered per video so rewinding is not fought.
- Time-fit queue: `TimeFitLogic.select` filters (live, too short, too long, watched, duplicates), ranks by interest words, favourite channels and planned items, then fills the budget greedily. Starting playback waits for the first video to load before queueing the rest.
- Not verified: none of this has been compiled or run in the authoring environment (no Android SDK); CI is the first build.

## Downloads library (DownloadsLibrary.kt, DownloadsUi.kt)
`DownloadsScreen` maps each task to a plain `DownloadEntry` and lets `DownloadsViewLogic` filter, sort and group it (unit-tested in `DownloadsLibraryTest`). View choices live in `DownloadsViewPrefs` (prefs `downloads_view`, initialised the first time the screen is shown). Selection is per tab and is dropped when a task disappears. "Retry failed" and "Resume all" call `Downloads.resume`, which restarts the file from zero. Free space comes from `StatFs` on the download folder. Not run on a device.

## Testing status
CI (`build.yml`) compiles the release build and runs the unit tests. No feature in this document has been run on a device.

## 2.9.2: imported zip modules

Two uploaded forks (2.9.1 and 2.9.2) were merged in. All 19 new Kotlin modules compile and their
bundled unit tests pass in CI, and their settings screens are reachable from Settings, Labs.
Fixes made while merging: renamed clashing `setX` functions and duplicate enums, switched the
auto-preset rule to check video type before the over-broad `isMusicTrack`, corrected one wrong test
expectation, and kept the richer `FrameStep`. Not wired into playback or feeds yet (settings are
stored, but most do not yet change behaviour): app-lock overlay, floating playback toolbar,
home discovery banner, music visualizer. Nothing has been run on a device.
