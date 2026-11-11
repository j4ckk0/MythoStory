@echo off
rem
rem Runs MythoStory in the Android emulator on Windows: starts the emulator's virtual phone — created on the first
rem run, in external\android-avd —, waits for it to boot, then builds the app if needed, installs it and starts it
rem there (scripts\windows\run-android.bat). The emulator keeps running afterwards: run this again after a change.
rem Needs the emulator and its system image: scripts\windows\setup.bat --emulator, once (offered when they are missing).
rem
rem   scripts\windows\emulator.bat                 start the phone (or use the one running), install and start the app
rem   scripts\windows\emulator.bat --rebuild       force a rebuild of the app first
rem   scripts\windows\emulator.bat --wipe          the phone as new: its data wiped as it starts
rem   scripts\windows\emulator.bat --no-window     the phone without its window
rem   scripts\windows\emulator.bat --stop          stop the phone
rem
setlocal EnableDelayedExpansion
cd /d "%~dp0..\.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
call "%~dp0android-env.bat" || exit /b 1
rem (setup done? it is offered otherwise; the externals first; then ANDROID and ANDROID_HOME)

set "AVD=mythostory"
set "LOG=tmp\emulator.log"
set "BOOT_TIMEOUT=300"

set "WIPE=0"
set "WINDOW=1"
set "STOP=0"
set "ARGS="
for %%a in (%*) do (
  if /i "%%~a"=="--wipe" (
    set "WIPE=1"
  ) else if /i "%%~a"=="--no-window" (
    set "WINDOW=0"
  ) else if /i "%%~a"=="--stop" (
    set "STOP=1"
  ) else (
    set "ARGS=!ARGS! %%a"
  )
)

rem externals.conf's ANDROID_EMULATOR_PACKAGES and ANDROID_AVD_DEVICE: the environment wins, then externals.local.conf
if exist "externals.local.conf" for /f "usebackq eol=# tokens=1* delims==" %%a in ("externals.local.conf") do if not defined %%a set "%%a=%%b"
for /f "usebackq eol=# tokens=1* delims==" %%a in ("externals.conf") do if not defined %%a set "%%a=%%b"
set "IMAGE="
for %%p in ("!ANDROID_EMULATOR_PACKAGES: =" "!") do (
  set "PACKAGE=%%~p"
  if "!PACKAGE:~0,14!"=="system-images;" set "IMAGE=!PACKAGE!"
)

set "EMULATOR=%ANDROID_HOME%\emulator\emulator.exe"
set "SET_UP=1"
if "%ANDROID%"=="0" set "SET_UP=0"
if not exist "%EMULATOR%" set "SET_UP=0"
if not defined IMAGE set "SET_UP=0"
if defined IMAGE if not exist "%ANDROID_HOME%\!IMAGE:;=\!" set "SET_UP=0"
if "%SET_UP%"=="0" (
  echo The Android emulator is not set up: scripts\windows\setup.bat --emulator installs it, once ^(the Android SDK and its >&2
  echo licences, the emulator and its system image: 3 to 4 GB^). >&2
  rem as externals.bat does: offer to run it, No after 30 s so that the CI does not hang
  choice /c YN /n /t 30 /d N /m "Run scripts\windows\setup.bat --emulator now? [Y/N] "
  if errorlevel 2 exit /b 1
  call "%~dp0setup.bat" --emulator || exit /b 1
  rem setup.bat has just made the Android SDK: its tools into PATH, as externals.bat does
  call "%~dp0externals.bat" || exit /b 1
  call "%~dp0android-env.bat" || exit /b 1
  if not exist "%ANDROID_HOME%\emulator\emulator.exe" (
    echo the emulator is still not set up: see setup.bat's messages above >&2
    exit /b 1
  )
  set "EMULATOR=!ANDROID_HOME!\emulator\emulator.exe"
)
rem the virtual phone under external\, with the rest of the externals
set "ANDROID_AVD_HOME=%CD%\external\android-avd"
if not exist "%ANDROID_AVD_HOME%" mkdir "%ANDROID_AVD_HOME%"
if not exist tmp mkdir tmp

call :findSerial
if "%STOP%"=="1" (
  if not defined SERIAL (
    echo The MythoStory phone is not running.
    exit /b 0
  )
  echo == stopping the MythoStory phone ^(!SERIAL!^)
  adb -s !SERIAL! emu kill >nul
  exit /b 0
)

if not exist "%ANDROID_AVD_HOME%\%AVD%.ini" (
  echo == creating the virtual phone %AVD%: a !ANDROID_AVD_DEVICE! on !IMAGE!, in %ANDROID_AVD_HOME%
  rem "no": no custom hardware profile
  echo no| "%ANDROID_HOME%\cmdline-tools\latest\bin\avdmanager.bat" create avd --name "%AVD%" --package "!IMAGE!" --device "!ANDROID_AVD_DEVICE!" --path "%ANDROID_AVD_HOME%\%AVD%.avd" >nul || exit /b 1
)

if defined SERIAL (
  if "%WIPE%"=="1" (
    echo --wipe: the phone is already running; stop it first ^(scripts\windows\emulator.bat --stop^) >&2
    exit /b 1
  )
  echo == the MythoStory phone is running ^(!SERIAL!^)
  goto :run
)

rem the first free port of the emulator's range; the serial is emulator-<port>
set "PORT=5554"
:port
adb devices | findstr /b /c:"emulator-!PORT!" >nul && (
  set /a PORT+=2
  goto :port
)
if !PORT! gtr 5584 (
  echo no free emulator port between 5554 and 5584 >&2
  exit /b 1
)
set "SERIAL=emulator-!PORT!"
set "OPTIONS=-avd %AVD% -port !PORT! -no-boot-anim"
if "%WIPE%"=="1" set "OPTIONS=!OPTIONS! -wipe-data"
if "%WINDOW%"=="0" set "OPTIONS=!OPTIONS! -no-window -no-audio"
echo == starting the MythoStory phone ^(!SERIAL!; its log: %LOG%^)
rem in a console of its own, minimised: the phone outlives this script, which hands over to run-android.bat
start "MythoStory phone" /min cmd /c ""%EMULATOR%" !OPTIONS! ^> "%CD%\%LOG%" 2^>^&1"
echo == waiting for it to boot
set /a WAITED=0
:boot
set "BOOTED="
for /f %%b in ('adb -s !SERIAL! shell getprop sys.boot_completed 2^>nul') do set "BOOTED=%%b"
if "!BOOTED!"=="1" goto :booted
if !WAITED! geq %BOOT_TIMEOUT% (
  echo the phone has not booted after %BOOT_TIMEOUT% s: see %LOG% >&2
  exit /b 1
)
timeout /t 3 /nobreak >nul
set /a WAITED+=3
goto :boot
:booted
echo == booted

:run
rem run-android.bat installs with a bare adb: this phone, whatever else adb sees
set "ANDROID_SERIAL=!SERIAL!"
call "%~dp0run-android.bat" !ARGS!
exit /b !errorlevel!

rem ---- the serial of the emulator running our phone, found by the phone's name, in SERIAL (undefined when none)
:findSerial
set "SERIAL="
for /f "tokens=1,2" %%s in ('adb devices') do (
  if "%%t"=="device" (
    set "CANDIDATE=%%s"
    if "!CANDIDATE:~0,9!"=="emulator-" (
      set "NAME="
      for /f "delims=" %%n in ('adb -s %%s emu avd name 2^>nul') do if not defined NAME set "NAME=%%n"
      if "!NAME!"=="%AVD%" set "SERIAL=%%s"
    )
  )
)
exit /b 0
