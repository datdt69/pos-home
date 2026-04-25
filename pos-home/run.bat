@echo off
rem POS Win7 32-bit only launcher.
setlocal EnableExtensions EnableDelayedExpansion

for %%I in ("%~dp0.") do set "APP_DIR=%%~fI"
if "!APP_DIR:~-1!"=="\" set "APP_DIR=!APP_DIR:~0,-1!"

set "RL=%APP_DIR%\chay_pos.log"
set "RL7=%TMP%\chay_pos_may7.log"
if exist "%RL%" del /q "%RL%" 2>nul
(echo [boot] %date% %time% APP=!APP_DIR! ^| run.bat win7-32)>>"%RL%" 2>&1
(echo [boot] %date% %time% APP=!APP_DIR! ^| run.bat win7-32)>>"%RL7%" 2>&1

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

if not exist "%JAR%" (
  (echo [%date% %time%] ERR khong thay pos-app.jar.)>>"%RL%"
  (echo Build lai: mvnw clean -Ppos32 -DskipTests package)>>"%RL%"
  goto :show_err
)

if not exist "!JFXDIR!\javafx-*-win-x86*.jar" (
  (echo [%date% %time%] ERR thieu JavaFX x86 ^(*-win-x86*.jar^) trong !JFXDIR!.)>>"%RL%"
  (echo Build lai dung profile 32-bit: mvnw clean -Ppos32 -DskipTests package)>>"%RL%"
  goto :show_err
)

if exist "!JFXDIR!\javafx-*-win.jar" (
  (echo [%date% %time%] ERR phat hien JavaFX 64-bit ^(*-win.jar^) trong !JFXDIR!.)>>"%RL%"
  (echo Xoa jfx cu, build lai: mvnw clean -Ppos32 -DskipTests package)>>"%RL%"
  (echo Danh sach jar:)>>"%RL%"
  dir /b "!JFXDIR!\javafx-*.jar" >>"%RL%" 2>&1
  goto :show_err
)

call :find_java11_x86
if errorlevel 1 (
  (echo [%date% %time%] ERR khong tim thay Java 11+ x86.)>>"%RL%"
  (echo Can JDK/JRE 11 x86. Goi setup.bat de cai.)>>"%RL%"
  goto :show_err
)

if exist "%USERPROFILE%\.openjfx\cache" (
  (echo [%date% %time%] Xoa cache OpenJFX: %USERPROFILE%\.openjfx\cache)>>"%RL%"
  rd /s /q "%USERPROFILE%\.openjfx\cache" 2>nul
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

set "CP=%JAR%"
if exist "!LIBDIR!" for %%F in ("!LIBDIR!\*.jar") do set "CP=!CP!;%%F"

(echo JAVA: !JAVA_CMD!)>>"%RL%"
(echo JFX : !JFXP!)>>"%RL%"
(echo JAR : %JAR%)>>"%RL%"

"!JAVA_CMD!" --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base -Dfile.encoding=UTF-8 -Dprism.order=es2,sw,d3d -Dprism.forceGPU=false -cp "!CP!" com.pos.Main 1>>"%RL%" 2>&1
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
echo.
pause
endlocal & exit /b 1
