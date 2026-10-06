@echo off
rem
rem Imports the characters of Greek mythology from Wikidata into core\src\main\resources\data\characters\wikidata\,
rem which it rewrites whole, and lists those files in data\index.json; the curated characters are never touched (see
rem importer\src\main\kotlin\...\WikidataImport.kt). Needs the network. Then check the links and build:
rem   scripts\check-links.bat, then scripts\build.bat
rem
rem   scripts\import-characters.bat          the characters with a Wikipedia article in English or French
rem   scripts\import-characters.bat --all    every one Wikidata has, even those it knows only by name
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
call mvn -q -pl importer -am package -DskipTests -P-android || exit /b 1
java -cp "importer\target\mythostory-importer.jar;importer\target\lib\*" org.jtools.mythostory.importer.WikidataImportKt %*
exit /b %errorlevel%
