@echo off
cd /d "%~dp0"
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -cp "lib\*" -d bin @sources.txt
if errorlevel 1 (
  echo Compile failed.
  pause
  exit /b 1
)
del sources.txt
java -cp "bin;lib\*" Main
pause
