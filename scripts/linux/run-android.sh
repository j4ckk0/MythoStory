#!/usr/bin/env bash
#
# Build the Android app if needed, then install and start it on the device or emulator adb sees (scripts/linux/setup.sh
# --android first; scripts/linux/emulator.sh starts the emulator, then hands over to this script).
#
#   scripts/linux/run-android.sh
#   scripts/linux/run-android.sh --rebuild   # force a rebuild first
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root
. scripts/linux/externals.sh   # setup done? (offers it otherwise); the externals first; $android (externals.local.sh)
$android || { echo "the Android app needs the Android SDK: run scripts/linux/setup.sh --android" >&2; exit 1; }
APK=android/target/gradle/outputs/apk/release/mythostory-android-release.apk

rebuild=false
for a in "$@"; do
  case "$a" in
    --rebuild) rebuild=true ;;
    *) echo "unknown argument: $a (try --rebuild)" >&2; exit 2 ;;
  esac
done

# Rebuild when asked, when the APK is missing, or when any source is newer than it.
sources=(pom.xml {core,frontend,backend,desktop,android}/pom.xml {core,frontend,backend,android}/src
         frontend/public frontend/package.json frontend/package-lock.json frontend/angular.json
         android/build.gradle.kts android/settings.gradle.kts)
if $rebuild || [ ! -f "$APK" ] || [ -n "$(find "${sources[@]}" -newer "$APK" -print -quit)" ]; then
  refuse_while_running "building the APK"   # the build replaces the desktop's jar too
  echo "== building $APK"
  # shellcheck disable=SC2086 # BUILD_GOALS (jtools.conf) is a list of goals; clean: Maven leaves in target/ what the sources no longer have
  mvn -q $BUILD_GOALS -DskipTests
fi
adb install -r "$APK"
exec adb shell am start -n org.jtools.mythostory/org.jtools.mythostory.android.MainActivity
