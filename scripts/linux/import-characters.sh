#!/usr/bin/env bash
#
# Imports the characters of Greek mythology from Wikidata into core/src/main/resources/data/characters/wikidata/,
# which it rewrites whole, and lists those files in data/index.json; the curated characters are never touched (see
# importer/src/main/kotlin/…/WikidataImport.kt). Needs the network. Then check the links and build:
#   scripts/linux/check-links.sh && scripts/linux/build.sh
#
#   scripts/linux/import-characters.sh          # the characters with a Wikipedia article in English or French
#   scripts/linux/import-characters.sh --all    # every one Wikidata has, even those it knows only by name
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../.."   # the project root
. scripts/linux/externals.sh   # setup done? (offers it otherwise); the externals first
mvn -q -pl importer -am package -DskipTests -P-android
exec java -cp "importer/target/mythostory-importer.jar:importer/target/lib/*" org.jtools.mythostory.importer.WikidataImportKt "$@"
