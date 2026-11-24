# MythoStory

The myths of ancient Greece, to read and to wander through: **the characters** — gods, Titans, nymphs,
heroes, mortals, monsters —, **the groups** they form — the Argonauts, the Muses, the Seven against Thebes… — and
**the stories** they appear in. Every story is summarised, **sourced**
from the ancient texts (with a link to read them online) and linked to **Wikipedia, articles and
videos**. One click leads from a story to its characters, from a character to their family, their
groups and their other stories, from a group to its members, and from one episode of a cycle to the next. **In French and in English.**

One application, two platforms:

- **Desktop**: a local server (`mythostory.jar`, with a bundled Java runtime) serves the page on
  `http://127.0.0.1:8090/` and opens the browser on it. The browser is the window.
- **Android**: the **same server** runs inside the app on `127.0.0.1`, and a WebView shows the
  **same page**. Everything stays on the phone and works offline (the links lead to the web).

## Quick start

On Linux (x86_64), or on Windows with the same scripts as `scripts\windows\*.bat`; `curl`, `tar` and `unzip` at hand (on Windows, `jtools-1.6.1\install.bat --no-project` for the first step):

```bash
# once: JToolsFramework 1.6.1, the build's parent (jtools-parent), into ~/.m2 - it is not on Maven Central
curl -fLO https://github.com/j4ckk0/JToolsFramework/releases/download/v1.6.1/jtools-1.6.1.zip
unzip -q jtools-1.6.1.zip && jtools-1.6.1/install.sh --no-project
git clone https://github.com/j4ckk0/MythoStory.git && cd MythoStory
scripts/linux/setup.sh                    # once: the JDK, Maven, Node.js, Piper and its voices into external/
git config core.hooksPath .githooks       # once: scripts/linux/ci.sh runs before every push (git push --no-verify skips it)
scripts/linux/run.sh                      # build if needed, start, open the browser on http://127.0.0.1:8090/
```

For the Android app: `scripts/linux/setup.sh --android` (the Android SDK, about a gigabyte), then
`scripts/linux/run-android.sh` with a phone plugged in, or `scripts/linux/setup.sh --emulator` and
`scripts/linux/emulator.sh` for the emulator. On Windows, `setup.bat` deploys Piper and its voices too, but has not
been run on Windows yet: say what fails.

