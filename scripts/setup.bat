@echo off
rem
rem Deploys the project's externals into external\ (not under source control), from externals.conf:
rem   external\jdk, external\maven, external\node          extracted, their top-level directory stripped
rem   external\jre-linux.tar.gz, external\jre-windows.zip  the JRE archives the distributions bundle
rem   external\downloads\                                  the archives as downloaded (kept)
rem and with --android, what the Android app's build needs (about a gigabyte):
rem   external\gradle                                      Gradle, which builds the APK (android\, run by Maven)
rem   external\android-sdk                                 the Android SDK: its command-line tools, then the
rem                                                        packages ANDROID_SDK_PACKAGES names, the licences accepted
rem
rem   scripts\setup.bat            deploys what is missing
rem   scripts\setup.bat --android  the above, plus the Android build's externals (the build then makes the APK too)
rem   scripts\setup.bat --emulator the above with --android, plus the Android emulator and its system image
rem                                (ANDROID_EMULATOR_PACKAGES, 2 to 3 GB), which scripts\emulator.bat runs the app in
rem   scripts\setup.bat --force    downloads and deploys everything again
rem
rem MIRROR=<url> in externals.conf, or in the environment, fetches <MIRROR>/<file name> instead of the URLs;
rem MIRROR_TOKEN (+ MIRROR_TOKEN_HEADER) or MIRROR_USER / MIRROR_PASSWORD authenticate there — from
rem externals.local.conf (not under source control) or the environment rather than externals.conf.
rem Needs curl and tar, both in Windows 10 (1803) and later.
rem
setlocal EnableDelayedExpansion
cd /d "%~dp0.."
rem (the project root)

set "CONF=externals.conf"
set "LOCAL_CONF=externals.local.conf"
set "EXT=external"
set "DL=%EXT%\downloads"
set "FORCE=0"
set "ANDROID=0"
set "EMULATOR=0"
:args
if "%~1"=="" goto :argsDone
if /i "%~1"=="--force" (
  set "FORCE=1"
) else if /i "%~1"=="--android" (
  set "ANDROID=1"
) else if /i "%~1"=="--emulator" (
  set "ANDROID=1"
  set "EMULATOR=1"
) else (
  echo unknown option: %~1 ^(try --force, --android, --emulator^) >&2
  exit /b 1
)
shift
goto :args
:argsDone

for %%t in (curl tar) do where %%t >nul 2>nul || (
  echo %%t not found ^(Windows 10 1803 and later have it^): install it, then run setup again >&2
  exit /b 1
)

rem externals.local.conf then externals.conf: KEY=VALUE lines, # comments; what is set already (the environment,
rem then the local file) wins
if exist "%LOCAL_CONF%" for /f "usebackq eol=# tokens=1* delims==" %%a in ("%LOCAL_CONF%") do if not defined %%a set "%%a=%%b"
for /f "usebackq eol=# tokens=1* delims==" %%a in ("%CONF%") do if not defined %%a set "%%a=%%b"

rem the authentication of the mirror, as curl arguments (none for the public URLs)
set "AUTH="
if defined MIRROR (
  if defined MIRROR_TOKEN (
    if not defined MIRROR_TOKEN_HEADER set "MIRROR_TOKEN_HEADER=Authorization: Bearer"
    set "AUTH=-H "!MIRROR_TOKEN_HEADER! !MIRROR_TOKEN!""
  )
  if defined MIRROR_USER set "AUTH=!AUTH! -u "!MIRROR_USER!:!MIRROR_PASSWORD!""
)

if not exist "%DL%" mkdir "%DL%"

call :tool jdk JDK_WINDOWS bin\java.exe || exit /b 1
call :tool maven MAVEN_WINDOWS bin\mvn.cmd || exit /b 1
call :tool node NODE_WINDOWS node.exe || exit /b 1
call :archive jre-linux.tar.gz JRE_LINUX || exit /b 1
call :archive jre-windows.zip JRE_WINDOWS || exit /b 1
if "%ANDROID%"=="1" call :android || exit /b 1

echo == external\ ready:
"%EXT%\jdk\bin\java.exe" -version 2>&1 | findstr /i "version"
set "JAVA_HOME=%CD%\%EXT%\jdk"
call "%EXT%\maven\bin\mvn.cmd" -v 2>nul | findstr /i "Apache Maven"
for /f %%v in ('"%EXT%\node\node.exe" --version') do echo Node.js %%v
if exist "%EXT%\gradle\bin\gradle.bat" echo Gradle: %EXT%\gradle
if exist "%EXT%\android-sdk\platforms" echo Android SDK: %EXT%\android-sdk
if exist "%EXT%\android-sdk\emulator\emulator.exe" echo Android emulator: %EXT%\android-sdk\emulator ^(scripts\emulator.bat^)
exit /b 0

rem ---- a tool: extracted into external\<name>, the archive's top-level directory stripped; present when its
rem      launcher (%3) is — a failed deployment leaves none
:tool
set "DIR=%EXT%\%~1"
if exist "%DIR%\%~3" if "%FORCE%"=="0" (
  echo == %DIR% present
  exit /b 0
)
call :fetch %~2 || exit /b 1
if exist "%DIR%" rmdir /s /q "%DIR%"
mkdir "%DIR%"
tar -xf "!FILE!" -C "%DIR%" --strip-components 1 || exit /b 1
echo == %DIR% deployed
exit /b 0

rem ---- a JRE archive: copied as it is under its generic name
:archive
set "TARGET=%EXT%\%~1"
if exist "%TARGET%" if "%FORCE%"=="0" (
  echo == %TARGET% present
  exit /b 0
)
call :fetch %~2 || exit /b 1
copy /y "!FILE!" "%TARGET%" >nul || exit /b 1
echo == %TARGET% deployed
exit /b 0

rem ---- the Android build's externals: Gradle, the SDK's command-line tools where sdkmanager expects to find
rem      itself (<sdk>\cmdline-tools\latest), then the packages, the licences accepted
:android
call :tool gradle GRADLE bin\gradle.bat || exit /b 1
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
    echo %%~p was not installed: accept the licences ^(scripts\setup.bat --android^) >&2
    exit /b 1
  )
)
exit /b 0

rem ---- the archive of the URL held by the variable named %1, into external\downloads\ unless it is there; sets FILE
:fetch
set "URL=!%~1!"
for %%f in ("!URL!") do set "NAME=%%~nxf"
set "SRC=!URL!"
if defined MIRROR set "SRC=!MIRROR!/!NAME!"
set "FILE=%DL%\!NAME!"
if exist "!FILE!" if "%FORCE%"=="0" exit /b 0
echo == downloading !NAME! from !SRC!
curl -fL !AUTH! -o "!FILE!.part" "!SRC!" || exit /b 1
move /y "!FILE!.part" "!FILE!" >nul
exit /b 0
