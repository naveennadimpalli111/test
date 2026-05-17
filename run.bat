@echo off
setlocal
call "%~dp0build.bat" || exit /b 1
if not exist "%~dp0target\its255-web-viewer-1.1.0.jar" (
  echo ERROR: Jar not found after build.
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -jar "%~dp0target\its255-web-viewer-1.1.0.jar"
