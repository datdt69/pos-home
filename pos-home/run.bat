@echo off
rem POS Win7 32-bit only launcher.
setlocal EnableExtensions EnableDelayedExpansion

for %%I in ("%~dp0.") do set "APP_DIR=%%~fI"
if "!APP_DIR:~-1!"=="\" set "APP_DIR=!APP_DIR:~0,-1!"
if not defined TEMP set "TEMP=%TMP%"
if not defined TMP set "TMP=%TEMP%"

set "RL=%APP_DIR%\chay_pos.log"
set "RL7=%TEMP%\chay_pos_may7.log"
if exist "%RL%" del /q "%RL%" 2>nul
(echo [boot] %date% %time% APP=!APP_DIR! ^| run.bat win7-32)>>"%RL%" 2>&1
(echo [boot] %date% %time% APP=!APP_DIR! ^| run.bat win7-32)>>"%RL7%" 2>&1
(echo [%date% %time%] STEP init_paths)>>"%RL%"

set "JAR=%APP_DIR%\pos-app.jar"
set "JFXDIR=%APP_DIR%\jfx"
set "LIBDIR=%APP_DIR%\lib"
set "AUTOFIX_DONE=0"
set "AUTOSETUP_DONE=0"
call :resolve_paths

:check_bundle
if not exist "%JAR%" (
  set "BUNDLE_ERR=missing_jar"
  goto :need_autofix
)
if not exist "!JFXDIR!\javafx-*-win-x86*.jar" (
  set "BUNDLE_ERR=missing_x86_jfx"
  goto :need_autofix
)
if exist "!JFXDIR!\javafx-*-win.jar" (
  set "BUNDLE_ERR=has_win64_jfx"
  goto :need_autofix
)
goto :bundle_ok

:need_autofix
if "!AUTOFIX_DONE!"=="0" (
  set "AUTOFIX_DONE=1"
  call :autofix_win7_bundle
  if errorlevel 1 goto :show_err
  call :resolve_paths
  goto :check_bundle
)
if /i "!BUNDLE_ERR!"=="missing_jar" (
  (echo [%date% %time%] ERR khong thay pos-app.jar.)>>"%RL%"
) else if /i "!BUNDLE_ERR!"=="missing_x86_jfx" (
  (echo [%date% %time%] ERR thieu JavaFX x86 ^(*-win-x86*.jar^) trong !JFXDIR!.)>>"%RL%"
) else if /i "!BUNDLE_ERR!"=="has_win64_jfx" (
  (echo [%date% %time%] ERR phat hien JavaFX 64-bit ^(*-win.jar^) trong !JFXDIR!.)>>"%RL%"
)
(echo Danh sach jar trong !JFXDIR!:)>>"%RL%"
dir /b "!JFXDIR!\javafx-*.jar" >>"%RL%" 2>&1
goto :show_err

:bundle_ok

(echo [%date% %time%] STEP detect_java_x86)>>"%RL%"
call :find_java11_x86
if errorlevel 1 (
  if "!AUTOSETUP_DONE!"=="0" (
    set "AUTOSETUP_DONE=1"
    call :auto_setup_java
    call :find_java11_x86
  )
  if errorlevel 1 (
    (echo [%date% %time%] ERR khong tim thay Java 11+ x86.)>>"%RL%"
    (echo Can JDK/JRE 11 x86. Da thu auto setup nhung chua xong.)>>"%RL%"
    goto :show_err
  )
)

if /i "%POS_OPENJFX_CACHE_CLEAR%"=="1" (
  if exist "%USERPROFILE%\.openjfx\cache" (
    (echo [%date% %time%] Xoa cache OpenJFX (theo yeu cau): %USERPROFILE%\.openjfx\cache)>>"%RL%"
    rd /s /q "%USERPROFILE%\.openjfx\cache" 2>nul
  )
)

