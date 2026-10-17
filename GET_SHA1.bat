@echo off
setlocal EnableExtensions
title Truelle - SHA-1 de la cle de debug
cd /d "%~dp0"

echo ============================================================
echo  TRUELLE - EMPREINTE SHA-1 POUR GOOGLE OAUTH
echo ============================================================
echo.

set "KEYTOOL="
if defined JAVA17_HOME if exist "%JAVA17_HOME%\bin\keytool.exe" set "KEYTOOL=%JAVA17_HOME%\bin\keytool.exe"
if not defined KEYTOOL if defined JAVA_HOME if exist "%JAVA_HOME%\bin\keytool.exe" set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
if not defined KEYTOOL for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if exist "%%~fD\bin\keytool.exe" set "KEYTOOL=%%~fD\bin\keytool.exe"
if not defined KEYTOOL if exist "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" set "KEYTOOL=C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
if not defined KEYTOOL for /f "delims=" %%K in ('where keytool 2^>nul') do if not defined KEYTOOL set "KEYTOOL=%%K"

if not defined KEYTOOL (
    echo [ERREUR] keytool introuvable. Le JDK 17 doit etre installe.
    pause
    exit /b 1
)

set "KS=%USERPROFILE%\.android\debug.keystore"
if not exist "%KS%" (
    echo [ERREUR] Cle de debug introuvable : %KS%
    echo Lancez BUILD_DEBUG.bat une fois pour la creer, puis relancez ce script.
    pause
    exit /b 1
)

echo Keystore : %KS%
echo.
echo ------------------------------------------------------------
"%KEYTOOL%" -list -v -keystore "%KS%" -alias androiddebugkey -storepass android -keypass android | findstr /c:"SHA1" /c:"SHA-1"
echo ------------------------------------------------------------
echo.
echo A copier dans Google Cloud :
echo   Google Auth Platform ^> Clients ^> Creer un client ^> Android
echo   Nom du package : com.gregory.truelle
echo   Empreinte SHA-1 : la valeur ci-dessus (format AA:BB:CC:...)
echo.
echo Attention : cette empreinte vaut pour les APK compiles sur CE PC.
echo L'APK compile par GitHub a une autre cle : la connexion Drive n'y marchera pas.
echo.
pause
