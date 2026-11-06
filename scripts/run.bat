@echo off
rem
rem Build (if needed) and run MythoStory on Windows: the desktop program serves the page and its API on
rem http://127.0.0.1:8080/ and opens the browser on it; or, with --android, the app installed and started on the
rem device adb sees.
rem
rem   scripts\run.bat
rem   scripts\run.bat --port 9090 --no-browser     see: scripts\run.bat --help
rem   scripts\run.bat --rebuild                    force a rebuild first
rem   scripts\run.bat --android                    on the device or emulator adb sees (setup.bat --android first);
rem                                                scripts\emulator.bat starts the emulator first
rem
rem Any other argument is passed to the program.
rem
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (setup done? it is offered otherwise; the externals first; sets JAR and ANDROID)
set "APK=android\target\gradle\outputs\apk\release\mythostory-android-release.apk"

set "REBUILD=0"
set "ON_ANDROID=0"
set "ARGS="
for %%a in (%*) do (
  if /i "%%~a"=="--rebuild" (
    set "REBUILD=1"
  ) else if /i "%%~a"=="--android" (
    set "ON_ANDROID=1"
  ) else (
    set "ARGS=!ARGS! %%a"
  )
)
if "%ON_ANDROID%"=="1" if "%ANDROID%"=="0" (
  echo --android needs the Android SDK: run scripts\setup.bat --android >&2
  exit /b 1
)

rem What is run, and what it is built from.
set "BUILT=%JAR%"
set "SOURCES=Get-Item pom.xml, core\pom.xml, frontend\pom.xml, backend\pom.xml, desktop\pom.xml, frontend\package.json, frontend\package-lock.json, frontend\angular.json"
set "SOURCE_DIRS=core\src, frontend\src, frontend\public, backend\src, desktop\src"
set "PROFILE=%NO_ANDROID_PROFILE%"
if "%ON_ANDROID%"=="1" (
  set "BUILT=%APK%"
  set "SOURCES=!SOURCES!, android\pom.xml, android\build.gradle.kts, android\settings.gradle.kts"
  set "SOURCE_DIRS=!SOURCE_DIRS!, android\src"
  set "PROFILE="
)

rem Rebuild when asked, when the output is missing, or when any source is newer than it.
set "NEED_BUILD=%REBUILD%"
if not exist "%BUILT%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$built = (Get-Item '%BUILT%').LastWriteTimeUtc;" ^
    "$src = @(%SOURCES%) + @(Get-ChildItem %SOURCE_DIRS% -Recurse -File);" ^
    "if ($src | Where-Object { $_.LastWriteTimeUtc -gt $built }) { exit 1 } else { exit 0 }"
  if errorlevel 1 set "NEED_BUILD=1"
)

if "%NEED_BUILD%"=="1" (
  echo == building %BUILT%
  call mvn -q clean package -DskipTests %PROFILE% || exit /b 1
)

if "%ON_ANDROID%"=="1" (
  adb install -r "%APK%" || exit /b 1
  adb shell am start -n org.jtools.mythostory/org.jtools.mythostory.android.MainActivity
  exit /b !errorlevel!
)

rem Piper and its voices from external\: the distributions have them next to the jar, where the program looks by default
java -jar "%JAR%" --piper external\piper --voices external\voices %ARGS%
exit /b %errorlevel%
