@echo off
rem MythoStory's own externals on Windows, called by scripts\windows\setup.bat once the devkit's are deployed, with its
rem arguments (EXT, DL, FORCE, CURL, TAR, AUTH and externals.conf's keys at hand):
rem   external\piper                                     Piper, the speech synthesis, for this machine (PIPER_WINDOWS)
rem   external\piper-linux.tar.gz, piper-windows.zip     Piper's archives the distributions bundle
rem   external\voices\<model>.onnx(.json)                Piper's voices, every VOICE_* of externals.conf
rem and with --android, what the Android app's build needs (about a gigabyte) - external\gradle, then
rem external\android-sdk: its command-line tools, the packages ANDROID_SDK_PACKAGES names, the licences accepted;
rem --emulator adds the emulator and its system image (ANDROID_EMULATOR_PACKAGES, 2 to 3 GB), which
rem scripts\windows\emulator.bat runs the app in.
rem
rem Each external remembers the URL it was deployed from (external\.<name>.url, as the devkit's do): one that
rem externals.conf names another URL for is deployed again. Written after setup.local.sh, NOT YET RUN ON WINDOWS: the
rem Piper part is new (2026-11-24) - say what fails, it will be fixed.
rem
rem   scripts\windows\setup.bat
rem   scripts\windows\setup.bat --android
rem   scripts\windows\setup.bat --emulator
rem
setlocal EnableDelayedExpansion
set "ANDROID=0"
set "EMULATOR=0"
for %%a in (%*) do (
  if /i "%%~a"=="--android" set "ANDROID=1"
  if /i "%%~a"=="--emulator" (
    set "ANDROID=1"
    set "EMULATOR=1"
  )
)

rem Piper for this machine, and the archives the distributions bundle under generic names
call :tool piper PIPER_WINDOWS piper.exe piper || exit /b 1
call :file piper-linux.tar.gz PIPER_LINUX piper-linux.tar.gz || exit /b 1
call :file piper-windows.zip PIPER_WINDOWS piper-windows.zip || exit /b 1

rem Piper's voices: every VOICE_* model and its .onnx.json, under their own names
if not exist "%EXT%\voices" mkdir "%EXT%\voices"
for /f "tokens=1 delims==" %%k in ('set VOICE_ 2^>nul') do (
  set "VMODEL_JSON=!%%k!.json"
  for %%f in ("!%%k!") do set "MODEL=%%~nxf"
  call :file "voices\!MODEL!" %%k "voice-!MODEL!" || exit /b 1
  call :file "voices\!MODEL!.json" VMODEL_JSON "voice-!MODEL!.json" || exit /b 1
)
echo Piper: %EXT%\piper, voices: %EXT%\voices

if "%ANDROID%"=="0" exit /b 0

call :tool gradle ANDROID_GRADLE bin\gradle.bat gradle || exit /b 1
call :tool android-sdk\cmdline-tools\latest ANDROID_CMDLINE_TOOLS_WINDOWS bin\sdkmanager.bat android-cmdline-tools || exit /b 1
set "SDK=%CD%\%EXT%\android-sdk"
set "JAVA_HOME=%CD%\%EXT%\jdk"
echo == the Android SDK licences: read them, and accept them to go on
call "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK%" --licenses
rem each package quoted: their ";" would otherwise split them
set "PACKAGES="!ANDROID_SDK_PACKAGES: =" "!""
echo == installing !ANDROID_SDK_PACKAGES!
call "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK%" --install !PACKAGES!
if "%EMULATOR%"=="1" (
  set "EMULATOR_PACKAGES="!ANDROID_EMULATOR_PACKAGES: =" "!""
  echo == installing !ANDROID_EMULATOR_PACKAGES!
  call "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK%" --install !EMULATOR_PACKAGES!
  set "PACKAGES=!PACKAGES! !EMULATOR_PACKAGES!"
)
rem sdkmanager says nothing by its status of the packages it skipped for want of a licence: each one's directory
rem tells (a package "a;b" lives in <sdk>\a\b)
for %%p in (!PACKAGES!) do (
  set "PACKAGE=%%~p"
  if not exist "%SDK%\!PACKAGE:;=\!" (
    echo %%~p was not installed: accept the licences ^(scripts\windows\setup.bat --android, or --emulator^) >&2
    exit /b 1
  )
)
echo Android SDK: %EXT%\android-sdk
if exist "%EXT%\android-sdk\emulator\emulator.exe" echo Android emulator: %EXT%\android-sdk\emulator ^(scripts\windows\emulator.bat^)
exit /b 0

rem ---- a tool: extracted into external\<%1>, the archive's top-level directory stripped, from the URL held by the
rem      variable named %2; present when its launcher (%3) is and it came from that URL (stamp %4) - a failed
rem      deployment leaves no launcher
:tool
set "DIR=%EXT%\%~1"
set "URL=!%~2!"
call :current "%DIR%\%~3" %~4 && (
  echo == %DIR% present
  exit /b 0
)
call :fetch %~2 %~4 || exit /b 1
if exist "%DIR%" rmdir /s /q "%DIR%"
mkdir "%DIR%"
"%TAR%" -xf "!FILE!" -C "%DIR%" --strip-components 1 || exit /b 1
call :stamp %~4
echo == %DIR% deployed
exit /b 0

rem ---- a file: the download of the URL held by the variable named %2, copied as external\<%1> (stamp %3)
:file
set "TARGET=%EXT%\%~1"
set "URL=!%~2!"
call :current "%TARGET%" %~3 && (
  echo == %TARGET% present
  exit /b 0
)
call :fetch %~2 %~3 || exit /b 1
copy /y "!FILE!" "%TARGET%" >nul || exit /b 1
call :stamp %~3
echo == %TARGET% deployed
exit /b 0

rem ---- succeeds when %1 exists, FORCE is off, and external\.<%2>.url names URL: deployed from the URL asked for
:current
if not "%FORCE%"=="0" exit /b 1
if not exist "%~1" exit /b 1
set "OLD="
if exist "%EXT%\.%~2.url" set /p OLD=<"%EXT%\.%~2.url"
if "!OLD!"=="!URL!" exit /b 0
exit /b 1

rem ---- remembers that the external %1 was deployed from URL
:stamp
>"%EXT%\.%~1.url" echo !URL!
exit /b 0

rem ---- the archive of the URL held by the variable named %1, into the downloads folder unless it is there - or
rem      downloaded anew when the external %2 came from another URL, which may keep the same file name; sets FILE
:fetch
set "URL=!%~1!"
for %%f in ("!URL!") do set "NAME=%%~nxf"
set "SRC=!URL!"
if defined MIRROR set "SRC=!MIRROR!/!NAME!"
set "FILE=%DL%\!NAME!"
set "OLD="
if exist "%EXT%\.%~2.url" set /p OLD=<"%EXT%\.%~2.url"
set "STALE=0"
if defined OLD if not "!OLD!"=="!URL!" set "STALE=1"
if exist "!FILE!" if "%FORCE%"=="0" if "!STALE!"=="0" exit /b 0
echo == downloading !NAME! from !SRC!
"%CURL%" -fL !AUTH! -o "!FILE!.part" "!SRC!" || exit /b 1
move /y "!FILE!.part" "!FILE!" >nul
exit /b 0
