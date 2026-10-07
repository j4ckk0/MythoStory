#!/usr/bin/env bash
#
# Build (if needed) and run MythoStory: the desktop program serves the page and its API on http://127.0.0.1:8080/
# and opens the browser on it — it stops once its last page is closed; or, with --android, the app installed and
# started on the device adb sees.
#
#   scripts/run.sh
#   scripts/run.sh --port 9090 --no-browser   # see: scripts/run.sh --help
#   scripts/run.sh --keep-running         # keep serving once the last page is closed (Ctrl+C then)
#   scripts/run.sh --rebuild              # force a rebuild first
#   scripts/run.sh --android              # on the device or emulator adb sees (setup.sh --android first)
#
# Any other argument is passed to the program.
#
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first; $android
JAR=$jar   # the program's jar (scripts/externals.sh)
APK=android/target/gradle/outputs/apk/release/mythostory-android-release.apk

rebuild=false
on_android=false
args=()
for a in "$@"; do
  case "$a" in
    --rebuild) rebuild=true ;;
    --android) on_android=true ;;
    *) args+=("$a") ;;
  esac
done
if $on_android && ! $android; then
  echo "--android needs the Android SDK: run scripts/setup.sh --android" >&2
  exit 1
fi

# What is run, and what it is built from.
built=$JAR
sources=(pom.xml {core,frontend,backend,desktop}/pom.xml {core,frontend,backend,desktop}/src
         frontend/public frontend/package.json frontend/package-lock.json frontend/angular.json)
profile=("$no_android_profile")
if $on_android; then
  built=$APK
  sources+=(android/pom.xml android/build.gradle.kts android/settings.gradle.kts android/src)
  profile=()
fi

# Rebuild when asked, when the output is missing, or when any source is newer than it.
if $rebuild || [ ! -f "$built" ] || [ -n "$(find "${sources[@]}" -newer "$built" -print -quit)" ]; then
  echo "== building $built"
  mvn -q clean package -DskipTests ${profile[@]+"${profile[@]}"}   # clean: Maven leaves in target/ what the sources no longer have (a removed resource would still be served, and shipped)
fi

if $on_android; then
  adb install -r "$APK"
  exec adb shell am start -n org.jtools.mythostory/org.jtools.mythostory.android.MainActivity
fi

# Run a copy of the jar, never the jar itself: a build rewrites $JAR in place, and a server still running from it
# would then read garbage out of its own jar. The copy is written under a name of its own and renamed into place,
# a new file: a copy an earlier run is still reading is left whole, and a build, which only writes $JAR, never touches it.
RUN_JAR="$(dirname "$JAR")/mythostory-run.jar"
cp "$JAR" "$RUN_JAR.$$"
mv -f "$RUN_JAR.$$" "$RUN_JAR"

# Piper and its voices from external/: the distributions have them next to the jar, where the program looks by default
exec java -jar "$RUN_JAR" --piper external/piper --voices external/voices ${args[@]+"${args[@]}"}
