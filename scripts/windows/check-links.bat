@echo off
rem
rem Checks that every URL of the catalog answers: Wikipedia, articles, videos (through YouTube's oEmbed, which
rem answers only for videos that exist) and the ancient texts online. Needs the network, so the build never runs it:
rem run it after editing core\src\main\resources\data, and now and then, since pages move.
rem
rem   scripts\windows\check-links.bat
rem
cd /d "%~dp0..\.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
call mvn -q -pl core test -Dtest=LinkCheckTest -Dlinks.check=true -Dsurefire.failIfNoSpecifiedTests=false
exit /b %errorlevel%
