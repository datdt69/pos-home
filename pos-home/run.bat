@echo off
rem run.bat: tim pos-app.jar + jfx\ + lib\ o APP_DIR; neu khong co thi dung target\ sau "mvn package"
setlocal EnableExtensions EnableDelayedExpansion
if not defined APP_DIR set "NEED_DIR=1"
if defined APP_DIR if "!APP_DIR!"=="" set "NEED_DIR=1"
if defined NEED_DIR (
  set "NEED_DIR="
  for %%I in ("%~dp0.") do set "APP_DIR=%%~fI"
)
set "LAST=%APP_DIR:~-1%"
if "!LAST!"=="\" set "APP_DIR=%APP_DIR:~0,-1%"

set "ERRF=%TEMP%\pos_last_error.txt"
set "RL=%APP_DIR%\chay_pos.log"

set "JAR=%APP_DIR%\pos-app.jar"
set "JFXDIR=%APP_DIR%\jfx"
set "LIBDIR=%APP_DIR%\lib"
rem Sau "mvn package" JAR o target\ — chay run.bat ngay duoi thu muc du an van duoc
if not exist "%JAR%" (
  if exist "%APP_DIR%\target\pos-app.jar" (
    set "JAR=%APP_DIR%\target\pos-app.jar"
    set "JFXDIR=%APP_DIR%\target\jfx"
    set "LIBDIR=%APP_DIR%\target\lib"
    (echo [%date% %time%] run.bat: dung target\pos-app.jar va target\jfx)>>"%RL%"
  )
)

cd /d "%APP_DIR%" 2>nul
(echo [%date% %time%] run.bat APP_DIR=%APP_DIR%)>>"%RL%"
if not exist "%JAR%" (
  (echo [%date% %time%] ERR khong thay pos-app.jar. Chay: mvn -DskipTests package ^(JAR: %APP_DIR%\pos-app.jar hoac %APP_DIR%\target\pos-app.jar^))>>"%RL%"
  exit /b 1
)
set "JFXN=0"
for %%F in ("!JFXDIR!\javafx-*.jar") do set /a JFXN+=1
if !JFXN! lss 1 (
  (echo [%date% %time%] ERR no javafx-*.jar in !JFXDIR!\)>>"%RL%"
  exit /b 1
)

call :find_java11
if errorlevel 1 (
  (echo [%date% %time%] ERR need Java 11, set JAVA_HOME)>>"%RL%"
  exit /b 1
)
for %%E in ("!JAVA_CMD!") do set "JAB=%%~dpE"
if exist "!JAB!javaw.exe" set "JAVA_CMD=!JAB!javaw.exe"

set "JFXP="
for %%F in ("!JFXDIR!\javafx-*.jar") do (
  if defined JFXP (set "JFXP=!JFXP!;%%F") else (set "JFXP=%%F")
)
set "CP=%JAR%"
if exist "!LIBDIR!" (
  for %%F in ("!LIBDIR!\*.jar") do set "CP=!CP!;%%F"
)
set "JVM_JFX=--add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.glass.utils=ALL-UNNAMED --add-opens=javafx.graphics/javafx.css=ALL-UNNAMED --add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED"
if /i "%POS_THEME%"=="light" set "POS_EXTRA=-Dpos.theme=light"

del "%ERRF%" 2>nul
"%JAVA_CMD%" --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base !JVM_JFX! !POS_EXTRA! -Dfile.encoding=UTF-8 -Dprism.lcdtext=false -cp "!CP!" com.pos.Main 1>nul 2>"%ERRF%"
set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" call :log_fail
del "%ERRF%" 2>nul
endlocal & exit /b %EXITCODE%

:find_java11
set "JAVA_CMD="
if defined JAVA_HOME if exist "!JAVA_HOME!\bin\java.exe" (
  call :j11 "!JAVA_HOME!\bin\java.exe"
  if not errorlevel 1 (set "JAVA_CMD=!JAVA_HOME!\bin\java.exe" & exit /b 0)
)
set "_jh="
for /f "skip=1 tokens=1,2,*" %%A in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v JAVA_HOME 2^>nul') do set "_jh=%%C"
if defined _jh if exist "!_jh!\bin\java.exe" (
  call :j11 "!_jh!\bin\java.exe"
  if not errorlevel 1 (set "JAVA_CMD=!_jh!\bin\java.exe" & exit /b 0)
)
set "_jh="
for /f "skip=1 tokens=1,2,*" %%A in ('reg query "HKCU\Environment" /v JAVA_HOME 2^>nul') do set "_jh=%%C"
if defined _jh if exist "!_jh!\bin\java.exe" (
  call :j11 "!_jh!\bin\java.exe"
  if not errorlevel 1 (set "JAVA_CMD=!_jh!\bin\java.exe" & exit /b 0)
)
if exist "C:\Program Files\Java\jdk-11\bin\java.exe" (
  call :j11 "C:\Program Files\Java\jdk-11\bin\java.exe"
  if not errorlevel 1 (set "JAVA_CMD=C:\Program Files\Java\jdk-11\bin\java.exe" & exit /b 0)
)
for /d %%D in ("C:\Program Files\Java\jdk-11*") do (
  if exist "%%~D\bin\java.exe" (
    call :j11 "%%~D\bin\java.exe"
    if not errorlevel 1 (set "JAVA_CMD=%%~D\bin\java.exe" & exit /b 0)
  )
)
for /f "delims=" %%W in ('where java 2^>nul') do (
  call :j11 "%%W"
  if not errorlevel 1 (set "JAVA_CMD=%%W" & exit /b 0)
)
exit /b 1

:j11
set "_jx=%~1"
if not exist "%_jx%" exit /b 1
set "JVT=%TEMP%\pos_rjver.txt"
"%_jx%" -version 1>"%JVT%" 2>&1
rem JDK 11 in -version: co chuoi "11.0." hoac " 11.0"
findstr "11.0" "%JVT%" >nul
set "JE=!errorlevel!"
del /f /q "%JVT%" 2>nul
if "!JE!"=="0" (exit /b 0) else (exit /b 1)

:log_fail
if exist "%ERRF%" (
  (echo [%date% %time%] JAR exit !EXITCODE!:)>>"%RL%"
  type "%ERRF%" 1>>"%RL%" 2>nul
)
exit /b 0
