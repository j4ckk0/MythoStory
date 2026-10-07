#!/usr/bin/env bash
#
# Stops MythoStory: the desktop servers running on this machine — from scripts/run.sh (mythostory-run.jar) or from a
# distribution (mythostory.jar). The clean way first: SIGTERM, which the server's shutdown hook answers by closing the
# server and its Piper voices; SIGKILL for one still running 10 seconds later.
#
#   scripts/stop.sh              # every MythoStory server
#   scripts/stop.sh --port 9090  # the one serving that port (8080 when started without --port)
#
set -euo pipefail

port=
case "${1:-}" in
  '') ;;
  --port) port=${2:?--port takes a number} ;;
  *) echo "unknown argument: $1 (try --port N)" >&2; exit 2 ;;
esac

# The port a server serves: its --port argument, 8080 without one.
served_port() {
  local args
  args=$(tr '\0' ' ' < "/proc/$1/cmdline" 2>/dev/null) || return 0
  if [[ $args =~ --port[[:space:]]+([0-9]+) ]]; then echo "${BASH_REMATCH[1]}"; else echo 8080; fi
}

# anchored on the process's own java: a shell command line that merely names the jar is not a server
servers=()
for pid in $(pgrep -f '^[^[:space:]]*java[^[:space:]]* .*-jar[[:space:]]+[^[:space:]]*mythostory(-run)?\.jar' || true); do
  if [ -z "$port" ] || [ "$(served_port "$pid")" = "$port" ]; then servers+=("$pid"); fi
done
if [ ${#servers[@]} -eq 0 ]; then
  echo "No MythoStory server is running${port:+ on port $port}."
  exit 0
fi

for pid in "${servers[@]}"; do
  echo "== stopping MythoStory on port $(served_port "$pid") (pid $pid)"
  kill -TERM "$pid" 2>/dev/null || true
done
for _ in $(seq 40); do
  alive=()
  for pid in "${servers[@]}"; do kill -0 "$pid" 2>/dev/null && alive+=("$pid"); done
  [ ${#alive[@]} -eq 0 ] && { echo "== MythoStory stopped"; exit 0; }
  sleep 0.25
done
echo "== still running after 10 s: killed (pid ${alive[*]})" >&2
kill -KILL "${alive[@]}" 2>/dev/null || true
