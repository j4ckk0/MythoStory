@echo off
rem
rem Build desktop\target\mythostory.jar — core, the frontend (npm, through Maven), the backend — with their tests;
rem and the Android app, android\target\gradle\outputs\apk\release\, when its externals are there (setup.bat --android).
rem
rem   scripts\build.bat
rem   scripts\build.bat --no-android       the desktop alone, even with the Android SDK there (first argument)
rem   scripts\build.bat -DskipTests        extra arguments are passed to mvn
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (setup done? it is offered otherwise; the externals first)
set "PROFILE="
if /i "%~1"=="--no-android" set "PROFILE=%NO_ANDROID_PROFILE%"
if /i "%~1"=="--no-android" shift
call :running || exit /b 1
call mvn clean package %PROFILE% %1 %2 %3 %4 %5 %6 %7 %8 %9
exit /b %errorlevel%

rem ---- MythoStory running from the jar? the build would fail deleting it, saying nothing of the cause
:running
wmic process where "name='java.exe'" get commandline 2>nul | findstr /i "mythostory.jar" >nul || exit /b 0
echo MythoStory is running: stop it before building, or it holds the jar the build replaces. >&2
exit /b 1
