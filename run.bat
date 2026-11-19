@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not exist bin mkdir bin

set "FILES="
for /r src %%f in (*.java) do (
    set "FILES=!FILES! "%%f""
)

javac -cp "lib\flatlaf.jar;lib\mssql-jdbc.jar" -d bin !FILES!
if %errorlevel% neq 0 (
    echo Compile failed.
    pause
    exit /b 1
)

java -cp "bin;lib\flatlaf.jar;lib\mssql-jdbc.jar" Main
pause
