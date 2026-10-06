#!/usr/bin/env bash
#
# Build the distributions into releases/:
#   releases/mythostory-<version>-windows.zip      the desktop: jar + bundled JRE + mythostory.bat
#   releases/mythostory-<version>-linux.tar.gz     the desktop: jar + bundled JRE + mythostory.sh
#   releases/mythostory-<version>-android.apk      the Android app, when its externals are there (setup.sh --android);
#                                                  signed with android/keystore.properties' key, the debug key without it
#
#   scripts/package.sh
#   scripts/package.sh --no-android    # the desktop's alone
#   scripts/package.sh -DskipTests     # extra arguments are passed to mvn
#
# The JRE archives come from external/ (scripts/setup.sh deploys them; scripts/externals.sh checks they are there).
# The releases of the previous versions are removed once the new one is built: releases/ holds the release
# of the version pom.xml names, and whatever else you put there is left alone.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first; $android
running_instance "packaging"

args=()
for a in "$@"; do
  case "$a" in
    --no-android) android=false; args+=("$no_android_profile") ;;
    *) args+=("$a") ;;
  esac
done

mvn clean package -Pdist ${args[@]+"${args[@]}"}

# The releases of the previous versions go, now that the new one is there (only ours, named after another version).
version=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' pom.xml | head -1)   # said once, in the parent pom
for archive in releases/mythostory-*-windows.zip releases/mythostory-*-linux.tar.gz releases/mythostory-*-android.apk; do
  [ -f "$archive" ] || continue
  case "$archive" in *"-$version-"*) continue ;; esac
  rm -f "$archive"
  echo "== $archive removed (previous release)"
done

# What every desktop distribution must carry: the jar, its launcher, the bundled JRE — and the frontend inside the jar.
# Every check gives grep the entries whole, never through a pipe it may leave early: under pipefail the writer it
# leaves behind would fail the pipeline, and the check with it.
check_jar=$(mktemp)
trap 'rm -f "$check_jar"' EXIT
for archive in releases/mythostory-"$version"-windows.zip releases/mythostory-"$version"-linux.tar.gz; do
  [ -f "$archive" ] || { echo "$archive was not built" >&2; exit 1; }
  case "$archive" in
    *.zip) entries=$(unzip -Z1 "$archive"); unzip -p "$archive" '*/mythostory.jar' > "$check_jar"
           needed=(mythostory.jar mythostory.bat jre/bin/java.exe) ;;
    *) entries=$(tar tzf "$archive"); tar xzOf "$archive" --wildcards '*/mythostory.jar' > "$check_jar"
       needed=(mythostory.jar mythostory.sh jre/bin/java) ;;
  esac
  for file in "${needed[@]}"; do
    grep -q "^mythostory-$version/$file\$" <<< "$entries" || { echo "$archive: no $file" >&2; exit 1; }
  done
  jar_entries=$(unzip -Z1 "$check_jar")
  grep -q '^static/index\.html$' <<< "$jar_entries" || { echo "$archive: the jar carries no frontend (static/index.html)" >&2; exit 1; }
done
echo "== releases/ checked: mythostory-$version, the jar (with the frontend), the launcher and the JRE in each"

# The APK: the same page and server inside (the frontend and the backend as Java resources and dex)
if $android; then
  apk=releases/mythostory-"$version"-android.apk
  [ -f "$apk" ] || { echo "$apk was not built" >&2; exit 1; }
  apk_entries=$(unzip -Z1 "$apk")
  for file in classes.dex static/index.html mythostory.properties; do
    grep -q "^$file\$" <<< "$apk_entries" || { echo "$apk: no $file" >&2; exit 1; }
  done
  [ -f android/keystore.properties ] || echo "== $apk is signed with the debug key: installable for testing, not publishable (android/build.gradle.kts)"
  echo "== $apk checked: the page and the server inside"
fi
