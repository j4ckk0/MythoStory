@echo off
rem
rem Build the Android app if needed, then install and start it on the device or emulator adb sees
rem (scripts\windows\setup.bat --android first; scripts\windows\emulator.bat starts the emulator, then hands over to this script).
rem
rem   scripts\windows\run-android.bat
rem   scripts\windows\run-android.bat --rebuild    force a rebuild first
rem
setlocal EnableDelayedExpansion

cd /d "%~dp0..\.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
call "%~dp0android-env.bat" || exit /b 1
if "%ANDROID%"=="0" (
  echo the Android app needs the Android SDK: run scripts\windows\setup.bat --android >&2
  exit /b 1
)
set "APK=android\target\gradle\outputs\apk\release\mythostory-android-release.apk"

set "REBUILD=0"
for %%a in (%*) do (
  if /i "%%~a"=="--rebuild" (
    set "REBUILD=1"
  ) else (
    echo unknown argument: %%~a ^(try --rebuild^) >&2
    exit /b 2
  )
)

rem Rebuild when asked, when the APK is missing, or when any source is newer than it.
set "NEED_BUILD=%REBUILD%"
if not exist "%APK%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$built = (Get-Item '%APK%').LastWriteTimeUtc;" ^
    "$src = @(Get-Item pom.xml, core\pom.xml, frontend\pom.xml, backend\pom.xml, desktop\pom.xml, android\pom.xml, frontend\package.json, frontend\package-lock.json, frontend\angular.json, android\build.gradle.kts, android\settings.gradle.kts) + @(Get-ChildItem core\src, frontend\src, frontend\public, backend\src, android\src -Recurse -File);" ^
    "if ($src | Where-Object { $_.LastWriteTimeUtc -gt $built }) { exit 1 } else { exit 0 }"
  if errorlevel 1 set "NEED_BUILD=1"
)
if "%NEED_BUILD%"=="1" (
  echo == building %APK%
  call mvn -q clean package -DskipTests || exit /b 1
)
adb install -r "%APK%" || exit /b 1
adb shell am start -n org.jtools.mythostory/org.jtools.mythostory.android.MainActivity
exit /b %errorlevel%
