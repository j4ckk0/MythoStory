#!/usr/bin/env bash
#
# Build desktop/target/mythostory.jar — core, the frontend (npm, through Maven), the backend — with their tests;
# and the Android app, android/target/gradle/outputs/apk/release/, when its externals are there (setup.sh --android).
#
#   scripts/build.sh
#   scripts/build.sh --no-android       # the desktop alone, even with the Android SDK there
#   scripts/build.sh -DskipTests        # extra arguments are passed to mvn
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first; $android
running_instance "building"
args=()
for a in "$@"; do
  case "$a" in
    --no-android) args+=("$no_android_profile") ;;
    *) args+=("$a") ;;
  esac
done
exec mvn clean package ${args[@]+"${args[@]}"}
