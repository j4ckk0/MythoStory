#!/usr/bin/env bash
#
# Runs MythoStory in the Android emulator: starts the emulator's virtual phone — created on the first run, in
# external/android-avd —, waits for it to boot, then builds the app if needed, installs it and starts it there
# (scripts/run.sh --android). The emulator keeps running afterwards: run this again after a change, it reinstalls.
# Needs the emulator and its system image: scripts/setup.sh --emulator, once (offered when they are missing).
#
#   scripts/emulator.sh                  start the phone (or use the one running), install and start the app
#   scripts/emulator.sh --rebuild        force a rebuild of the app first
#   scripts/emulator.sh --wipe           the phone as new: its data wiped as it starts (the app uninstalled with it)
#   scripts/emulator.sh --no-window      the phone without its window (the CI, a remote machine)
#   scripts/emulator.sh --stop           stop the phone
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first; $android, $ANDROID_HOME

AVD=mythostory
LOG=tmp/emulator.log
BOOT_TIMEOUT=300   # seconds: a fresh phone's first boot takes a minute or two, even with KVM

wipe=false
window=true
stop=false
args=()
for a in "$@"; do
  case "$a" in
    --wipe) wipe=true ;;
    --no-window) window=false ;;
    --stop) stop=true ;;
    *) args+=("$a") ;;
  esac
done

# externals.conf's ANDROID_EMULATOR_PACKAGES and ANDROID_AVD_DEVICE (the environment wins, as in setup.sh)
conf() {
  local key=$1 value
  [ -n "${!key:-}" ] && { echo "${!key}"; return; }
  value=
  [ -f externals.local.conf ] && value=$(sed -n "s/^$key=//p" externals.local.conf | sed -n 1p)
  [ -n "$value" ] || value=$(sed -n "s/^$key=//p" externals.conf | sed -n 1p)
  echo "$value"
}
image=
for package in $(conf ANDROID_EMULATOR_PACKAGES); do
  case "$package" in system-images\;*) image=$package ;; esac
done
device=$(conf ANDROID_AVD_DEVICE)

# Whether setup.sh --emulator has deployed the emulator and its system image.
emulator_set_up() {
  [ -n "$image" ] && [ -x "external/android-sdk/emulator/emulator" ] && [ -d "external/android-sdk/${image//;//}" ]
}
if ! emulator_set_up; then
  echo "The Android emulator is not set up: scripts/setup.sh --emulator installs it, once (the Android SDK and its" >&2
  echo "licences, the emulator and its system image: 3 to 4 GB)." >&2
  # as externals.sh does: at a terminal, offer to run it; elsewhere (the CI), stop with the way out
  [ -t 0 ] && [ -t 2 ] || exit 1
  read -r -p "Run scripts/setup.sh --emulator now? [Y/n] " answer
  case "$answer" in ''|[Yy]*) scripts/setup.sh --emulator ;; *) exit 1 ;; esac
  emulator_set_up || { echo "the emulator is still not set up: see setup.sh's messages above" >&2; exit 1; }
  # setup.sh has just made the Android SDK: its tools into PATH, as externals.sh does
  . scripts/externals.sh
fi
emulator=$ANDROID_HOME/emulator/emulator
avdmanager() { "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" "$@"; }
# the virtual phone under external/, with the rest of the externals
export ANDROID_AVD_HOME=$PWD/external/android-avd
mkdir -p "$ANDROID_AVD_HOME" tmp

# Every pipe below lets its reader take the whole input (sed -n, grep > /dev/null), never leave early as grep -q or
# head do: under pipefail the writer they would leave behind, adb, would fail the pipeline.

# The serial of the emulator running our phone ("emulator-5554"), if one is: found by the phone's name, whatever
# other emulator or device adb sees.
running_serial() {
  local serial
  for serial in $(adb devices | sed -n 's/^\(emulator-[0-9]*\)\tdevice$/\1/p'); do
    [ "$(adb -s "$serial" emu avd name 2>/dev/null | sed -n 1p | tr -d '\r')" = "$AVD" ] && { echo "$serial"; return 0; }
  done
  return 0   # none: an empty serial
}

serial=$(running_serial)
if $stop; then
  if [ -z "$serial" ]; then echo "The MythoStory phone is not running."; exit 0; fi
  echo "== stopping the MythoStory phone ($serial)"
  adb -s "$serial" emu kill >/dev/null
  exit 0
fi

if [ ! -f "$ANDROID_AVD_HOME/$AVD.ini" ]; then
  echo "== creating the virtual phone $AVD: a $device on $image, in $ANDROID_AVD_HOME"
  # "no": no custom hardware profile
  echo no | avdmanager create avd --name "$AVD" --package "$image" --device "$device" --path "$ANDROID_AVD_HOME/$AVD.avd" >/dev/null
fi

if [ -n "$serial" ]; then
  $wipe && { echo "--wipe: the phone is already running; stop it first (scripts/emulator.sh --stop)" >&2; exit 1; }
  echo "== the MythoStory phone is running ($serial)"
else
  # the first free port of the emulator's range; the serial is emulator-<port>
  port=5554
  while adb devices | grep "^emulator-$port" > /dev/null; do port=$((port + 2)); done
  [ "$port" -le 5584 ] || { echo "no free emulator port between 5554 and 5584" >&2; exit 1; }
  serial=emulator-$port
  options=(-avd "$AVD" -port "$port" -no-boot-anim)
  $wipe && options+=(-wipe-data)
  $window || options+=(-no-window -no-audio)
  echo "== starting the MythoStory phone ($serial; its log: $LOG)"
  # detached: the phone outlives this script, which hands over to run.sh
  setsid nohup "$emulator" "${options[@]}" > "$LOG" 2>&1 < /dev/null &
  emulator_pid=$!
  echo -n "== waiting for it to boot"
  waited=0
  until [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
      echo; echo "the emulator stopped: see $LOG" >&2; tail -5 "$LOG" >&2; exit 1
    fi
    if [ "$waited" -ge "$BOOT_TIMEOUT" ]; then
      echo; echo "the phone has not booted after $BOOT_TIMEOUT s: see $LOG" >&2; exit 1
    fi
    echo -n .; sleep 3; waited=$((waited + 3))
  done
  echo " booted"
fi

# run.sh installs with a bare adb: this phone, whatever else adb sees
export ANDROID_SERIAL=$serial
exec scripts/run.sh --android ${args[@]+"${args[@]}"}
