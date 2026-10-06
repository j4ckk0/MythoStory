# MythoStory

One application, two platforms:

- **Desktop**: a local server (`mythostory.jar`, with a bundled JRE) serves the page on
  `http://127.0.0.1:8080/` and opens the browser on it. The browser is the window.
- **Android**: the **same server** runs inside the app on `127.0.0.1`, and a WebView shows the
  **same page**. Everything stays on the phone and works offline.

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
           (fat jar + JRE, Maven)        (APK, Gradle run by Maven)
```

| Module      | What it is                                                                                        | Built by |
|-------------|---------------------------------------------------------------------------------------------------|----------|
| `core/`     | the domain: stories, their storage (a JSON file). No framework, Java 17 bytecode: Android runs it. | Maven |
| `frontend/` | the Angular page, packaged as a jar of `static/` resources.                                        | npm, run by Maven |
| `backend/`  | the HTTP server: Ktor on its CIO engine (pure Kotlin, so it runs on Android too); `/api` + the page. | Maven |
| `desktop/`  | the desktop program: `main`, the executable jar `desktop/target/mythostory.jar`, the distributions. | Maven |
| `android/`  | the Android app: starts the backend, shows the page in a WebView.                                  | Gradle (AGP), run by Maven |

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

| Method | Path           | Body / answer                                              |
|--------|----------------|------------------------------------------------------------|
| GET    | `/api/info`    | `{name, version, platform}`, where platform is `desktop` or `android` |
| GET    | `/api/stories` | `[{id, title, summary}]`                                   |
| POST   | `/api/stories` | `{title, summary}` → 201 and the story; 400 on a blank title |

Any other path serves the page (`index.html` for the Angular routes).

## Quick start

```bash
scripts/setup.sh              # once: the JDK, Maven, Node.js and the JREs into external/
scripts/run.sh                # build if needed, start, open the browser
```

On Windows, the same scripts are `scripts\*.bat`.

## Externals

Everything the build and the distributions need lives in `external/` at the project root, not under
source control, under **generic names** that the scripts and the build are the only ones to know:

```
external/jdk/                 the scripts' JAVA_HOME (Temurin 25); its java first in their PATH
external/maven/               its mvn first in their PATH
external/node/                Node.js and npm, which Maven runs to build the frontend
external/jre-linux.tar.gz     bundled by scripts/package.sh in the Linux distribution
external/jre-windows.zip      bundled in the Windows distribution
external/gradle/              (--android) Gradle, which Maven runs to build the APK
external/android-sdk/         (--android) the Android SDK: command-line tools, platform, build-tools, platform-tools
external/downloads/           the archives as downloaded, kept so that a second run downloads nothing
```

`scripts/setup.sh` (Linux: needs `curl`, `tar`, `unzip`) and `scripts\setup.bat` (Windows 10 1803
and later: `curl` and `tar` are there) download and deploy what is missing. `--force` downloads and
deploys everything again. **`--android` adds Gradle and the Android SDK** (about a gigabyte), and
asks you to accept the Android SDK licences.

**The versions live in [`externals.conf`](externals.conf) and nowhere else**: one URL per external
and per OS. Set `MIRROR=` there (or in the environment) to the folder of a local Artifactory holding
the same file names, and everything is fetched as `<MIRROR>/<file name>` instead of from the internet.
A mirror that wants **authentication** gets it from `MIRROR_TOKEN`, sent in the header
`MIRROR_TOKEN_HEADER` (`Authorization: Bearer` by default, for an Artifactory access token;
`X-JFrog-Art-Api:` for an API key), or from `MIRROR_USER` / `MIRROR_PASSWORD` (basic authentication).
Keep the secrets out of `externals.conf`: put them, with the mirror, in `externals.local.conf` next to
it (same syntax, not under source control) or in the environment. The environment wins over the
local file, which wins over `externals.conf`.

`scripts/externals.sh` / `externals.bat`, called by every script, check that setup has been run
(everything it deploys is there) and offer to run it when something is missing. At a terminal they
ask; the CI or a hook stops with the way out instead. They then put the externals first in `PATH`.

Libraries (Maven Central, Google's Maven, npm) are not externals: Maven, Gradle and npm fetch them
into their usual caches. To fetch them from an Artifactory too, point `~/.m2/settings.xml`, an
`.npmrc` and `~/.gradle/init.d/` at it.

## Building

```bash
scripts/build.sh                  # clean build, every test: desktop/target/mythostory.jar (+ the APK, see below)
scripts/build.sh -DskipTests      # extra arguments are passed to mvn
scripts/build.sh --no-android     # the desktop alone, even with the Android SDK there
scripts/ci.sh                     # the CI: quiet, npm ci (exactly package-lock.json)
```

The Android app is built **as soon as `scripts/setup.sh --android` has run**: Maven's `android`
profile switches itself on when `external/android-sdk/platforms` exists. Plain `mvn package` does
the same; `mvn package -P-android` leaves it out.

## Running

```bash
scripts/run.sh                                  # build if a source is newer than the jar, then run
scripts/run.sh --port 9090 --no-browser         # the program's options: --port, --host, --data, --no-browser, --help
scripts/run.sh --rebuild
scripts/run.sh --android                        # install and start the app on the device or emulator adb sees
```

The desktop keeps its stories in `~/.mythostory/stories.json` (`--data DIR` elsewhere), and listens
on `127.0.0.1` only (`--host 0.0.0.0` for the network). The Android app keeps them in its private
storage.

### Working on the frontend

Angular's dev server reloads the page on every change. It forwards `/api` to the backend
(`frontend/proxy.conf.json`):

```bash
scripts/run.sh --no-browser                     # the backend on :8080
cd frontend && PATH="$PWD/../external/node/bin:$PATH" npm start   # the page on http://localhost:4200/
```

## Packaging

```bash
scripts/package.sh                 # or scripts\package.bat on Windows
scripts/package.sh --no-android    # the desktop's distributions alone
```

Into `releases/` (not under source control):

| File                                    | What it holds                                         |
|-----------------------------------------|-------------------------------------------------------|
| `mythostory-<version>-linux.tar.gz`     | `mythostory.jar`, `mythostory.sh`, `jre/`, this README |
| `mythostory-<version>-windows.zip`      | `mythostory.jar`, `mythostory.bat`, `jre/`, this README |
| `mythostory-<version>-android.apk`      | the app (when the Android SDK is there)               |

The previous versions' releases are removed; anything else in `releases/` is left alone. The script
checks every archive: the jar, the launcher and the JRE, the page inside the jar, and the page and the
server inside the APK.

**Signing the APK**: put the release key's coordinates in `android/keystore.properties` (not under
source control: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it, the APK is
signed with the debug key: you can install it for testing, but you can't publish it.

**The version** is `<revision>` in [`pom.xml`](pom.xml), said once: the jar, the API's `/api/info`,
the archives' names, and the APK's `versionName` and `versionCode` (`a.b.c` → `a·10000 + b·100 + c`)
all come from it.

## Git hooks

`.githooks/pre-push` runs `scripts/ci.sh` before every push. Enable it once per clone:

```bash
git config core.hooksPath .githooks
```

Skip it once with `git push --no-verify`.
