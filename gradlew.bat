@echo off
setlocal enabledelayedexpansion
set GRADLE_VERSION=8.11.1
if "%GRADLE_USER_HOME%"=="" (
  set BASE_DIR=%USERPROFILE%\.gradle\vadana-bootstrap
) else (
  set BASE_DIR=%GRADLE_USER_HOME%\vadana-bootstrap
)
set DIST_DIR=%BASE_DIR%\gradle-%GRADLE_VERSION%
set ZIP_FILE=%BASE_DIR%\gradle-%GRADLE_VERSION%-bin.zip

if not exist "%DIST_DIR%\bin\gradle.bat" (
  if not exist "%BASE_DIR%" mkdir "%BASE_DIR%"
  if not exist "%ZIP_FILE%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
    if errorlevel 1 exit /b 1
  )
  if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%BASE_DIR%' -Force"
  if errorlevel 1 exit /b 1
)

call "%DIST_DIR%\bin\gradle.bat" %*
endlocal
