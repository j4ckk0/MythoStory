@echo off
rem Called by MythoStory's Android scripts once externals.bat has run: the Android SDK's tools, once
rem scripts\windows\setup.bat --android has deployed it - Maven's profile "android" then switches itself on, and the build
rem makes the APK too. Sets ANDROID (1 or 0), ANDROID_HOME, and adb in PATH. No setlocal: the caller keeps what is set here.
set "ANDROID=0"
if not exist "%CD%\external\android-sdk\platforms" exit /b 0
if not exist "%CD%\external\gradle\bin\gradle.bat" (
  echo external\android-sdk is there, not external\gradle: run scripts\windows\setup.bat --android >&2
  exit /b 1
)
set "ANDROID=1"
set "ANDROID_HOME=%CD%\external\android-sdk"
set "PATH=%CD%\external\android-sdk\platform-tools;%PATH%"
exit /b 0
