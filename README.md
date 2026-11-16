# Web2Speech

A small Android app that reads a web page aloud. In your browser (Fennec, Firefox,
Chrome, etc.) choose **Share**, then **Web2Speech**. The app fetches the page, strips it
to the article text (no navigation, ads or footers) and speaks it through the phone's
own text-to-speech engine. It needs no account and sends nothing anywhere except the
page request itself.

Built for GrapheneOS, but it runs on any Android 10 or later.

## Install

1. Download `Web2Speech-0.2.0.apk` from the Releases page.
2. Open it on the phone and allow installing from that source when asked.
3. Open Web2Speech once and allow notifications (needed for the playback controls).

## Use

- From a browser: Share, then Web2Speech. Reading starts in the background.
- From any app (e.g. a reply in the Claude app): Share the text itself. If the shared
  text is not just a title and a link, it is read as written, with Markdown symbols
  removed and code blocks skipped. Nothing is fetched.
- Sharing again while it is reading adds the page to a queue.
- Notification controls: back, play/pause, next, stop. A part is a few sentences.
- The app remembers where you stopped in each page.
- Open the app for speed, pitch, voice, the number of sentences per chunk, and to
  paste an address by hand.

## How text is chunked

Text is sent to the speech engine in whole sentences, up to 3 sentences or 600
characters at a time (both adjustable), never crossing a paragraph break and never
exceeding the engine's own limit.

## Limits

- It re-fetches the page, so paywalled or logged-in pages and pages that build their
  content with JavaScript may read poorly or not at all.
- Extraction uses Readability4J (Mozilla Readability port); unusual layouts may read
  badly.

## Build

```
./gradlew testDebugUnitTest assembleDebug
```

Release builds are signed when a `keystore.properties` file (not in this repo) points at
a keystore.
