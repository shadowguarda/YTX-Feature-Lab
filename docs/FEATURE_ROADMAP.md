# Feature roadmap

Status key: **Implemented** = in a release and built by CI; **Partial** = exists but incomplete; **Planned** = not started. "Device-tested" is `no` for everything added by the lab work so far: CI compiles the app and runs unit tests, but the UI has not been exercised on a phone.

| Area (brief) | Item | Status | Notes |
|---|---|---|---|
| A. Player | Double-tap seek step, hold-to-speed rate, controls timeout, gesture sensitivity | Implemented (YTX Tweaks) | Device-tested: no |
| A. Player | Chapter ticks, thumbnails and haptics while scrubbing, stats HUD, equalizer, loop, skip silence | Implemented | Device-tested: no |
| A. Player | Frame-by-frame stepping while paused | Implemented | Exact seeks; accuracy depends on the stream. Device-tested: no |
| A. Player | Alternative player layouts, customisable control layout | Planned | |
| A. Player | Playback recovery | Implemented (earlier work) | Unit tests for the policy |
| B. Theme | Glass settings and theme presets already in the app; live ambient light with its own settings | Implemented / Partial | Separate player/home themes, theme import/export planned |
| C. Offline | Downloads, storage usage and cache clearing | Existing | Queue reordering, profiles, collections planned |
| D. Queue | Add to queue, remove, clear | Existing | |
| D. Queue | Restore last queue after the app is closed | Implemented | Drag reorder and save as playlist still planned |
| E. Music | Synced lyrics, recently played | Existing | |
| E. Music | Lyrics timing offset; ListenBrainz scrobbling with offline queue | Implemented | Crossfade, folders planned. Device/network-tested: no |
| F. Subscriptions | Subscribe/unsubscribe | Existing | Groups, folders, import/export planned |
| G. Discovery | Personalised "More videos" ranking from title words and channels you watch, with a reason shown, resettable | Implemented | Off by default; only reorders YouTube's related list, so it is not a full recommender. New-channel discovery and a Home feed remain planned |
| H. Home dashboard | Reorderable sections | Planned | |
| I. Privacy | Privacy sheet: clear search history, watched list, resume positions, cache; plain-language list of what leaves the device | Implemented | Device-tested: no |
| J. Settings search | Search box that jumps to the matching setting; player-only options show where to find them | Implemented | Device-tested: no |
| Feeds | Hide Shorts/live/watched/short/long/low-view videos; search filters | Implemented | |
| Watch page | Share at time, copy link, open in browser, mark watched, like/dislike bar | Implemented | |
| K. Planning | Watch plan: schedule videos with reminder notifications (Labs, Watch plan) | Implemented | Inexact alarms, re-armed when the app starts, so reminders set before a reboot arrive late until the app is opened. Device-tested: no |
| K. Planning | Watch insights: on-device watch log, charts, streak, history search (Labs, Watch insights) | Implemented | Kept 120 days, never uploaded; off when history is paused. Device-tested: no |
| K. Planning | Focus guard: daily budget and break nudges (Labs, Focus guard) | Implemented | Nudges are toasts, not a hard block. Device-tested: no |
| K. Planning | Chapter autopilot: skip/speed chapters by keyword (Labs, Chapter autopilot) | Implemented | Only works on videos that have chapters; never skips the last chapter. Device-tested: no |
| K. Planning | Time-fit queue: build a queue that fits N minutes (Labs, Time-fit queue) | Implemented | Greedy fit, not an optimal packing; needs loaded feeds. Device-tested: no |
| C. Offline | Downloads tab: search, sort, group by channel, storage summary, multi-select delete, bulk pause/resume/retry/clear, density/thumbnail/summary options | Implemented | Retry restarts the file from zero (existing resume behaviour). Device-tested: no |
| A. Player | Player layout settings (Labs): the fill-screen, picture-in-picture, settings and fullscreen buttons follow their on/off switches | Partial | Only those four buttons are wired; reordering, zones and presets do not change the live player yet. Device-tested: no |
| B. Theme | Liquid Glass Studio: blur per surface, refraction, inner shadow, chromatic aberration, surface opacity and border now change the real glass | Implemented | Scaled from each surface's design values; untouched settings look the same. Device-tested: no |
| B. Theme | Accent colours: separate home and player accents (Settings, Appearance), typed #RRGGBB, opt-in switch | Implemented | Recolours primary/container/tint only; background tint, glass opacity and glow in the scoped theme are not applied yet. Device-tested: no |
| C. Offline | Download progress: audio and single-stream downloads showed the last chunk size instead of the running total; retried chunks were counted twice; speed and time left added | Fixed | Device-tested: no |
| C. Offline | Free up space: remove watched, oldest or largest downloads until a chosen amount is freed, with preview and confirmation | Implemented | "Watched" uses the app's watched list (videos finished or marked). Device-tested: no |
| C. Offline | Play all / Shuffle for downloaded videos; overall speed and time left in the Downloads summary | Implemented | Plays through the player queue; if something is already playing it adds to the queue instead. Device-tested: no |
| K. Planning | Export watch history as CSV (share sheet) | Implemented | Newest 1500 sessions; titles starting with = + - @ are prefixed so spreadsheets do not run them. Device-tested: no |
| UI | Dynamic Island: floating playback pill with artwork, progress ring, play/pause, next | Implemented | Shows while playing and 4 s after a pause; hidden in the full player, fullscreen, Shorts and PiP. It does not replace the bottom mini player. Device-tested: no |
| UI | Quick Settings panel from the header slider icon: privacy, glass, glow, island, wallpaper, theme mode | Implemented | Device-tested: no |
| UI | Adaptive dock: hide/reorder tabs (Home locked, min 2), label modes Always / Selected only / Icons only | Implemented | Reorder uses Up/Down buttons, not drag. Device-tested: no |
| UI | Custom home layout: shelves above the feed (Continue watching, Latest from subscriptions, Watch plan), hide and reorder | Implemented | Up/Down buttons, not drag; shows on the All filter only. The older HomeDashboardManager is not used. Device-tested: no |
| UI | Interactive mini player: swipe left for next, right to restart (existing swipe down to dismiss and drag up to expand kept) | Implemented | Previous track is not available in the player, so right restarts. Device-tested: no |
| UI | Dynamic artwork: player colour uses the most vivid artwork shade instead of a 1x1 average | Implemented | Switchable in Quick settings. The player already tinted itself from artwork; this improves the colour. |
| UI | Ambient wallpaper: blurred, slowly drifting current artwork behind the app | Implemented | Off by default; blur needs Android 12+, older versions get the colour wash only. Device-tested: no |
| UI | Shared-element transition from a feed thumbnail to the player | Not done | Needs the tapped card's bounds fed into the player morph; to be built with device testing. The mini player to player morph already exists. |
