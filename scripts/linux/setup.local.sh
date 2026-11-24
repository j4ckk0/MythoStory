# MythoStory's own externals, sourced by scripts/linux/setup.sh once the devkit's are deployed (fetch, deploy, $ext,
# $force and the options it does not know in $local_args at hand):
#   external/piper                                      Piper, the speech synthesis, for this machine
#   external/piper-linux.tar.gz, piper-windows.zip      Piper's archives the distributions bundle
#   external/voices/<model>.onnx(.json)                 Piper's voices, every VOICE_* of externals.conf
# and with --android, what the Android app's build needs (about a gigabyte):
#   external/gradle                                     Gradle, which builds the APK (android/, run by Maven)
#   external/android-sdk                                the Android SDK: its command-line tools, then the
#                                                       packages ANDROID_SDK_PACKAGES names, the licences accepted
#
#   scripts/linux/setup.sh --android    the above, plus the Android build's externals (the build then makes the APK too)
#   scripts/linux/setup.sh --emulator   the above with --android, plus the Android emulator and its system image
#                                       (ANDROID_EMULATOR_PACKAGES, 2 to 3 GB), which scripts/linux/emulator.sh runs the app in
#
# An external is deployed again when externals.conf names another URL for it than the one it came from
# (external/.<name>.url, the devkit's stamp): a new version is a line changed there, then setup. Its download is
# then fetched anew too, in case the new URL keeps the old file name (a voice's new version).
current() { ! $force && [ -e "$1" ] && deployed_from "$2" "$3"; }
fetch_for() { if [ -e "$ext/.$1.url" ] && ! deployed_from "$1" "$2"; then (force=true; fetch "$2"); else fetch "$2"; fi; }

android=false
emulator=false
for arg in ${local_args[@]+"${local_args[@]}"}; do
  case "$arg" in
    --android) android=true ;;
    --emulator) android=true; emulator=true ;;
    *) echo "unknown option: $arg (try --force, --android, --emulator)" >&2; exit 1 ;;
  esac
done

# Piper, for this machine: the system tar, which keeps the libraries' symlinks
if current "$ext/piper/piper" piper "$PIPER_LINUX"; then echo "== $ext/piper present"; else
  file=$(fetch_for piper "$PIPER_LINUX") || exit 1
  deploy "$file" "$ext/piper"
  stamp piper "$PIPER_LINUX"
  echo "== $ext/piper deployed"
fi

# Piper's voices: every VOICE_* model and its .onnx.json, under their own names; a model no longer listed goes
mkdir -p "$ext/voices"
models=()
for key in $(compgen -A variable VOICE_); do
  for url in "${!key}" "${!key}.json"; do
    target=$ext/voices/${url##*/}
    models+=("${url##*/}")
    if current "$target" "voice-${url##*/}" "$url"; then echo "== $target present"; continue; fi
    file=$(fetch_for "voice-${url##*/}" "$url") || exit 1
    cp "$file" "$target"
    stamp "voice-${url##*/}" "$url"
    echo "== $target deployed"
  done
done
for file in "$ext"/voices/*.onnx "$ext"/voices/*.onnx.json; do
  [ -e "$file" ] || continue
  case " ${models[*]} " in *" ${file##*/} "*) ;; *) rm -f "$file" "$ext/.voice-${file##*/}.url"; echo "== $file removed (no longer in externals.conf)" ;; esac
done

# Piper's archives the distributions bundle, kept as archives under generic names
for spec in "piper-linux.tar.gz PIPER_LINUX" "piper-windows.zip PIPER_WINDOWS"; do
  set -- $spec
  target=$ext/$1
  if current "$target" "$1" "${!2}"; then echo "== $target present"; continue; fi
  file=$(fetch_for "$1" "${!2}") || exit 1
  cp "$file" "$target"
  stamp "$1" "${!2}"
  echo "== $target deployed"
done

# the Android build's externals: only when asked for
if $android; then
  dir=$ext/gradle
  if current "$dir/bin/gradle" gradle "$ANDROID_GRADLE"; then echo "== $dir present"; else
    file=$(fetch_for gradle "$ANDROID_GRADLE") || exit 1
    deploy "$file" "$dir"
    stamp gradle "$ANDROID_GRADLE"
    echo "== $dir deployed"
  fi
  # the command-line tools go where sdkmanager expects to find itself: <sdk>/cmdline-tools/latest
  sdk=$ext/android-sdk
  if current "$sdk/cmdline-tools/latest/bin/sdkmanager" android-cmdline-tools "$ANDROID_CMDLINE_TOOLS_LINUX"; then echo "== $sdk/cmdline-tools present"; else
    file=$(fetch_for android-cmdline-tools "$ANDROID_CMDLINE_TOOLS_LINUX") || exit 1
    deploy "$file" "$sdk/cmdline-tools/latest"
    stamp android-cmdline-tools "$ANDROID_CMDLINE_TOOLS_LINUX"
    echo "== $sdk/cmdline-tools deployed"
  fi
  sdkmanager() { JAVA_HOME="$PWD/$ext/jdk" "$sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$PWD/$sdk" "$@"; }
  # The licences are yours to read and accept, once: sdkmanager asks, at a terminal. Elsewhere (the CI) they
  # must have been accepted already — the packages are not installed otherwise.
  if [ -t 0 ]; then
    echo "== the Android SDK licences: read them, and accept them to go on"
    sdkmanager --licenses
  fi
  echo "== installing $ANDROID_SDK_PACKAGES"
  # shellcheck disable=SC2086   # one package a word
  sdkmanager --install $ANDROID_SDK_PACKAGES
  # sdkmanager says nothing by its status of the packages it skipped for want of a licence: each one's directory
  # tells (a package "a;b" lives in <sdk>/a/b)
  packages=$ANDROID_SDK_PACKAGES
  if $emulator; then
    echo "== installing $ANDROID_EMULATOR_PACKAGES"
    # shellcheck disable=SC2086   # one package a word
    sdkmanager --install $ANDROID_EMULATOR_PACKAGES
    packages="$packages $ANDROID_EMULATOR_PACKAGES"
  fi
  for package in $packages; do
    [ -d "$sdk/${package//;//}" ] || { echo "$package was not installed: accept the licences (scripts/linux/setup.sh --android, at a terminal)" >&2; exit 1; }
  done
fi

echo "Piper: $ext/piper, voices: $(cd "$ext/voices" && ls *.onnx | tr '\n' ' ')"
if [ -d "$ext/android-sdk/platforms" ]; then echo "Android SDK: $ext/android-sdk ($(ls "$ext/android-sdk/platforms" | tr '\n' ' '))"; fi
if [ -x "$ext/android-sdk/emulator/emulator" ]; then echo "Android emulator: $ext/android-sdk/emulator (scripts/linux/emulator.sh)"; fi
