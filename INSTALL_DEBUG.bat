@echo off
setlocal EnableExtensions
title Truelle - Install Debug APK
cd /d "%~dp0"

echo ============================================================
echo  TRUELLE - INSTALL DEBUG APK
echo ============================================================
echo.

set "APK=%~dp0Truelle-debug.apk"

if not exist "%APK%" (
    echo [ERREUR] Truelle-debug.apk n'existe pas.
    echo Lancez d'abord BUILD_DEBUG.bat.
    pause
    exit /b 1
)

if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not defined ADB (
    echo [ERREUR] adb.exe introuvable.
    echo Emplacement attendu :
    echo   %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
    pause
    exit /b 1
)

echo ADB :
echo   %ADB%
echo.

"%ADB%" start-server >nul

echo Appareils connectes :
"%ADB%" devices
echo.

for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" set "DEVICE_FOUND=1"
)

if not defined DEVICE_FOUND (
    echo [ERREUR] Aucun appareil Android autorise detecte.
    echo.
    echo Verifiez :
    echo   - le debogage USB est active
    echo   - le cable USB est branche
    echo   - la demande d'autorisation a ete acceptee sur le Pixel
    pause
    exit /b 1
)

echo Installation...
"%ADB%" install -r "%APK%"

if errorlevel 1 (
    echo.
    echo [ERREUR] L'installation de l'APK a echoue.
    echo Si une ancienne version installee depuis GitHub bloque, desinstallez Truelle du Pixel puis relancez.
    pause
    exit /b 1
)

echo.
echo Lancement de Truelle...
"%ADB%" shell am start -n com.gregory.truelle/.MainActivity >nul

echo.
echo ============================================================
echo  INSTALLATION REUSSIE
echo ============================================================
echo.
echo Truelle est ouverte sur le Pixel.
echo.
pause
