# MythoStory's own externals, sourced by scripts/linux/externals.sh before it checks the devkit's: what of them is
# missing (missing_local_externals, which externals.sh adds to its own list), and the Android SDK's tools once
# scripts/linux/setup.sh --android has deployed it — Maven's profile "android" then switches itself on, and the build makes
# the APK too: $android is true, and -P-android ($no_android_profile) leaves it out.

missing_local_externals() {
  local m=
  [ -x "$ext/piper/piper" ] || m="$m piper/"
  [ -s "$ext/piper-linux.tar.gz" ] || m="$m piper-linux.tar.gz"
  [ -s "$ext/piper-windows.zip" ] || m="$m piper-windows.zip"
  local model   # every VOICE_* of externals.conf
  for model in $(sed -n 's:^VOICE_[A-Z0-9_]*=.*/\([^/]*\.onnx\)\r*$:\1:p' externals.conf); do
    [ -s "$ext/voices/$model" ] && [ -s "$ext/voices/$model.json" ] || m="$m voices/$model"
  done
  if [ -d "$ext/android-sdk/platforms" ] && [ ! -x "$ext/gradle/bin/gradle" ]; then m="$m gradle/ (setup.sh --android)"; fi
  echo "$m"
}

android=false
if [ -d "$ext/android-sdk/platforms" ]; then
  android=true
  export ANDROID_HOME="$ext/android-sdk"
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi
no_android_profile=-P-android
