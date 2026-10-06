@echo off
rem Run MythoStory with the bundled JRE, in a console. Arguments are passed to the program:
rem   mythostory.bat [--port N] [--host H] [--no-browser]
"%~dp0jre\bin\java.exe" -jar "%~dp0mythostory.jar" %*
