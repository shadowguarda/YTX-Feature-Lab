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

## Testing status
CI (`build.yml`) compiles the release build and runs the unit tests. No feature in this document has been run on a device.
