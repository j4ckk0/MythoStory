@echo off
rem
rem Called by the scripts, from the project root: makes sure scripts\setup.bat has been run — everything it
rem deploys is there: external\jdk, maven, node, jre-linux.tar.gz, jre-windows.zip — offering to run it
rem when something is missing (a prompt with a 30 s timeout, No by default: the CI does not hang). Then puts
rem the externals in front: external\jdk as JAVA_HOME, its java.exe, Maven's mvn.cmd and Node's node.exe and
rem npm.cmd first in PATH (Maven runs npm to build the frontend). No setlocal: the caller keeps what is set here.
rem Only generic names: the versions are externals.conf's business.
rem
rem The Android app is built too as soon as scripts\setup.bat --android has deployed external\gradle and
rem external\android-sdk (Maven's profile "android" switches itself on): ANDROID is then 1, and a script's
rem --no-android leaves it out (passed to Maven as NO_ANDROID_PROFILE, -P-android).
rem
rem Sets JAR, the jar the build makes.
rem
set "EXT=%CD%\external"
call :missing
if defined MISSING (
  echo external\ is not set up: missing%MISSING% ^(scripts\setup.bat deploys them, see README: Externals^) >&2
  choice /c YN /n /t 30 /d N /m "Run scripts\setup.bat now? [Y/N] "
  if errorlevel 2 exit /b 1
  call "%~dp0setup.bat" || exit /b 1
  call :missing
  if defined MISSING (
    echo still missing%MISSING% >&2
    exit /b 1
  )
)
set "JAVA_HOME=%EXT%\jdk"
set "PATH=%EXT%\jdk\bin;%EXT%\maven\bin;%EXT%\node;%PATH%"
set "JAR=desktop\target\mythostory.jar"
set "NO_ANDROID_PROFILE=-P-android"
set "ANDROID=0"
if exist "%EXT%\android-sdk\platforms" (
  if not exist "%EXT%\gradle\bin\gradle.bat" (
    echo external\android-sdk is there, not external\gradle: run scripts\setup.bat --android >&2
    exit /b 1
  )
  set "ANDROID=1"
  set "ANDROID_HOME=%EXT%\android-sdk"
  set "PATH=%EXT%\gradle\bin;%EXT%\android-sdk\platform-tools;%PATH%"
)
exit /b 0

rem ---- what setup deploys and is not there, in MISSING (undefined when all is there)
:missing
set "MISSING="
if not exist "%EXT%\jdk\bin\java.exe" set "MISSING=%MISSING% jdk\"
if not exist "%EXT%\maven\bin\mvn.cmd" set "MISSING=%MISSING% maven\"
if not exist "%EXT%\node\node.exe" set "MISSING=%MISSING% node\"
if not exist "%EXT%\jre-linux.tar.gz" set "MISSING=%MISSING% jre-linux.tar.gz"
if not exist "%EXT%\jre-windows.zip" set "MISSING=%MISSING% jre-windows.zip"
exit /b 0