The scripts are [JToolsFramework](https://github.com/j4ckk0/JToolsFramework)'s devkit's, whose `jtools-parent` is
also the build's parent (the page's npm build, the one jar, the distributions' runtimes); MythoStory's own scripts
stand beside them (`setup.local`, `externals.local`, `run-android`, `emulator`, `check-links`,
`import-characters`; on Windows `android-env.bat` too). `jtools.conf` is what the devkit's scripts know of the
project, and `scripts/linux/jtools sync` refreshes them from the framework version it names.

## Running a distribution

Unpack `mythostory-<version>-linux.tar.gz` or `-windows.zip` (Packaging, below) anywhere, then run `./mythostory.sh`,
or `MythoStory.exe` (`mythostory.bat` for a console): nothing to install, the Java runtime, Piper and its voices are
inside. The browser opens on the page; MythoStory stops by itself once its last page is closed. `mythostory.ini`,
beside the jar, sets the port (`port=`, 8090 by default) and the interface (`host=`, 127.0.0.1: this computer only);
the command line wins over it (`--port N`, `--host H`, `--no-browser`, `--keep-alive`, `--piper DIR`,
`--voices DIR`, `--help`). The log is `~/.mythostory/logs/mythostory.0.log`; the voices' credits are in `VOICES.md`.

## Architecture

```
            ┌──────────────── frontend (Angular) ────────────────┐
            │  the only UI; talks to the backend's /api only     │
            └───────────────────────┬────────────────────────────┘
                                    │ HTTP on 127.0.0.1
            ┌───────────────────────┴────────────────────────────┐
            │  backend (Kotlin, Ktor CIO): /api + the page       │
            ├────────────────────────────────────────────────────┤
            │  core (pure Kotlin): the domain and its storage    │
            └───────────┬─────────────────────────┬──────────────┘
                        │                         │
           desktop: main + browser       android: Application + WebView
           (fat jar + runtime, Maven)        (APK, Gradle run by Maven)
```

| Module      | What it is                                                                                        | Built by |
|-------------|---------------------------------------------------------------------------------------------------|----------|
| `core/`     | the catalog: its model, its data files, their validation. No framework, Java 17 bytecode: Android runs it. | Maven |
| `frontend/` | the Angular page, packaged as a jar of `static/` resources.                                        | npm, run by Maven |
| `backend/`  | the HTTP server: Ktor on its CIO engine (pure Kotlin, so it runs on Android too); `/api` + the page. | Maven |
| `desktop/`  | the desktop program: `main`, the executable jar `desktop/target/mythostory.jar`, the distributions. | Maven |
| `android/`  | the Android app: starts the backend, shows the page in a WebView.                                  | Gradle (AGP), run by Maven |
| `importer/` | the characters imported from Wikidata into `core`'s data files: a tool, never shipped.             | Maven |

The choices behind this layout:

- **Ktor, not Spring Boot**: Spring Boot does not run on Android. Ktor does, so the desktop and the
  phone run **one** backend, not two.
- **One UI**: Angular in the desktop's browser and in Android's WebView. The page only ever calls
  relative `api/...` URLs, so it doesn't know which platform serves it (`/api/info` tells it).
- **Maven manages every library**, Android's included. Android apps can only be built by Gradle
  with the Android Gradle Plugin, so `android/pom.xml` resolves the dependencies into
  `android/target/libs`, then runs Gradle on them. Gradle declares no library of its own. The
  Kotlin version and the app version are read from `pom.xml`, and live only there.

### The API

| Method | Path           | Answer                                                                            |
|--------|----------------|-----------------------------------------------------------------------------------|
| GET    | `/api/info`    | `{name, version, platform, languages, voices}`: platform is `desktop` or `android` |
| GET    | `/api/catalog` | the whole catalog, every language in it: `{characters, groups, stories, cycles, eras, places}` |
| GET    | `/api/speech?lang=&voice=&text=` | a text of 1 to 1000 characters read aloud, as WAV, by that voice or the language's default (the desktop's Piper; `voices` in `/api/info` says which); 404 without a voice for the language, 400 for a bad text |
| GET    | `/api/licences` | `{embedded, libraries}`: what the help page lists                              |
| GET    | `/api/session` | a page's session: a WebSocket, its first message the session's id                 |
| POST   | `/api/session/{id}/closed` | the page has gone (a beacon): the desktop stops once its last page has   |

The API answers the page only: a request a browser marks as sent from another site (`Sec-Fetch-Site`), or whose
`Origin` is not the server's, gets 403, and so does any request naming another host than this computer while the
server listens on loopback (DNS rebinding). Requests without those headers (curl, the scripts) pass.

The page's own addresses — the Angular routes, which any other path serves `index.html` for: `/characters`
(`?kind=`, `&letter=`), `/characters/:id`, `/groups`, `/groups/:id`, `/stories`, `/stories/:id`, `/timeline`, `/map`
(`?story=`, `?place=`), `/search?q=`, `/settings`, `/help`. Under `/api` or for a missing file (a dot in its name),
404 instead, and `index.html` is never cached.

