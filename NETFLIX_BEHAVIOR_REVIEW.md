# Netflix reference recording

Reviewed Chris's 82.6-second IMG_7483.mp4 recording. It shows browsing, navigation and previews; it does not demonstrate episode playback, Skip Intro or the next-episode countdown.

Observed browsing behavior:
- Compact persistent top navigation with a clearly selected destination.
- A large featured title with artwork/video, short description and Play/More Info actions.
- Horizontal shelves with a strong selected-card outline.
- The selected title expands into a wider preview in its shelf; neighboring titles remain visible.
- Title metadata and description stay attached to that focused shelf.
- Continue Watching is prominent and preserves episode/progress context.
- Focus, artwork and the selected destination stay coordinated during remote navigation.

Proposed later Petalix presentation:
- Home, Live TV, Movies, TV Shows and My List in the main navigation.
- Keep the native Live TV guide and Sports Hub as working destinations.
- Use one coordinated focus/preview state with a dwell delay, cancelling stale trailers on navigation.
- Expand the selected movie/show within its shelf and keep its metadata adjacent to that shelf.
- Keep service artwork as one animated card, rather than covering it with an unrelated opaque logo video.
- Preserve the current test APK work before attempting this broader browsing redesign.

Current implementation scope is playback settings and the approved guide/Home fixes. A complete Netflix-style browsing redesign is separate work, not a claimed feature of this candidate.