set "JFXP="
for %%F in ("!JFXDIR!\javafx-*.jar") do (
  echo %%~nxF | findstr /i /c:"-win-x86" >nul
  if not errorlevel 1 call :add_jfx "%%~fF"
)
if not defined JFXP (
  (echo [%date% %time%] ERR khong tao duoc module-path JavaFX x86.)>>"%RL%"
  goto :show_err
)

(echo [%date% %time%] STEP launch_javafx)>>"%RL%"
set "CP=%JAR%"
if exist "!LIBDIR!" for %%F in ("!LIBDIR!\*.jar") do set "CP=!CP!;%%F"
set "JVM_OPTS=-Xms64m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m"

(echo JAVA: !JAVA_CMD!)>>"%RL%"
(echo JFX : !JFXP!)>>"%RL%"
(echo JAR : %JAR%)>>"%RL%"
(echo JVM : !JVM_OPTS!)>>"%RL%"

"!JAVA_CMD!" !JVM_OPTS! --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base -Dfile.encoding=UTF-8 -Dprism.order=es2,sw,d3d -Dprism.forceGPU=false -cp "!CP!" com.pos.Main 1>>"%RL%" 2>&1
set "EXITCODE=!ERRORLEVEL!"

if not "%EXITCODE%"=="0" (
  (echo. & echo [%date% %time%] [FAIL] ma !EXITCODE!)>>"%RL%"
  if exist "%RL%" type "%RL%" >> "%RL7%" 2>&1
  if exist "%RL7%" start "" notepad "%RL7%"
  if exist "%RL%" start "" notepad "%RL%"
  echo.
  echo Loi khi chay app. Xem log:
  echo   %RL%
  echo   %RL7%
  echo.
  pause
  endlocal & exit /b %EXITCODE%
)

(echo [%date% %time%] [OK] ung dung thoat ma 0)>>"%RL%"
if exist "%RL%" type "%RL%" >> "%RL7%" 2>&1
endlocal & exit /b 0

:add_jfx
if not exist "%~1" exit /b 0
if defined JFXP (set "JFXP=!JFXP!;%~1") else (set "JFXP=%~1")
exit /b 0

:resolve_paths
set "JAR=%APP_DIR%\pos-app.jar"
set "JFXDIR=%APP_DIR%\jfx"
set "LIBDIR=%APP_DIR%\lib"
if not exist "%JAR%" (
  if exist "%APP_DIR%\target\pos-app.jar" (
    set "JAR=%APP_DIR%\target\pos-app.jar"
    set "JFXDIR=%APP_DIR%\target\jfx"
    set "LIBDIR=%APP_DIR%\target\lib"
  )
)
exit /b 0

:autofix_win7_bundle
(echo [%date% %time%] Auto-fix: don dep va build lai bundle Win7 32-bit...)>>"%RL%"
if exist "%APP_DIR%\target\jfx" rd /s /q "%APP_DIR%\target\jfx" 2>nul
if exist "%APP_DIR%\jfx" rd /s /q "%APP_DIR%\jfx" 2>nul
if exist "%USERPROFILE%\.openjfx\cache" rd /s /q "%USERPROFILE%\.openjfx\cache" 2>nul
if exist "%USERPROFILE%\.m2\repository\org\openjfx" rd /s /q "%USERPROFILE%\.m2\repository\org\openjfx" 2>nul
if exist "%APP_DIR%\mvnw.cmd" (
  call "%APP_DIR%\mvnw.cmd" -U clean -DskipTests package 1>>"%RL%" 2>&1
) else (
  call mvn -U clean -DskipTests package 1>>"%RL%" 2>&1
)
if errorlevel 1 (
  (echo [%date% %time%] ERR auto build Win7 32-bit that bai.)>>"%RL%"
  exit /b 1
)
exit /b 0

