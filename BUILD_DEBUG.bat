@echo off
setlocal EnableExtensions EnableDelayedExpansion

title Truelle - Build Debug APK
cd /d "%~dp0"

echo ============================================================
echo  TRUELLE - BUILD DEBUG APK
echo ============================================================
echo.

if not exist "%~dp0settings.gradle" (
    echo [ERREUR] Projet Android introuvable dans :
    echo          %~dp0
    pause
    exit /b 1
)

echo [1/4] Verification de Java...
where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo [ERREUR] Java introuvable dans le PATH.
    echo Installez le JDK 17, puis rouvrez cette fenetre.
    pause
    exit /b 1
)
echo       Java detecte :
java -version 2>&1

echo.
echo [2/4] Recherche du SDK Android...
if defined ANDROID_HOME if exist "%ANDROID_HOME%" set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%" set "SDK_DIR=%ANDROID_SDK_ROOT%"
if not defined SDK_DIR if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"

if not defined SDK_DIR (
    echo.
    echo [ERREUR] SDK Android introuvable.
    echo Emplacements testes :
    echo   ANDROID_HOME
    echo   ANDROID_SDK_ROOT
    echo   %LOCALAPPDATA%\Android\Sdk
    pause
    exit /b 1
)
echo       SDK : %SDK_DIR%

> "%~dp0local.properties" echo sdk.dir=%SDK_DIR:\=\\%

if not exist "%SDK_DIR%\platforms\android-36" (
    echo.
    echo [ATTENTION] La plateforme Android 36 n'est pas installee.
    echo Gradle va tenter de la telecharger. En cas d'echec, lancez :
    echo   "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "build-tools;36.0.0"
    echo.
)

echo.
echo [3/4] Compilation de Truelle (Gradle 8.14.3 via le wrapper, telecharge au premier lancement)...
call "%~dp0gradlew.bat" --no-daemon assembleDebug
set "BUILD_RESULT=%ERRORLEVEL%"

if not "%BUILD_RESULT%"=="0" (
    echo.
    echo ============================================================
    echo  ECHEC DE LA COMPILATION
    echo ============================================================
    pause
    exit /b %BUILD_RESULT%
)

echo.
echo [4/4] Copie de l'APK...
set "APK_SOURCE=%~dp0app\build\outputs\apk\debug\app-debug.apk"
set "APK_DEST=%~dp0Truelle-debug.apk"

if not exist "%APK_SOURCE%" (
    echo [ERREUR] Compilation terminee mais APK introuvable :
    echo          %APK_SOURCE%
    pause
    exit /b 1
)

copy /y "%APK_SOURCE%" "%APK_DEST%" >nul

echo.
echo ============================================================
echo  COMPILATION REUSSIE
echo ============================================================
echo.
echo APK :
echo   %APK_DEST%
echo.
echo Ensuite :
echo   1. Branchez le Pixel en USB (debogage USB active)
echo   2. Double-cliquez sur INSTALL_DEBUG.bat
echo.
pause
