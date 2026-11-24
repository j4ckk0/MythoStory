# MythoStory's own externals, sourced by scripts/linux/externals.sh before it checks the devkit's: what of them is
# missing or out of date (missing_local_externals, which externals.sh adds to its own list: each external remembers
# the URL it was deployed from, external/.<name>.url, as the devkit's do), and the Android SDK's tools once
# scripts/linux/setup.sh --android has deployed it — $android is true then, and Maven's profile "android" switches
# itself on: the build makes the APK too (-P-android leaves it out).

missing_local_externals() {
  local m=
  # deployed, and from the URL externals.conf names now
  here() { [ -e "$1" ] && [ "$(cat "$ext/.$2.url" 2>/dev/null)" = "$3" ]; }
  here "$ext/piper/piper" piper "$(url_of PIPER_LINUX)" || m="$m piper/"
  here "$ext/piper-linux.tar.gz" piper-linux.tar.gz "$(url_of PIPER_LINUX)" || m="$m piper-linux.tar.gz"
  here "$ext/piper-windows.zip" piper-windows.zip "$(url_of PIPER_WINDOWS)" || m="$m piper-windows.zip"
  local key url   # every VOICE_* of externals.conf, its model and the model's .json
  for key in $(sed -n 's:^\(VOICE_[A-Z0-9_]*\)=.*:\1:p' externals.conf); do
    url=$(url_of "$key")
    here "$ext/voices/${url##*/}" "voice-${url##*/}" "$url" && here "$ext/voices/${url##*/}.json" "voice-${url##*/}.json" "$url.json" \
      || m="$m voices/${url##*/}"
  done
  if [ -d "$ext/android-sdk/platforms" ]; then
    here "$ext/gradle/bin/gradle" gradle "$(url_of ANDROID_GRADLE)" || m="$m gradle/ (setup.sh --android)"
  fi
  echo "$m"
}

android=false
if [ -d "$ext/android-sdk/platforms" ]; then
  android=true
  export ANDROID_HOME="$ext/android-sdk"
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi
