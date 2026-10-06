@echo off
rem
rem The project's CI, run locally: a clean build with every test, the frontend's dependencies installed
rem exactly as frontend\package-lock.json has them (npm ci); the Android app too when its externals are there.
rem
rem   scripts\ci.bat
rem   scripts\ci.bat --no-android     the desktop alone
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (setup done? it is offered otherwise; the externals first)
set "PROFILE="
if /i "%~1"=="--no-android" set "PROFILE=%NO_ANDROID_PROFILE%"
call mvn -q clean package -Dnpm.install=ci %PROFILE%
exit /b %errorlevel%
