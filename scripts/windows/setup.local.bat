@echo off
rem MythoStory's own externals on Windows, called by scripts\windows\setup.bat once the devkit's are deployed, with its
rem arguments (EXT, DL, FORCE, CURL, TAR, AUTH and externals.conf's keys at hand): with --android, what the Android
rem app's build needs (about a gigabyte) - external\gradle, then external\android-sdk: its command-line tools, the
rem packages ANDROID_SDK_PACKAGES names, the licences accepted; --emulator adds the emulator and its system image
rem (ANDROID_EMULATOR_PACKAGES, 2 to 3 GB), which scripts\windows\emulator.bat runs the app in.
rem
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
if "%ANDROID%"=="0" exit /b 0

call :tool gradle ANDROID_GRADLE bin\gradle.bat || exit /b 1
call :tool android-sdk\cmdline-tools\latest ANDROID_CMDLINE_TOOLS_WINDOWS bin\sdkmanager.bat || exit /b 1
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
    echo %%~p was not installed: accept the licences ^(scripts\windows\setup.bat --android^) >&2
    exit /b 1
  )
)
echo Android SDK: %EXT%\android-sdk
if exist "%EXT%\android-sdk\emulator\emulator.exe" echo Android emulator: %EXT%\android-sdk\emulator ^(scripts\windows\emulator.bat^)
exit /b 0

rem ---- a tool: extracted into external\<name>, the archive's top-level directory stripped; present when its
rem      launcher (%3) is - a failed deployment leaves none
:tool
set "DIR=%EXT%\%~1"
if exist "%DIR%\%~3" if "%FORCE%"=="0" (
  echo == %DIR% present
  exit /b 0
)
call :fetch %~2 || exit /b 1
if exist "%DIR%" rmdir /s /q "%DIR%"
mkdir "%DIR%"
"%TAR%" -xf "!FILE!" -C "%DIR%" --strip-components 1 || exit /b 1
echo == %DIR% deployed
exit /b 0

rem ---- the archive of the URL held by the variable named %1, into the downloads folder unless it is there; sets FILE
:fetch
set "URL=!%~1!"
for %%f in ("!URL!") do set "NAME=%%~nxf"
set "SRC=!URL!"
if defined MIRROR set "SRC=!MIRROR!/!NAME!"
set "FILE=%DL%\!NAME!"
if exist "!FILE!" if "%FORCE%"=="0" exit /b 0
echo == downloading !NAME! from !SRC!
"%CURL%" -fL !AUTH! -o "!FILE!.part" "!SRC!" || exit /b 1
move /y "!FILE!.part" "!FILE!" >nul
exit /b 0
