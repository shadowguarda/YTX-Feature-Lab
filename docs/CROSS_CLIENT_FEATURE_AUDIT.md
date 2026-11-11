# Cross-Client Feature Discovery Audit

**Branch:** `feat/cross-client-feature-expansion`  
**Scope:** YTXTube feature audit against the open-source YouTube-client ecosystem.  
**Status:** Research baseline completed; implementation candidates prioritized below.

## 1. Current YTXTube baseline

The repository README and implementation notes show that YTXTube is already a substantial Kotlin/Jetpack Compose client built around Media3 ExoPlayer and NewPipeExtractor. The following are already present and should be improved rather than duplicated:

- Video/music playback, background playback, PiP, quality selection, HDR formats, audio tracks and captions.
- Downloads, download/storage screens, playback queue and last-queue restoration.
- Shorts, Home and subscriptions feeds, channels, playlists, history and search.
- SponsorBlock, DeArrow, Return YouTube Dislike, feed/content filters.
- Synced lyrics, ListenBrainz scrobbling, equalizer, skip silence and sleep timer.
- Gesture controls, YTX Tweaks, theme/Glass controls, ambient light.
- Settings search, privacy controls, profiles and JSON backup/restore.
- Local related-video ranking (off by default).

Relevant existing modules confirmed in the repository include `Feeds.kt`, `Screens.kt`, `Account.kt`, `PlayerState.kt`, `SettingsScreen.kt`, `Tweaks.kt`, `Discovery.kt`, `Downloads.kt`, `QueueStore.kt`, `SponsorBlock.kt`, `DeArrow.kt`, `ReturnDislike.kt`, and `ListenBrainz.kt`.

## 2. Competitor references and useful ideas

| Project | Useful ideas to evaluate | YTXTube overlap / direction |
|---|---|---|
| [NewPipe](https://github.com/TeamNewPipe/NewPipe) | Local subscriptions, local playlists, queueing, popup/background playback, subtitle workflows, history controls | Playback and downloads already exist; prioritize local organization, portability and polish |
| [PipePipe](https://github.com/InfinityLoop1308/PipePipe) | Richer format selection, advanced search, per-content playback options, multi-source exploration | YTX already has quality and search filters; investigate gaps before adding providers |
| [LibreTube](https://github.com/libre-tube/LibreTube) | Subscription groups, playlist bookmarks, import/export, dislike data, Material UI patterns | Groups and robust migration are meaningful gaps |
| [Metrolist](https://github.com/Metrolist/Metrolist) | Music library workflows, queue, lyrics and scrobbling | Lyrics/scrobbling/equalizer exist; focus on library organization and queue usability |
| [Grayjay](https://github.com/GrayjayTeam/Grayjay) | Unified creator feeds and multi-source architecture | Large architectural change; do not add without an explicit product decision |
| [SkyTube](https://github.com/SkyTubeTeam/SkyTube) | Subscription feed organization and offline-first browsing patterns | Evaluate channel groups and feed controls |
| [Tubular](https://github.com/TubeArchivist/TubeArchivist) | User-controlled playback and content organization ideas | Treat as inspiration only; it is not a drop-in Android client |

Feature inspiration is not permission to copy source code. Review each project's license and notices before any code reuse. Prefer independent implementations based on verified behavior.

## 3. Gap and priority matrix

| Candidate | Existing status | Value | Complexity | Priority |
|---|---|---:|---:|---|
| Subscription groups / channel folders | Planned; no group model found in the initial code scan | High | Medium | P0 |
| Subscription import/export (NewPipe / compatible formats) | Not confirmed; inspect existing backup/import paths first | High | Medium–High | P0 |
| Local playlists and saved collections | YouTube playlists and downloads exist; local-only collections not confirmed | High | Medium | P1 |
| Drag-reorder and save queue as playlist | Roadmap says planned | High | Medium | P1 |
| Saved searches | Not confirmed | Medium | Low–Medium | P1 |
| Compact / comfortable feed density | Planned | Medium | Low | P1 |
| Per-channel playback defaults | Not confirmed | Medium | Medium | P2 |
| Home dashboard section ordering | Planned | High | Medium–High | P2 |
| Richer playback recovery diagnostics | Existing recovery; further edge-case audit needed | High | Medium | P0 |
| Android Auto / media-session validation | Not confirmed complete | Medium–High | High | P2 |
| Multi-provider / casting architecture | Not implemented / large bet | Variable | Very High | Defer |

“Not confirmed” means the current audit did not establish the feature's absence; search the full codebase before implementing it.

## 4. First implementation batch

Start with **Subscription Organization & Portability**, because it is a concrete gap called out by the existing roadmap and adds value without replacing the extractor/player architecture.

### Batch A — Subscription Groups

1. Add a local, persistent model for user-created channel groups.
2. Allow creating, renaming and deleting groups.
3. Allow assigning and removing subscribed channels from groups.
4. Support an ungrouped/default view and a channel appearing in more than one group only if the model and UI make that behavior clear.
5. Add group chips or a compact selector to the subscriptions experience.
6. Filter the existing subscription feed using the selected group's channel IDs; do not issue new network requests per group unless required by the existing feed contract.
7. Preserve groups across app restarts and account switches; define whether groups are global or account-scoped before implementation.
8. Handle channel removal and stale IDs safely.
9. Add unit tests for persistence, membership, rename/delete, duplicate handling and account isolation.
10. Keep the UI consistent with the current Liquid Glass / Material design and accessible on phones and Android TV.

### Batch B — Subscription Portability

After the group model is stable:

1. Inspect current Profiles/Backup Center and all existing import/export formats.
2. Design a versioned subscription export format.
3. Support importing channel IDs and names from a documented compatible format.
4. Validate input size, malformed JSON, duplicates and unsupported versions.
5. Show a preview and counts before applying changes.
6. Never export cookies, authentication tokens, session data or credentials.
7. Add round-trip and malformed-input tests.

Do not implement both batches in one giant change if it makes review or testing difficult.

## 5. Required implementation guardrails

- Read the full relevant source files before editing; do not rely on search snippets.
- Confirm whether a candidate already exists before adding another implementation.
- Preserve current settings keys and existing user data.
- Keep all new local data scoped deliberately (global vs account-specific).
- Use stable IDs, not channel display names, as membership keys.
- Keep database/preferences writes off the main thread where appropriate.
- Expose clear loading, empty, error and confirmation states.
- Avoid fake toggles and placeholder actions.
- Do not change playback extraction, account authentication or network endpoints for this feature batch.
- Add tests for pure logic and persistence behavior.
- Run the repository's CI build and tests; distinguish pre-existing failures from regressions.
- Do not merge or publish a release without explicit authorization.
- Device-test status must be reported honestly; CI is not a substitute for testing on a phone.

## 6. Definition of done

The first PR is complete only when subscription groups are real, persistent, usable in the subscriptions UI, account-safe, tested, and documented. The PR must state exactly what was built, what was tested, and what remains unverified.

## 7. Sources to inspect during implementation

- [NewPipe](https://github.com/TeamNewPipe/NewPipe)
- [PipePipe](https://github.com/InfinityLoop1308/PipePipe)
- [LibreTube](https://github.com/libre-tube/LibreTube)
- [Metrolist](https://github.com/Metrolist/Metrolist)
- [Grayjay](https://github.com/GrayjayTeam/Grayjay)
- [SkyTube](https://github.com/SkyTubeTeam/SkyTube)
