@echo off
REM Build the distributable library pack.
REM
REM What it does:
REM   1. Runs `./gradlew dist`, which compiles the mod jar, then strips `fabric.mod.json`
REM      out of it to produce a slim library jar.
REM   2. Bundles that jar together with LICENSE.txt and library-README.md into
REM      dist\<name>-lib-<version>.zip.
REM
REM To use:
REM   - Drop the .jar inside the zip into another modder's /libs/ folder, or distribute the
REM     .zip as the public release payload.
REM
REM Optional flags (forward to gradlew):
REM   CLEAN=1       Run `gradlew clean` first to wipe stale incremental state.

setlocal
cd /d "%~dp0"

if defined CLEAN (
    echo Running gradlew clean...
    call gradlew.bat clean
    if errorlevel 1 goto :fail
)

echo Running gradlew dist...
call gradlew.bat dist %*
if errorlevel 1 goto :fail

echo.
echo ============================================
echo Library pack built under %CD%\dist\
echo ============================================
echo.
echo Contents:
dir /b dist
endlocal
exit /b 0

:fail
echo.
echo Build failed. See the gradle output above.
endlocal
exit /b 1
