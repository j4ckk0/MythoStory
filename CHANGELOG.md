# Changelog

What each version brings, for whoever installs it. `scripts/linux/release.sh` publishes the version the root pom
names with its section here as the release's notes (JToolsFramework's devkit).

## 0.1.0

The first version: a guide to Greek mythology, in English and in French, on the desktop and on Android.

- **The characters** — 3,053 of them, gods, Titans, nymphs, heroes, mortals and creatures, most imported from
  Wikidata —, their families drawn as trees, and **the groups** they form (the Argonauts, the Muses, the Seven
  against Thebes…), their members from the ancient lists.
- **The stories**, 52 in 7 cycles, each summarised in our own words, sourced from the ancient texts with a link to
  read them online, and linked to Wikipedia, articles and videos; a **timeline** by generation and a **map** of
  their places.
- **Reading aloud**: on the desktop, neural voices that work offline (Piper), chosen in the settings, each heard
  first; on Android, the phone's own voices.
- **One program, two platforms**: on the desktop, a local server and the browser as its window, which stops once its
  last page is closed (`--keep-alive` keeps it serving); on Android, the same server inside the app.
- The desktop listens on port **8090** (`--port`, or `port=` in `mythostory.ini`). Launched again, it opens the
  running one's page; its log is `~/.mythostory/logs/mythostory.0.log`.
- Licensed under the Apache License 2.0; `NOTICE` lists what it carries that it did not write.
