@echo off
REM LegionTube Windows Build & Release Automation Script
echo ===================================================
echo LegionTube Production Release Build Script
echo ===================================================

cd /d "%~dp0\.."

echo.
echo [1/3] Cleaning build cache...
call gradlew.bat clean --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo Error: Gradle clean failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Building GitHub Release APK...
call gradlew.bat assembleGithubRelease --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo Error: GitHub Release APK build failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Building FOSS Release APK...
call gradlew.bat assembleFossRelease --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo Error: FOSS Release APK build failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo SUCCESS: All Release APKs built successfully!
echo Artifacts located in app\build\outputs\apk\
echo ===================================================
