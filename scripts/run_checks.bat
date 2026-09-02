@echo off
REM LegionTube Verification & Quality Assurance Check Script
echo ===================================================
echo LegionTube Code Verification & Test Script
echo ===================================================

cd /d "%~dp0\.."

echo.
echo [1/2] Compiling Kotlin code...
call gradlew.bat compileGithubDebugKotlin --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo Error: Kotlin compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Running Unit Tests...
call gradlew.bat testGithubDebugUnitTest --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo Error: Unit tests failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo SUCCESS: All compilation and verification checks passed!
echo ===================================================