The catalog (about 1.7 MB, 300 kB gzipped) is read-only: the page loads it once, builds its own
indexes (a character's stories, children and groups, a group's stories, a story's cycles and neighbours) and switches language
without asking the server again. `ContractTest` (backend) checks that the TypeScript interfaces of
`catalog.ts` and `help.ts` have the Kotlin classes' fields, the ones the server may leave out optional.

## The catalog

The data lives in `core/src/main/resources/data/`, in the core jar — so on the desktop and on
Android alike:

```
data/index.json               the files below, in order (CatalogTest fails on a file it does not list)
data/characters/<kinds>.json  the curated characters, one file per kind (plural names): primordials, titans,
                              olympians, deities, nymphs, heroes, mortals, creatures
data/characters/wikidata/     the characters imported from Wikidata, <kind>.json (singular): never edited by hand,
                              scripts/linux/import-characters.sh rewrites them (below)
data/groups.json              the groups: the Argonauts, the Muses… and their members, from the ancient lists
data/stories/<cycle>.json     the stories, one file per cycle
data/cycles.json              the cycles: sequences of stories, in their order
data/eras.json                the eras of the timeline, in their order
data/places.json              the places of the map: coordinates from Wikidata
```

The records refer to each other by **id**, a slug (`zeus`, `labours-of-heracles`), which is also the
page's URL (`/characters/zeus`, `/groups/argonauts`). Each relation is said **once** — a character's `parents`,
a group's `members`, a story's `characters` and `groups`, a cycle's `stories`, a story's `related` — and
everything else is derived: children, a character's stories and groups, a group's stories, the previous and
next story of a cycle.

```jsonc
// a character
{ "id": "perseus", "name": {"en": "Perseus", "fr": "Persée"}, "kind": "hero", "wikidata": "Q130832",
  "description": {"en": "…", "fr": "…"}, "greek": "Περσεύς", "roman": null,
  "parents": ["zeus", "danae"],
  "links": [{"kind": "wikipedia", "lang": "en", "title": "Wikipedia — Perseus", "url": "https://en.wikipedia.org/wiki/Perseus"}] }

// a story: its summary in our own words (paragraphs separated by a blank line), at least one ancient source
{ "id": "perseus-and-medusa", "title": {"en": "…", "fr": "…"}, "summary": {"en": "…", "fr": "…"},
  "characters": ["perseus", "medusa"],
  "sources": [{"author": {"en": "Apollodorus", "fr": "Apollodore"}, "work": {"en": "Library", "fr": "Bibliothèque"},
               "passage": "2.4.1–2.4.4",
               "url": {"en": "https://www.theoi.com/Text/Apollodorus2.html", "fr": "https://fr.wikisource.org/wiki/…"}}],
  "links": [{"kind": "video", "lang": "en", "title": "TED-Ed — The myth of Medusa", "url": "https://www.youtube.com/watch?v=O18-FA83BaM"}],
  "related": ["bellerophon-and-the-chimera"],
  "era": "heroes", "generation": 4, "groups": ["gorgons", "graeae"], "places": ["argos", "seriphos", "larissa"] }
```

A field the parser does not know — misspelt — is refused, as is a missing required one (`era`, a story's).

A story also says **when** and **where** it happens: its `era` (the timeline's section), its
`generation` — how many generations before the Trojan War, as the genealogies count them; absent
when the tradition does not say —, a `dating` when ancient chronographers gave one, its `places`, and
`route: true` when those places are a journey, in its order.

```jsonc
// data/groups.json: a group, its members in the order of the ancient list it comes from, which its sources cite
{ "id": "argonauts", "name": {"en": "Argonauts", "fr": "Argonautes"}, "description": {"en": "…", "fr": "…"},
  "members": ["jason", "tiphys", "orpheus", "zetes", "calais", "castor", "pollux", "…"], "wikidata": "Q165510",
  "sources": [{"author": {"en": "Apollodorus", "fr": "Apollodore"}, "work": {"en": "Library", "fr": "Bibliothèque"}, "passage": "1.9.16", "url": {…}}],
  "links": [{"kind": "wikipedia", "lang": "en", "title": "Wikipedia — Argonauts", "url": "https://en.wikipedia.org/wiki/Argonauts"}] }
```

A story names the groups it involves in its `groups` (the voyage of the Argo: the Argonauts, the Boreads, the
Harpies); a member may belong to several groups (Castor: an Argonaut, a hunter of Calydon, one of the Dioscuri).

```jsonc
// data/places.json: a place, with the Wikidata item its coordinates come from; identification marks a mythical place
{ "id": "lotus-eaters", "name": {"en": "Land of the Lotus-eaters", "fr": "Pays des Lotophages"}, "lat": 33.7837, "lon": 10.8833,
  "wikidata": "Q188938", "identification": {"en": "Identified with the island of Djerba since antiquity.", "fr": "…"} }
```

Link kinds: `wikipedia`, `article`, `video`. Each link says its **language**; the page shows the
reader's language first and marks the others.

**Checks.** `Catalog.problems()` refuses, among others (it lists every rule): a duplicate id or Wikidata item, an id
that is not a slug, a dangling reference (to a character, a story, an era, a place, a group), a place no story uses,
a story without a source or a character, in no cycle, related to itself, or listing an entry twice (a place too,
unless its places are a route), a character that is its own parent, a group of fewer than two members or listing
one twice, a generation outside 0–20, a place off the Earth, a text missing one of the languages or in another, a
link or source that is not http(s), a link without a title; the build runs it (`CatalogTest`), and the server would
not start. **The links** are
checked by `scripts/linux/check-links.sh` (`scripts\windows\check-links.bat`): every URL must answer — the videos
through YouTube's oEmbed, which answers only for videos that exist, the Wikipedia articles through
Wikipedia's API, fifty to a request. It needs the network, so the build
never runs it: run it after editing the data, and now and then, since pages move.

**Adding** a character, a group or a story: edit the JSON of its kind, `groups.json` or its cycle (a new file: add it to
`index.json`), with every text in English and French, then `scripts/linux/build.sh` (the checks) and
`scripts/linux/check-links.sh`. Summaries are written in our own words, never copied: the sources are
cited, with the passage.

### The characters from Wikidata

`scripts/linux/import-characters.sh` (`.bat`) imports **every character of Greek mythology Wikidata knows
with a Wikipedia article in English or French** — the instances of "mythological Greek character"
(Q22988604) and its subclasses —, `--all` even those without (names, little else). It needs the
network, takes half a minute, and rewrites `data/characters/wikidata/` whole; the curated characters
are never touched, and a Wikidata item one of them already is (their `wikidata` field, or their English Wikipedia
article) is not imported. A character keeps the id the previous import gave it, its page's address.
Each imported character gets:

- **its names** in English and French from Wikidata's labels (one language standing in for the
  other when Wikidata has only one), and its **Greek** name;
- **its kind**, from its Wikidata classes (`Kinds.kt`: primordial deity, Titan, Olympian, nymph,
  centaur, Giant…, deity, demigod (→ hero), mythical creature — the most specific first); a character with no
  such class is a **hero** (a demigod) when one parent is a god and the other is not or is unknown, a **deity**
  when both are, else a **mortal**;
- **its parents** (Wikidata's father and mother) among the catalog's characters, so that the curated
  ones gain their children too (Zeus: about a hundred);
- **a description**: Wikidata's, unless it says no more than "Greek mythological figure", in which case
  one made from the parents — "Daughter of Salmoneus and Alcidice in Greek mythology." /
  « Fille de Salmonée et d’Alcidice dans la mythologie grecque. »;
- **links**: Wikipedia in both languages when the articles exist, and the **Theoi Project**'s page
  Wikidata names, once the importer has checked that it answers (some ids predate Theoi's
  reorganisation).

The catalog must be sound with what is imported before anything is written. Then
`scripts/linux/check-links.sh` and `scripts/linux/build.sh`. Wikidata knows characters the import leaves out: those without a Wikipedia article of their own — Castor and
Pollux share one, as do Zetes and Calais — and those Wikidata files under classes outside "mythological Greek
character" (the Harpy Celaeno, the Siren Ligeia). The ones the groups need are curated, with their `wikidata`, so
that a later import never brings them twice. A group's members must stay in the catalog: an import that dropped one
would be refused.

Many characters share a name (four Clymenes, three
Thalias…): their ids then carry their Wikidata item (`clymene-q2980439`), their descriptions tell them
apart, and so does the description shown when hovering their name.

Wikidata's data is CC0: no attribution required — each imported character links to its Wikidata item.

### The catalog today

**3,053 characters** — 166 curated, 2,887 from Wikidata —, **30 groups** of them (433 memberships, 411
characters: 44
Argonauts, the Muses, the Nereids, the Seven against Thebes, the suitors of Penelope…) and **52
stories** in 7 cycles: the birth
of the gods (5), Prometheus and the first humans (3), gods among mortals (7), the age of heroes (8),
Thebes (1), **the Trojan War in full** (15, from the golden apple through the Iliad to the fall of the
city) and **the Odyssey in full** (13, in the order it happened); 73 places on the map; **5,483
links**, all checked. The catalog weighs about 1.7 MB, about 300 kB gzipped as the
server sends it. English translations of the sources come from the Theoi Classical Texts Library, the
MIT Internet Classics Archive and the University of Chicago's LacusCurtius; French ones from
Wikisource (Leconte de Lisle, Nisard, Clavier).

The page has a timeline, a map and family trees (below); it lists the groups by what most of their members are
(heroes and mortals, gods and nymphs, monsters), and the characters by kind and, when there are more than 150, one initial at a time
(`/characters?kind=mortal&letter=t`); the search shows the first hundred matches, names starting with
the query first.

## The timeline, the map and the family trees

**The timeline** (`/timeline`) lays the stories out in the mythic chronology: the eras in their
order (the origins, the reign of Cronus, the war for heaven, the first humans, the age of heroes, the
Trojan War, the returns), and in each the stories by generation before the war — Phaethon six
generations before it, Perseus four, Europa three, Bellerophon and Oedipus two, Heracles, Jason,
Theseus and Orpheus one — then those the tradition does not place. The generations come from the
genealogies (Perseus is Heracles' great-grandfather; Heracles' son Tlepolemus fought at Troy), which
the myths do not always agree on; the dates shown are the ancient chronographers' — the flood of
Deucalion in 1529/1528 BC by the Parian Chronicle, the fall of Troy in 1184/1183 BC by Eratosthenes —,
never ours. The cards take their cycle's colour; each leads to its story and its place on the map.

**The map** (`/map`; `?story=` draws one in front and zooms on it, `?place=` tells what happens
there) puts the places of the stories on the Mediterranean and the Black Sea, the journeys as
routes in their cycle's colour: Heracles from Tiryns to Gades and the Hesperides, the Argo from
Iolcus to Colchis, Odysseus from Troy to the Cyclopes. A hollow dot is a mythical place, or one whose
location was disputed: its panel says who identified it with what. Zoom with the wheel or the
buttons, pan by dragging; labels are laid out so as not to overlap. Each story page has its own inset.

The map is drawn in SVG from `frontend/public/map/land.json`, so that it works offline, on Android
too: Natural Earth's 1:10m land (public domain), clipped, the islands smaller than 15 km² left out,
simplified with mapshaper:

```bash
npx mapshaper@0.6 -i ne_10m_land.geojson -clip bbox=-22,18,62,56 -explode -filter 'this.area > 15000000' \
  -simplify 15% keep-shapes -o precision=0.01 format=geojson land.json
```

**The family tree**, on each character's page: their ancestors over two generations (further up, the
gods' families repeat themselves), and their children grouped by the other parent — Zeus's hundred,
with Hera, with Leto, with Maia… —, each child with how many children of their own. Every name leads
to that character's tree.

## Listening to the stories

Each story page has a **speaker button**: it reads the title, then the summary, aloud in the reader's
language — pause, resume where it stopped, stop —, the paragraph being read highlighted. It stops when
the page goes or the language changes, and is disabled, saying so, where the device has no voice for
the language. The *Stories* page reads all the stories, or a cycle's, one after the other, a player at
the bottom of every page.

**Settings** (`/settings`) choose the voice of each language among the reader's — Piper's, or the
browser's —, each one heard first (the Iliad's first line); the browser remembers the choice
(`localStorage`), and a voice no longer there reads with the default.

- **On the desktop**, the server reads aloud itself, with **Piper**: neural voices, offline, the
  same in every browser on every system — in French Siwis (the default), Jessica and Pierre; in
  English Alba (the default), Cori and Northern. The models are `externals.conf`'s `VOICE_*`;
  [`voices.json`](desktop/src/main/resources/voices.json) names them and their speakers for the page,
  each language's first the default. `/api/speech?lang=fr&voice=fr-pierre&text=…` answers as WAV in a
  fraction of a second (a process per model, started on its first text and kept; a cache for what was
  said already); the page sends a few sentences at a time — Piper keeps the intonation going across
  them —, fetching the next ones while the current ones play. Piper and its voices are externals
  (`scripts/linux/setup.sh` deploys them) and the distributions carry them, with their credits in
  `VOICES.md` (`desktop/src/dist/files/`) and the *Help* page (Piper: MIT; the voices: CC BY 4.0, CC BY-SA 4.0, public domain).
  The program finds them next to its jar, or where `--piper DIR --voices DIR` say.
- **Without Piper**, the browser's own speech synthesis (the Web Speech API) with the system's voices,
  local ones and the locale's first (fr-FR, en-GB): fine on Windows and macOS; on Linux, browsers take
  theirs from speech-dispatcher, whose espeak-ng sounds robotic (and a snap Chromium reaches none).
- **On Android**, the WebView has no speech synthesis: the app lends the page Android's
  text-to-speech through a bridge (`SpeechBridge.kt`, `window.MythoStorySpeech`), with the voices
  installed on the phone (Settings › Text-to-speech).

## i18n

French and English, everywhere: everything about it is named **i18n**.

- **The catalog's texts** are `I18nText` — `{"en": …, "fr": …}` — and the languages are
  `I18n.LANGUAGES` in `core` (`I18n.kt`), which the checks require in every text.
- **The page's texts** are i18n messages: `frontend/src/app/i18n/en.ts` holds every key,
  `fr.ts` must hold the same (the compiler checks it), shown by the `i18n` pipe
  (`{{ 'nav.characters' | i18n }}`); catalog texts by the `i18nText` pipe
  (`{{ character.name | i18nText }}`). `I18nService` holds the language: the reader's last
  choice, else the browser's (the phone's on Android), else English; the header's list of languages, each named
  in itself (English, Français), switches it, instantly.
- **The Android app's own strings** (its name, its error) are Android resources: `res/values/`
  (English) and `res/values-fr/`.

Adding a language: add it to `I18n.LANGUAGES` and `I18N_LANGUAGES` (`i18n.service.ts`), write its
messages file, and its texts in every data file — the checks list what is missing.

## Externals

Everything the build and the distributions need lives in `external/` at the project root, not under
source control, under **generic names** that the scripts and the build are the only ones to know:

```
external/jdk/                 the scripts' JAVA_HOME (Temurin 25); its java first in their PATH
external/maven/               its mvn first in their PATH
external/node/                Node.js and npm, which Maven runs to build the frontend
external/jdk-linux.tar.gz     this machine's JDK again, as its archive: the jlink that links the distributions' runtimes
external/jmods-linux.tar.gz   the jmods the Linux distribution's runtime is linked from, external/jmods-windows.zip the Windows one's
external/piper/               Piper, the speech synthesis the desktop server reads the stories aloud with
external/piper-linux.tar.gz   Piper for the Linux distribution, external/piper-windows.zip for the Windows one
external/voices/<model>.onnx  Piper's voices (externals.conf's VOICE_*), with their <model>.onnx.json
external/gradle/              (--android) Gradle, which Maven runs to build the APK
external/android-sdk/         (--android) the Android SDK: command-line tools, platform, build-tools, platform-tools;
                              (--emulator) the emulator and its system image too
external/android-avd/         the emulator's virtual phone, which scripts/linux/emulator.sh creates
external/downloads/           the archives as downloaded, kept so that a second run downloads nothing
```

`scripts/linux/setup.sh` (needs `curl`, `tar`, `unzip`) and `scripts\windows\setup.bat` (Windows 10 1803 and later:
`curl` and `tar` are there) download and deploy what is missing; `--force` deploys everything again. The JDK, Maven,
Node.js and the jmods are the devkit's to deploy; Piper, the voices and the Android externals are MythoStory's own,
`scripts/linux/setup.local.sh`, which the devkit's setup runs after its own (on Windows `setup.local.bat`, which
deploys Piper and its voices too but has not been run on Windows yet). **`--android` adds Gradle and the Android SDK** (about a gigabyte), and
asks you to accept the Android SDK licences. **`--emulator`** does the same and adds the Android emulator and the
system image of its virtual phone (`ANDROID_EMULATOR_PACKAGES`, 2 to 3 GB more; a Google Play image, which carries
Google's text-to-speech for the speaker button).

**The versions live in [`externals.conf`](externals.conf) and nowhere else**: one URL per external
and per OS — but for the Android Gradle Plugin's, in `android/settings.gradle.kts`. **A new version is a line
changed there, then setup**: each external remembers the URL it came from (`external/.<name>.url`), and one deployed
from another is deployed again; until then the scripts say it is out of date. `JTOOLS_DOWNLOADS=<dir>` in the
environment shares one downloads folder between projects; `scripts/linux/jtools` unpacks the devkit into
`external/jtools-devkit-<version>/`. Set `MIRROR=` there (or in the environment) to the folder of a local Artifactory holding
the same file names, and everything is fetched as `<MIRROR>/<file name>` instead of from the internet.
A mirror that wants **authentication** gets it from `MIRROR_TOKEN`, sent in the header
`MIRROR_TOKEN_HEADER` (`Authorization: Bearer` by default, for an Artifactory access token;
`X-JFrog-Art-Api:` for an API key), or from `MIRROR_USER` / `MIRROR_PASSWORD` (basic authentication).
Keep the secrets out of `externals.conf`: put them, with the mirror, in `externals.local.conf` next to
it (same syntax, not under source control) or in the environment. The environment wins over the
local file, which wins over `externals.conf`.

`scripts/linux/externals.sh` / `externals.bat`, called by every script, check that setup has been run
(everything it deploys is there, `scripts/linux/externals.local.sh` saying what of MythoStory's own) and offer to run
it when something is missing. At a terminal they ask; the CI or a hook stops with the way out instead. They then put
the externals first in `PATH`.

Libraries (Maven Central, Google's Maven, npm) are not externals: Maven, Gradle and npm fetch them
into their usual caches. To fetch them from an Artifactory too, point `~/.m2/settings.xml`, an
`.npmrc` and `~/.gradle/init.d/` at it.

## Building

```bash
scripts/linux/build.sh                     # clean build, every test: desktop/target/mythostory.jar (+ the APK, see below)
scripts/linux/build.sh -DskipTests         # extra arguments are passed to mvn
scripts/linux/build.sh -P-android          # the desktop alone, even with the Android SDK there
scripts/linux/ci.sh                        # the CI: quiet, npm ci (exactly package-lock.json)
scripts/linux/check-links.sh               # every URL of the catalog answers (network; not part of the build)
scripts/linux/import-characters.sh         # the characters from Wikidata (network; see "The characters from Wikidata")
```

The Android app is built **as soon as `scripts/linux/setup.sh --android` has run**: Maven's `android`
profile switches itself on when `external/android-sdk/platforms` exists. Plain `mvn package` does
the same, with the externals first in `PATH` (`. scripts/linux/externals.sh`); `mvn package -P-android` leaves it
out, and `BUILD_GOALS='clean package -P-android' scripts/linux/run.sh` runs quicker without it.

## Running

```bash
scripts/linux/run.sh                            # build if a source is newer than the jar, then run
scripts/linux/run.sh --port 9090 --no-browser   # the program's options: --port, --host, --no-browser, --keep-alive, --piper, --voices, --help
scripts/linux/run.sh --rebuild
scripts/linux/run-android.sh                    # install and start the app on the device or emulator adb sees
scripts/linux/emulator.sh                       # the same in the Android emulator, which it starts first (below)
scripts/linux/stop.sh                           # stop every MythoStory server on this machine (scripts\windows\stop.bat: every one, no --port; it needs wmic)
scripts/linux/stop.sh --port 9090               # only the one on that port
```

The desktop listens on `127.0.0.1` only (`--host 0.0.0.0` for the network), on port 8090 (MythoStory's own, not the 8080 of so many servers) unless `mythostory.ini`,
beside a distribution's jar (`port=`, `host=`), names another; the command line wins over it. **It stops by itself
when its last page is closed**, like a program whose last window is closed: each page holds a WebSocket
to the server (`/api/session`) and, as it goes away, closes it and says so; the server waits five seconds — a
reload, a tab closed while another opens — and stops. Should the browser crash instead, the socket
closes and the server notices at once. A WebSocket rather than a stream of server-sent events: browsers allow six
HTTP connections per host, all tabs together, and a stream held by every tab left the seventh tab blank. It never stops before a first page has opened, and
`--keep-alive` keeps it serving (Ctrl+C then). The Android app's server lives as long as the app.
Launched again while it runs, it opens the running one's page and exits; should another program hold the port, it
says so and exits (3) — in a dialog too when there is no console to say it in (`MythoStory.exe`). A command line it
does not understand exits with 2. The log: `~/.mythostory/logs/mythostory.0.log`.

Run from the repository, the program reads the stories aloud with `external/piper` and `external/voices`; a
distribution's are beside its jar.

### In the Android emulator

```bash
scripts/linux/setup.sh --emulator          # once: the Android SDK, its licences, the emulator and its system image
scripts/linux/emulator.sh                  # start the virtual phone (created on the first run), build, install, start the app
scripts/linux/emulator.sh --rebuild        # force a rebuild of the app first (run-android.sh's option)
scripts/linux/emulator.sh --wipe           # the phone as new, its data wiped as it starts
scripts/linux/emulator.sh --no-window      # the phone without its window
scripts/linux/emulator.sh --stop           # stop the phone
```

The virtual phone, `mythostory` — an `ANDROID_AVD_DEVICE` (a Pixel 7) on the system image of
`ANDROID_EMULATOR_PACKAGES` —, lives in `external/android-avd/`. The emulator keeps running after the script: run it
again after a change, and it reinstalls the app on the phone already started. It finds its phone by name, so a real
phone plugged in at the same time is left alone (`ANDROID_SERIAL`). On Linux it needs KVM (`/dev/kvm`, the `kvm`
group); its output goes to `tmp/emulator.log`.

### Working on the frontend

Angular's dev server reloads the page on every change. It forwards `/api` to the backend
(`frontend/proxy.conf.json`):

```bash
scripts/linux/run.sh --no-browser --keep-alive   # the backend on :8090, whatever the dev server's reloads
cd frontend && PATH="$PWD/../external/node/bin:$PATH" npm start   # the page on http://localhost:4200/
```

`npm start` needs `frontend/node_modules`, which a first `scripts/linux/build.sh` installs.

## Packaging

```bash
scripts/linux/package.sh                 # or scripts\windows\package.bat on Windows
scripts/linux/package.sh --no-android    # the desktop's distributions alone (jtools.conf's PACKAGE_OPTION)
```

Into `releases/` (not under source control):

| File                                    | What it holds                                         |
|-----------------------------------------|-------------------------------------------------------|
| `mythostory-<version>-linux.tar.gz`     | `mythostory.jar`, `mythostory.sh`, `mythostory.ini`, `jre/`, `piper/`, `voices/`, `VOICES.md`, this README, `LICENSE`, `NOTICE` |
| `mythostory-<version>-windows.zip`      | `mythostory.jar`, `MythoStory.exe` (no console), `mythostory.bat`, `mythostory.ini`, `jre/`, `piper/`, `voices/`, `VOICES.md`, this README, `LICENSE`, `NOTICE` |
| `mythostory-<version>-android.apk`      | the app (when the Android SDK is there)               |

`jre/` is a runtime jlink links from Temurin's jmods with only the modules MythoStory uses (about 66 MB, against
200 MB for a whole JRE); the voices are most of the rest. jtools-parent's `dist` profile links it and makes the
launchers; MythoStory's own (`desktop/pom.xml`, `desktop/src/assembly/`) then makes the archives again with Piper and
the voices. The previous versions' archives and APKs are removed; anything else in `releases/` is left alone. The
script checks the Linux and Windows archives — the jar, the launchers, the `.ini`, the runtime, the page inside the
jar —; the APK is copied as Gradle made it.

**Releasing**: `scripts/linux/release.sh` publishes the version the root pom names, once its tag `v<version>` exists
and `package` has made its archives, with `CHANGELOG.md`'s `## <version>` section as its notes. `jtools.conf` names
no `RELEASE_GITHUB`: the release is local (`releases/mythostory-<version>.sha256` and `-notes.md`). Publishing the
archives anywhere is a new distribution: check first what they carry and under which licences (`NOTICE`).

**Licences**: the page's *Help* (`/help`, `/api/licences`) lists what MythoStory carries and under
which licence:

- the embedded software — the Temurin runtime (GPL-2.0 with the Classpath exception; its own notices in
  `jre/legal/`), Piper and what it runs on, the voices — from
  [`desktop/src/main/resources/licences/embedded.json`](desktop/src/main/resources/licences/embedded.json),
  kept by hand; `EmbeddedLicencesTest` fails when `externals.conf` deploys other jmods, Piper or voice;
- the server's libraries, from their poms: `license-maven-plugin` writes `licences/THIRD-PARTY.txt` into
  the jar at every build, and among the Android app's resources (`android/pom.xml`, `build.gradle.kts`), which also
  keeps the libraries' own `META-INF/LICENSE*` and `NOTICE*`;
- MythoStory's own `LICENSE` and `NOTICE`, copied into the page as `licences/LICENSE.txt` and `licences/NOTICE.txt`
  (`frontend/pom.xml`), on both platforms;
- the page's libraries: the Angular build's `3rdpartylicenses.txt`, served as
  `licences/3rdpartylicenses.txt`.

**Signing the APK**: put the release key's coordinates in `android/keystore.properties` (not under
source control: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it, the APK is
signed with the debug key: you can install it for testing, but you can't publish it.

**The version** is `<revision>` in [`pom.xml`](pom.xml), said once: the jar, the API's `/api/info`,
the archives' names, and the APK's `versionName` and `versionCode` (`a.b.c` → `a·10000 + b·100 + c`)
all come from it.

## Licence

MythoStory — its code, its data and the summaries written for it — is licensed under the
[Apache License, Version 2.0](LICENSE). [`NOTICE`](NOTICE) lists everything it holds or ships that it did not
write, where it comes from and on what terms: the characters from Wikidata (CC0), the coastlines from Natural
Earth (public domain), JToolsFramework's devkit scripts (Apache-2.0), and, in the builds, the page's and the
server's libraries, the Java runtime, Piper and its voices. The Linux and Windows archives carry both files, and
`piper/licences/` the licence texts of Piper's components, which Piper's own archive leaves out, with where eSpeak
NG's source code is ([`desktop/src/dist/piper-licences/`](desktop/src/dist/piper-licences/README.txt)).

Android, Windows, Linux, Java, Wikipedia, Wikidata, YouTube, GitHub and the other names this README uses are
trademarks of their owners, named only to say what MythoStory runs on, is built with or links to; MythoStory is not
affiliated with any of them. `NOTICE` names the owners.
