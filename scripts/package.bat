@echo off
rem
rem Build the distributions into releases\:
rem   releases\mythostory-<version>-windows.zip      the desktop: jar + bundled JRE + mythostory.bat
rem   releases\mythostory-<version>-linux.tar.gz     the desktop: jar + bundled JRE + mythostory.sh
rem   releases\mythostory-<version>-android.apk      the Android app, when its externals are there (setup.bat --android);
rem                                                  signed with android\keystore.properties' key, the debug key without it
rem
rem   scripts\package.bat
rem   scripts\package.bat --no-android    the desktop's alone (first argument)
rem   scripts\package.bat -DskipTests     extra arguments are passed to mvn
rem
rem The JRE archives come from external\ (scripts\setup.bat deploys them; scripts\externals.bat checks they are there).
rem The releases of the previous versions are removed once the new one is built: releases\ holds the release
rem of the version pom.xml names, and whatever else you put there is left alone.
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (setup done? it is offered otherwise; the externals first)
set "PROFILE="
if /i "%~1"=="--no-android" set "PROFILE=%NO_ANDROID_PROFILE%"
if /i "%~1"=="--no-android" set "ANDROID=0"
if /i "%~1"=="--no-android" shift
call :running || exit /b 1

call mvn clean package -Pdist %PROFILE% %1 %2 %3 %4 %5 %6 %7 %8 %9 || exit /b 1

rem The releases of the previous versions go, now that the new one is there (only ours, named after another version).
for /f "delims=" %%l in ('findstr /r /c:"<revision>.*</revision>" pom.xml') do if not defined VERSION_LINE set "VERSION_LINE=%%l"
set "VERSION=%VERSION_LINE:*<revision>=%"
set "VERSION=%VERSION:</revision>=%"
for %%a in (releases\mythostory-*-windows.zip releases\mythostory-*-linux.tar.gz releases\mythostory-*-android.apk) do call :previous "%%a"

rem What every desktop distribution must carry: the jar, its launcher, the bundled JRE.
call :carries releases\mythostory-%VERSION%-windows.zip mythostory.jar mythostory.bat jre/bin/java.exe piper/piper.exe voices/fr_FR-siwis-medium.onnx voices/en_GB-alba-medium.onnx VOICES.md || exit /b 1
call :carries releases\mythostory-%VERSION%-linux.tar.gz mythostory.jar mythostory.sh jre/bin/java piper/piper voices/fr_FR-siwis-medium.onnx voices/en_GB-alba-medium.onnx VOICES.md || exit /b 1
echo == releases\ checked: mythostory-%VERSION%, the jar, the launcher and the JRE in each
if "%ANDROID%"=="0" exit /b 0
rem The APK: the same page and server inside
set "APK=releases\mythostory-%VERSION%-android.apk"
if not exist "%APK%" (
  echo %APK% was not built >&2
  exit /b 1
)
for %%f in (classes.dex static/index.html mythostory.properties) do (
  tar -tf "%APK%" | findstr /x /c:"%%f" >nul || ( echo %APK%: no %%f >&2 & exit /b 1 )
)
if not exist android\keystore.properties echo == %APK% is signed with the debug key: installable for testing, not publishable ^(android\build.gradle.kts^)
echo == %APK% checked: the page and the server inside
exit /b 0

rem ---- an archive (%1) and the files its mythostory-<version>\ must hold
:carries
if not exist "%~1" (
  echo %~1 was not built >&2
  exit /b 1
)
set "ARCHIVE=%~1"
:carriesNext
shift
if "%~1"=="" exit /b 0
tar -tf "%ARCHIVE%" | findstr /x /c:"mythostory-%VERSION%/%~1" >nul || ( echo %ARCHIVE%: no %~1 >&2 & exit /b 1 )
goto :carriesNext

rem ---- MythoStory running from the jar? the build would fail deleting it, saying nothing of the cause
:running
wmic process where "name='java.exe'" get commandline 2>nul | findstr /i "mythostory.jar" >nul || exit /b 0
echo MythoStory is running: stop it before packaging, or it holds the jar the build replaces. >&2
exit /b 1

rem ---- an archive of another version: removed
:previous
echo %~nx1 | findstr /c:"-%VERSION%-" >nul && exit /b 0
del /q "%~1"
echo == %~1 removed ^(previous release^)
exit /b 0
