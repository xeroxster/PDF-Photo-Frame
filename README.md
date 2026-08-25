# PDF Photo Frame

An Android app that turns a PDF into a fullscreen digital picture frame: one page
at a time, auto-advancing on a timer, with configurable order and page exclusions.

## How to open it

1. Open Android Studio (Koala/2024.1 or newer recommended).
2. **File > Open**, select the `pdf-photo-frame` folder.
3. Let Gradle sync — Android Studio will fetch the Gradle wrapper and dependencies
   automatically (this project's wrapper files weren't pre-generated since this was
   built without network access; Android Studio creates them on first sync, or you
   can run `gradle wrapper` once if you have Gradle installed locally).
4. Run on a device/emulator running Android 8.0 (API 26) or higher.

## Architecture

- **`data/SettingsRepository`** — DataStore-backed persisted config: PDF URI,
  interval, order mode (sequential/random), excluded pages. Exposed as a `Flow`
  so both screens stay in sync automatically.
- **`data/PdfPageRepository`** — wraps Android's native `PdfRenderer`. Opens the
  PDF once and renders individual pages to `Bitmap` on demand, scaled to fit the
  target view/screen size. Shared as a singleton (via `PdfPhotoFrameApplication`)
  between the setup screen (thumbnails) and the slideshow screen (fullscreen).
- **`slideshow/SlideshowController`** — the state machine that decides which page
  is showing and when to advance. Sequential mode loops through eligible pages in
  order; random mode shuffles the eligible-page list and reshuffles each time it's
  exhausted, so every page gets shown once per "lap" rather than being picked
  independently (which tends to repeat some pages and skip others for a while).
- **`ui/setup/SetupScreen`** — pick a PDF, set the interval and order mode, and
  tap page thumbnails to exclude them from the rotation.
- **`ui/slideshow/SlideshowScreen`** — fullscreen display driven by
  `SlideshowController`. Hides the system status/navigation bars (immersive mode)
  and keeps the screen awake. Tapping anywhere reveals a small exit button that
  auto-hides again after a few seconds.

## Known limitations / good next steps

- No launcher icon is included — add one via Android Studio's
  **Image Asset Studio** (right-click `res` > New > Image Asset).
- Thumbnails in the setup screen render sequentially and aren't cached to disk,
  so very large PDFs will take a moment to fully populate the exclusion grid.
- No brightness/dimming control, no support for multiple PDFs or folders, no
  transition animation between pages, and orientation isn't locked — all
  reasonable follow-ups once the core flow feels right.
- The Gradle wrapper jar isn't included (this project was scaffolded without
  network access) — Android Studio will generate it on first open.
