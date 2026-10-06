#!/usr/bin/env bash
#
# Checks that every URL of the catalog answers: Wikipedia, articles, videos (through YouTube's oEmbed, which
# answers only for videos that exist) and the ancient texts online. Needs the network, so the build never runs it:
# run it after editing core/src/main/resources/data, and now and then, since pages move.
#
#   scripts/check-links.sh
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first
exec mvn -q -pl core test -Dtest=LinkCheckTest -Dlinks.check=true -Dsurefire.failIfNoSpecifiedTests=false
