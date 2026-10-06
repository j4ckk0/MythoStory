#!/usr/bin/env bash
#
# The project's CI, run locally: a clean build with every test, the frontend's dependencies installed
# exactly as frontend/package-lock.json has them (npm ci); the Android app too when its externals are there.
#
#   scripts/ci.sh
#   scripts/ci.sh --no-android     # the desktop alone
#
# Run by .githooks/pre-push before every push; enable the hooks once with:
#   git config core.hooksPath .githooks
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first; $android
running_instance "the build"
profile=()
[ "${1:-}" = "--no-android" ] && profile=("$no_android_profile")
exec mvn -q clean package -Dnpm.install=ci ${profile[@]+"${profile[@]}"}
