#!/usr/bin/env bash
#
# Sourced by the scripts, from the project root: makes sure scripts/setup.sh has been run — everything it
# deploys is there: external/jdk, maven, node, piper, voices, the JRE's and Piper's archives — offering to run it
# when something is missing (at a terminal; elsewhere, the CI or a hook, it stops with the way out). Then
# puts the externals in front: external/jdk as JAVA_HOME, its java, Maven's mvn and Node's node and npm first
# in PATH (Maven runs npm to build the frontend). Only generic names: the versions are externals.conf's business.
#
# The Android app is built too as soon as scripts/setup.sh --android has deployed external/gradle and
# external/android-sdk (Maven's profile "android" switches itself on): $android is then true, and a script's
# --no-android leaves it out (passed to Maven as -P-android, by $no_android_profile).
#
# Sets $jar, the jar the build makes.
#
ext="$PWD/external"

# What setup deploys; empty when all is there.
missing_externals() {
  local m=
  [ -x "$ext/jdk/bin/java" ] || m="$m jdk/"
  [ -x "$ext/maven/bin/mvn" ] || m="$m maven/"
  [ -x "$ext/node/bin/node" ] || m="$m node/"
  [ -s "$ext/jre-linux.tar.gz" ] || m="$m jre-linux.tar.gz"
  [ -s "$ext/jre-windows.zip" ] || m="$m jre-windows.zip"
  [ -x "$ext/piper/piper" ] || m="$m piper/"
  [ -s "$ext/piper-linux.tar.gz" ] || m="$m piper-linux.tar.gz"
  [ -s "$ext/piper-windows.zip" ] || m="$m piper-windows.zip"
  local model   # every VOICE_* of externals.conf
  for model in $(sed -n 's:^VOICE_[A-Z0-9_]*=.*/\([^/]*\.onnx\)$:\1:p' externals.conf); do
    [ -s "$ext/voices/$model" ] && [ -s "$ext/voices/$model.json" ] || m="$m voices/$model"
  done
  echo "$m"
}

missing=$(missing_externals)
if [ -n "$missing" ]; then
  echo "external/ is not set up: missing$missing (scripts/setup.sh deploys them, see README: Externals)" >&2
  if [ -t 0 ] && [ -t 2 ]; then
    read -r -p "Run scripts/setup.sh now? [Y/n] " answer
    case "$answer" in ''|[Yy]*) scripts/setup.sh ;; *) exit 1 ;; esac
    missing=$(missing_externals)
    [ -z "$missing" ] || { echo "still missing$missing" >&2; exit 1; }
  else
    exit 1
  fi
fi

export JAVA_HOME="$ext/jdk"
export PATH="$ext/jdk/bin:$ext/maven/bin:$ext/node/bin:$PATH"

android=false
if [ -d "$ext/android-sdk/platforms" ]; then
  [ -x "$ext/gradle/bin/gradle" ] || { echo "external/android-sdk is there, not external/gradle: run scripts/setup.sh --android" >&2; exit 1; }
  android=true
  export ANDROID_HOME="$ext/android-sdk"
  export PATH="$ext/gradle/bin:$ANDROID_HOME/platform-tools:$PATH"
fi
no_android_profile=-P-android

jar=desktop/target/mythostory.jar

# Refuses to go on while MythoStory runs from the jar a build is about to replace: Maven would otherwise fail
# deleting it, with an error saying nothing of the cause. $1 is what the caller is about to do.
running_instance() {
  local pids
  # anchored on the process's own java: a shell command line that merely names the jar is not an instance
  pids=$(pgrep -f '^[^[:space:]]*java[^[:space:]]* .*-jar[[:space:]].*mythostory\.jar' || true)
  [ -n "$pids" ] || return 0
  echo "MythoStory is running (pid$( [ "$(echo "$pids" | wc -l)" -gt 1 ] && echo s) $(echo "$pids" | tr '\n' ' ')): stop it before ${1:-building}, or it holds the jar the build replaces." >&2
  exit 1
}
