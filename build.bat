@echo off
setlocal
if not defined JAVA_HOME (
  for /f "delims=" %%J in ('where java ^| findstr /i "\\java.exe$" ^| sort /r') do (
    set "JAVA_EXE=%%J"
    goto foundJava
  )
  echo ERROR: java.exe not found in PATH
  exit /b 1
)
goto gotJava
:foundJava
for %%I in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpI.."
:gotJava
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: JAVA_HOME is not set correctly: %JAVA_HOME%
  exit /b 1
)
call "%~dp0mvnw.cmd" clean package
if errorlevel 1 exit /b 1
echo Build successful.
