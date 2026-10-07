@echo off
rem
rem Stops MythoStory: the desktop servers running on this machine — from scripts\run.bat or from a distribution
rem (mythostory.jar). Windows has no gentle signal for a console program: the process is ended; its Piper voices end
rem with it, their input closed.
rem
rem   scripts\stop.bat              every MythoStory server
rem   scripts\stop.bat --port 9090  the one serving that port (8080 when started without --port)
rem
setlocal
set "PORT="
if /i "%~1"=="--port" set "PORT=%~2"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$port = '%PORT%';" ^
  "$servers = Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -match 'mythostory(-run)?\.jar' } |" ^
  "  Where-Object { $served = if ($_.CommandLine -match '--port\s+(\d+)') { $Matches[1] } else { '8080' }; -not $port -or $served -eq $port };" ^
  "if (-not $servers) { 'No MythoStory server is running.'; exit 0 };" ^
  "foreach ($s in $servers) { Stop-Process -Id $s.ProcessId -Force; '== MythoStory stopped (pid ' + $s.ProcessId + ')' }"
exit /b %errorlevel%
