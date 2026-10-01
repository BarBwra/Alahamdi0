@echo off
setlocal enabledelayedexpansion
title MlumInventory - build and install

echo.
echo  ============================================
echo   MLUM INVENTORY  -  build ^& install
echo  ============================================
echo.

cd /d "%~dp0"

REM ---------------------------------------------------------------- find a JDK
set "JAVA_BIN="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_BIN (
    where java >nul 2>nul && set "JAVA_BIN=java"
)
if not defined JAVA_BIN (
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-17*") do (
        if exist "%%D\bin\java.exe" set "JAVA_BIN=%%D\bin\java.exe"
    )
)
if not defined JAVA_BIN (
    for /d %%D in ("%ProgramFiles%\Java\jdk-17*") do (
        if exist "%%D\bin\java.exe" set "JAVA_BIN=%%D\bin\java.exe"
    )
)

if not defined JAVA_BIN (
    echo  [X] No Java found.
    echo.
    echo      Install JDK 17 from https://adoptium.net/temurin/releases/?version=17
    echo      then run this file again.
    echo.
    pause
    exit /b 1
)

echo  [1/3] Java: %JAVA_BIN%
"%JAVA_BIN%" -version 2>&1 | findstr /i "version"
echo.

REM -------------------------------------------------------------------- build
echo  [2/3] Building. First run downloads Gradle + Forge and decompiles
echo        Minecraft - expect 5 to 15 minutes. Later builds take seconds.
echo.

call gradlew.bat build --console=plain
if errorlevel 1 (
    echo.
    echo  [X] Build failed. Scroll up for the first error line.
    echo      Most common cause: the JDK found above is older than 17.
    echo.
    pause
    exit /b 1
)

REM ---------------------------------------------------------------- find jar
set "JAR="
for %%F in ("build\libs\mlum-*.jar") do (
    echo %%~nxF | findstr /i "sources dev" >nul || set "JAR=%%~fF"
)
if not defined JAR (
    echo  [X] Build succeeded but no jar was found in build\libs.
    pause
    exit /b 1
)
echo.
echo  Built: !JAR!

REM ------------------------------------------------------------- install path
set "MODS=%~1"
if not defined MODS set "MODS=%USERPROFILE%\curseforge\minecraft\Instances\claude\mods"

if not exist "%MODS%" (
    echo.
    echo  [!] Could not find the pack folder:
    echo        %MODS%
    echo.
    echo      Drag your instance's "mods" folder onto this .bat file, or copy
    echo      the jar there yourself. The jar is at:
    echo        !JAR!
    echo.
    pause
    exit /b 0
)

echo  [3/3] Installing to %MODS%
del /q "%MODS%\mlum-*.jar" 2>nul
copy /y "!JAR!" "%MODS%\" >nul
if errorlevel 1 (
    echo  [X] Copy failed - is Minecraft running?
    pause
    exit /b 1
)

echo.
echo  ============================================
echo   Done. Launch the pack in CurseForge and
echo   press E.
echo  ============================================
echo.
pause
