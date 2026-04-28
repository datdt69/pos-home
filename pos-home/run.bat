@echo off
setlocal EnableExtensions EnableDelayedExpansion

for %%I in ("%~dp0.") do set "APP_DIR=%%~fI"
if "!APP_DIR:~-1!"=="\" set "APP_DIR=!APP_DIR:~0,-1!"
if not defined TEMP set "TEMP=%TMP%"
if not defined TMP set "TMP=%TEMP%"

set "RL=%APP_DIR%\chay_pos.log"
set "RL7=%TEMP%\chay_pos_may7.log"
if exist "%RL%" del /q "%RL%" 2>nul
echo [boot] %date% %time% APP=!APP_DIR! ^| run.bat win7-32>>"%RL%" 2>&1
echo [boot] %date% %time% APP=!APP_DIR! ^| run.bat win7-32>>"%RL7%" 2>&1
echo [%date% %time%] STEP init_paths>>"%RL%"

set "JAR=%APP_DIR%\target\pos-app.jar"
set "JFXDIR=%APP_DIR%\target\jfx"
set "LIBDIR=%APP_DIR%\target\lib"
if not exist "%JAR%" (
  set "JAR=%APP_DIR%\pos-app.jar"
  set "JFXDIR=%APP_DIR%\jfx"
  set "LIBDIR=%APP_DIR%\lib"
)

if not exist "%JAR%" (
  echo [%date% %time%] ERR missing_jar %JAR%>>"%RL%"
  goto :show_err
)
if not exist "%JFXDIR%\javafx-base-*-win-x86.jar" (
  echo [%date% %time%] ERR missing_jfx_x86 %JFXDIR%>>"%RL%"
  goto :show_err
)

echo [%date% %time%] STEP detect_java_x86>>"%RL%"
set "JAVA_CMD="
for /d %%D in ("%LOCALAPPDATA%\pos-jdk\zulu11*") do (
  if exist "%%~D\bin\java.exe" (
    set "JAVA_CMD=%%~D\bin\java.exe"
    goto :java_ok
  )
)
for /d %%D in ("C:\Program Files (x86)\Eclipse Adoptium\*") do (
  if exist "%%~D\bin\java.exe" (
    set "JAVA_CMD=%%~D\bin\java.exe"
    goto :java_ok
  )
)
for /d %%D in ("C:\Program Files (x86)\Java\jdk-11*") do (
  if exist "%%~D\bin\java.exe" (
    set "JAVA_CMD=%%~D\bin\java.exe"
    goto :java_ok
  )
)
echo [%date% %time%] ERR java_x86_not_found>>"%RL%"
goto :show_err

:java_ok
echo [%date% %time%] STEP java_found !JAVA_CMD!>>"%RL%"

set "JFXP=%JFXDIR%\javafx-base-17.0.6-win-x86.jar;%JFXDIR%\javafx-controls-17.0.6-win-x86.jar;%JFXDIR%\javafx-fxml-17.0.6-win-x86.jar;%JFXDIR%\javafx-graphics-17.0.6-win-x86.jar"
set "CP=%JAR%"
if exist "%LIBDIR%" for %%F in ("%LIBDIR%\*.jar") do set "CP=!CP!;%%~fF"
set "JVM_OPTS=-Xms64m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m"

echo [%date% %time%] STEP launch_javafx>>"%RL%"
echo JAVA: !JAVA_CMD!>>"%RL%"
echo JFX : !JFXP!>>"%RL%"
echo JAR : %JAR%>>"%RL%"
echo JVM : !JVM_OPTS!>>"%RL%"

"!JAVA_CMD!" !JVM_OPTS! --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base -Dfile.encoding=UTF-8 -Dprism.order=es2,sw,d3d -Dprism.forceGPU=false -cp "!CP!" com.pos.Main 1>>"%RL%" 2>&1
set "EXITCODE=!ERRORLEVEL!"
if not "%EXITCODE%"=="0" (
  echo [%date% %time%] [FAIL] ma !EXITCODE!>>"%RL%"
  goto :show_err
)
echo [%date% %time%] [OK] ung dung thoat ma 0>>"%RL%"
if exist "%RL%" type "%RL%" >> "%RL7%" 2>&1
endlocal & exit /b 0

:show_err
if exist "%RL%" type "%RL%" >> "%RL7%" 2>&1
if exist "%RL7%" start "" notepad "%RL7%"
if exist "%RL%" start "" notepad "%RL%"
echo.
echo Loi khi khoi dong POS Win7 32-bit. Xem log:
echo   %RL%
echo   %RL7%
echo.
pause
endlocal & exit /b 1
