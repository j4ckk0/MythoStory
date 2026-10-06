#!/bin/sh
# Run MythoStory with the bundled JRE. Arguments are passed to the program:
#   ./mythostory.sh [--port N] [--host H] [--data DIR] [--no-browser]
dir=$(dirname "$(readlink -f "$0")")
exec "$dir/jre/bin/java" -jar "$dir/mythostory.jar" "$@"
