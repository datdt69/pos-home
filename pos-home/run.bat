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

set "RL=%APP_DIR%\chay_pos.log"

set "JAR=%APP_DIR%\pos-app.jar"
set "JFXDIR=%APP_DIR%\jfx"
set "LIBDIR=%APP_DIR%\lib"
rem Sau mvn package JAR o target\ - chay run.bat duoi thu muc du an van duoc
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
  goto :show_err
)
set "JFXN=0"
for %%F in ("!JFXDIR!\javafx-*.jar") do set /a JFXN+=1
if !JFXN! lss 1 (
  (echo [%date% %time%] ERR no javafx-*.jar in !JFXDIR!\)>>"%RL%"
  goto :show_err
)

call :find_java11
if errorlevel 1 (
  (echo [%date% %time%] ERR need Java 11+. Cai JDK hoac dung thu muc Zulu: !LOCALAPPDATA!\pos-jdk\zulu11*  ; hoac set JAVA_HOME)>>"%RL%"
  goto :show_err
)
rem mac dinh dung java.exe (stderr/exit ong dinh hon javaw tren Win7). Im lang: dat POS_USE_JAVAW=1
if /i "%POS_USE_JAVAW%"=="1" (
  for %%E in ("!JAVA_CMD!") do set "JAB=%%~dpE"
  if exist "!JAB!javaw.exe" set "JAVA_CMD=!JAB!javaw.exe"
)

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

rem Ghi toan bo loi JVM vao chay_pos.log (1>nul truoc day lam POS khong biet li do)
(echo. & echo === JVM chay @ %date% %time% === & echo JAVA: !JAVA_CMD! & echo === & echo.)>>"%RL%"

if /i "%POS_DEBUG%"=="1" (
  (echo [POS_DEBUG=1: ra console, khong ghi vao chay_pos.log] )>>"%RL%"
  "%JAVA_CMD%" --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base !JVM_JFX! !POS_EXTRA! -Dfile.encoding=UTF-8 -Dprism.lcdtext=false -cp "!CP!" com.pos.Main
) else (
  "%JAVA_CMD%" --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base !JVM_JFX! !POS_EXTRA! -Dfile.encoding=UTF-8 -Dprism.lcdtext=false -cp "!CP!" com.pos.Main 1>>"%RL%" 2>&1
)
set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" call :log_fail
if not "%EXITCODE%"=="0" if exist "%RL%" start "" notepad "%RL%"
if not "%EXITCODE%"=="0" if /i not "%POS_NO_PAUSE%"=="1" (
  echo.
  echo Loi! Da mo chay_pos.log. Doc file hoac bao lai nguoi cai POS.
  echo.
  pause
)
if "%EXITCODE%"=="0" (echo. & echo [OK] ung dung thoat @ %date% %time% ma 0)>>"%RL%"

if /i "%POS_KEEP_OPEN%"=="1" (
  if "%EXITCODE%"=="0" (
    echo.
    echo [POS_KEEP_OPEN=1] Thoat thanh cong, Enter de dong cua so.
    echo.
    pause
  )
)

endlocal & exit /b %EXITCODE%

:find_java11
set "JAVA_CMD="
if defined JAVA_HOME if exist "!JAVA_HOME!\bin\java.exe" (
  call :j11 "!JAVA_HOME!\bin\java.exe"
  if not errorlevel 1 (set "JAVA_CMD=!JAVA_HOME!\bin\java.exe" & exit /b 0)
)
rem uu tien: JDK sau setup (Zulu 11 x86) - same user, may 32bit
set "_pjd=%LOCALAPPDATA%\pos-jdk"
if exist "%_pjd%\" for /d %%D in ("%_pjd%\zulu11*") do (
  if exist "%%~D\bin\java.exe" (
    call :j11 "%%~D\bin\java.exe"
    if not errorlevel 1 (set "JAVA_CMD=%%~D\bin\java.exe" & exit /b 0)
  )
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
for /d %%D in ("C:\Program Files\Eclipse Adoptium\*") do (
  if exist "%%~D\bin\java.exe" (
    call :j11 "%%~D\bin\java.exe"
    if not errorlevel 1 (set "JAVA_CMD=%%~D\bin\java.exe" & exit /b 0)
  )
)
if exist "C:\Program Files (x86)\Eclipse Adoptium\" for /d %%D in ("C:\Program Files (x86)\Eclipse Adoptium\*") do (
  if exist "%%~D\bin\java.exe" (
    call :j11 "%%~D\bin\java.exe"
    if not errorlevel 1 (set "JAVA_CMD=%%~D\bin\java.exe" & exit /b 0)
  )
)
for /d %%D in ("C:\Program Files (x86)\Java\jdk-11*") do (
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

rem Chap nhan JDK 11+ (11.0 ... 25.x trong nho -version)
:j11
set "_jx=%~1"
if not exist "%_jx%" exit /b 1
set "JVT=%TEMP%\pos_rjver.txt"
"%_jx%" -version 1>"%JVT%" 2>&1
for %%P in (11.0 12.0 13.0 14.0 15.0 16.0 17.0 18.0 19.0 20.0 21.0 22.0 23.0 24.0 25.0) do (
  findstr "%%P" "%JVT%" >nul 2>&1
  if not errorlevel 1 (
    del /f /q "%JVT%" 2>nul
    exit /b 0
  )
)
del /f /q "%JVT%" 2>nul
exit /b 1

:log_fail
(echo. & echo [%date% %time%] [FAIL] ma !EXITCODE! JAVA: !JAVA_CMD! & echo --- java -version: )>>"%RL%"
if defined JAVA_CMD (
  "%JAVA_CMD%" -version 1>>"%RL%" 2>&1
)
exit /b 0

:show_err
if exist "%RL%" start "" notepad "%RL%"
if /i not "%POS_NO_PAUSE%"=="1" (
  echo.
  echo Loi! Mo chay_pos.log: %RL%
  echo.
  pause
)
endlocal
exit /b 1