:auto_setup_java
(echo [%date% %time%] Auto-setup: chay setup.ps1 de cai Java x86...)>>"%RL%"
if exist "%APP_DIR%\setup.ps1" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%APP_DIR%\setup.ps1" 1>>"%RL%" 2>&1
) else (
  (echo [%date% %time%] ERR khong tim thay setup.ps1.)>>"%RL%"
  exit /b 1
)
exit /b 0

:find_java11_x86
set "JAVA_CMD="

if defined JAVA_HOME if exist "!JAVA_HOME!\bin\java.exe" (
  call :is_java11 "!JAVA_HOME!\bin\java.exe"
  if not errorlevel 1 (
    call :is_x86 "!JAVA_HOME!\bin\java.exe"
    if not errorlevel 1 (set "JAVA_CMD=!JAVA_HOME!\bin\java.exe" & exit /b 0)
  )
)

set "_PJD=%LOCALAPPDATA%\pos-jdk"
if exist "!_PJD!\" for /d %%D in ("!_PJD!\zulu11*") do (
  if exist "%%~D\bin\java.exe" (
    call :is_java11 "%%~D\bin\java.exe"
    if not errorlevel 1 (
      call :is_x86 "%%~D\bin\java.exe"
      if not errorlevel 1 (set "JAVA_CMD=%%~D\bin\java.exe" & exit /b 0)
    )
  )
)

for /d %%D in ("C:\Program Files (x86)\Eclipse Adoptium\*") do (
  if exist "%%~D\bin\java.exe" (
    call :is_java11 "%%~D\bin\java.exe"
    if not errorlevel 1 (
      call :is_x86 "%%~D\bin\java.exe"
      if not errorlevel 1 (set "JAVA_CMD=%%~D\bin\java.exe" & exit /b 0)
    )
  )
)

for /d %%D in ("C:\Program Files (x86)\Java\jdk-11*") do (
  if exist "%%~D\bin\java.exe" (
    call :is_java11 "%%~D\bin\java.exe"
    if not errorlevel 1 (
      call :is_x86 "%%~D\bin\java.exe"
      if not errorlevel 1 (set "JAVA_CMD=%%~D\bin\java.exe" & exit /b 0)
    )
  )
)

for /f "delims=" %%W in ('where java 2^>nul') do (
  call :is_java11 "%%W"
  if not errorlevel 1 (
    call :is_x86 "%%W"
    if not errorlevel 1 (set "JAVA_CMD=%%W" & exit /b 0)
  )
)
exit /b 1

:is_java11
set "_J=%~1"
if not exist "%_J%" exit /b 1
"%_J%" -version 1>"%TEMP%\pos_java_ver.txt" 2>&1
for %%V in (11.0 12.0 13.0 14.0 15.0 16.0 17.0 18.0 19.0 20.0 21.0 22.0 23.0 24.0 25.0) do (
  findstr "%%V" "%TEMP%\pos_java_ver.txt" >nul 2>&1
  if not errorlevel 1 (
    del /f /q "%TEMP%\pos_java_ver.txt" 2>nul
    exit /b 0
  )
)
del /f /q "%TEMP%\pos_java_ver.txt" 2>nul
exit /b 1

:is_x86
set "_J=%~1"
if not exist "%_J%" exit /b 1
"%_J%" -XshowSettings:properties -version 1>nul 2>"%TEMP%\pos_java_arch.txt"
findstr /c:"sun.arch.data.model = 32" "%TEMP%\pos_java_arch.txt" >nul 2>&1
set "_E=%ERRORLEVEL%"
del /f /q "%TEMP%\pos_java_arch.txt" 2>nul
exit /b %_E%

:show_err
if exist "%RL%" type "%RL%" >> "%RL7%" 2>&1
if exist "%RL7%" start "" notepad "%RL7%"
if exist "%RL%" start "" notepad "%RL%"
echo.
echo Loi khi khoi dong POS Win7 32-bit. Xem log:
echo   %RL%
echo   %RL7%
echo Neu van khong ro loi: mo CMD va chay "cmd /k run.bat"
echo.
pause
endlocal & exit /b 1
