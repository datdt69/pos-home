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
rem Win7 / USB / quyen: chay_pos.log o thu muc du an that bai. Luon ghi them ban %TMP% de van co log.
set "RL7=%TMP%\chay_pos_may7.log"
if exist "%RL%" del /q "%RL%" 2>nul
(echo [boot] %date% %time% APP=!APP_DIR! ^| run.bat moi) >> "%RL%" 2>&1
(echo [boot] %date% %time% APP=!APP_DIR! ^| RL may chu pos + RL7=Temp) >> "%RL7%" 2>&1

set "JAR=%APP_DIR%\pos-app.jar"
set "JFXDIR=%APP_DIR%\jfx"
set "LIBDIR=%APP_DIR%\lib"
set "USING_TARGET="
rem Sau mvn package: JAR nam target\ - run.bat van chay duoc tu thu muc goc
if not exist "%JAR%" (
  if exist "%APP_DIR%\target\pos-app.jar" (
    set "JAR=%APP_DIR%\target\pos-app.jar"
    set "JFXDIR=%APP_DIR%\target\jfx"
    set "LIBDIR=%APP_DIR%\target\lib"
    set "USING_TARGET=1"
  )
)

cd /d "%APP_DIR%" 2>nul
if defined USING_TARGET (
  (echo [%date% %time%] run.bat APP_DIR=!APP_DIR! ^| JAR+jfx+lib: target\ ^(sau mvn package^))>>"%RL%"
) else (
  (echo [%date% %time%] run.bat APP_DIR=!APP_DIR! ^| JAR+jfx+lib: thu muc goc)>>"%RL%"
)
if not exist "%JAR%" (
  (echo [%date% %time%] ERR khong thay pos-app.jar. Chay: mvn -DskipTests package ^(JAR: !APP_DIR!\pos-app.jar hoac !APP_DIR!\target\pos-app.jar^))>>"%RL%"
  goto :show_err
)
set "JFXN=0"
for %%F in ("!JFXDIR!\javafx-*.jar") do set /a JFXN+=1
if !JFXN! lss 1 (
  (echo [%date% %time%] ERR no javafx-*.jar in !JFXDIR!\)>>"%RL%"
  goto :show_err
)
rem Java 11: chi chay jfx 17.0.6. target\jfx con ban 20+ = loi 61.0: xoa jfx, mvnw clean -Ppos32 package
if exist "!JFXDIR!\javafx-base-2*.jar" (
  (echo [%date% %time%] ERR jfx 20+ ^(ten jar javafx-base-21^) - Java 11 khong doc duoc. Xoa target\jfx, build: mvnw clean -Ppos32 -DskipTests package)>>"%RL%"
  goto :show_err
)
if exist "!JFXDIR!\javafx-base-19*.jar" (
  (echo [%date% %time%] ERR jfx 19 can Java 15+. Dung 17.0.6, build: mvnw clean -Ppos32 -DskipTests package)>>"%RL%"
  goto :show_err
)
if exist "!JFXDIR!\javafx-base-18*.jar" (
  (echo [%date% %time%] ERR jfx 18+ can Java 16+. Dung 17.0.6, build: mvnw clean -Ppos32 -DskipTests package)>>"%RL%"
  goto :show_err
)

call :find_java11
if errorlevel 1 (
  (echo [%date% %time%] ERR: khong chay duoc "java" hoac version khong 11-25. JDK 11/17/21/... deu duoc. Cai JDK hoac set JAVA_HOME=thu muc co bin\java.exe ^(vi du Zulu: !LOCALAPPDATA!\pos-jdk\zulu11*^))>>"%RL%"
  goto :show_err
)
rem May 32-bit: JAR javafx phai *-win-x86*. (ban *-win.jar* la 64 bit). Cache .openjfx cu 64 -> UnsatisfiedLinkError AMD64 on IA32
set "J32="
"%JAVA_CMD%" -XshowSettings:properties 2>nul | findstr /c:"sun.arch.data.model = 32" >nul && set "J32=1"
rem findstr: khong thay "32" tren JVM 64 -> errorlevel=1, lam EXITCODE=1 sau khi java OK. Reset:
ver >nul
if defined J32 (
  if not exist "!JFXDIR!\javafx-*-win-x86*.jar" (
    (echo [%date% %time%] ERR Java 32-bit ma jfx khong *-win-x86*. Cai: mvnw clean -Ppos32 -DskipTests package, lay target\jfx)>>"%RL%"
    (echo. Neu tren may 64-bit: dung JDK 64, build KHONG -Ppos32. )>>"%RL%"
    goto :show_err
  )
  if exist "!JFXDIR!\javafx-*-win.jar" (
    (echo [%date% %time%] ERR Java 32-bit nhung thay jfx 64-bit ^(*-win.jar^). Xoa jfx cu va build lai profile pos32.)>>"%RL%"
    (echo Danh sach jar trong !JFXDIR!:)>>"%RL%"
    dir /b "!JFXDIR!\javafx-*.jar" >>"%RL%" 2>&1
    goto :show_err
  )
  if not defined POS_OPENJFX_CACHE_CLEAR set "POS_OPENJFX_CACHE_CLEAR=1"
  (echo. May 32: dang ep JavaFX x86 + xoa cache OpenJFX de tranh nap nham DLL 64-bit) >> "%RL%"
  if /i "%POS_OPENJFX_CACHE_CLEAR%"=="1" if exist "%USERPROFILE%\.openjfx\cache" (
    (echo [%date% %time%] POS_OPENJFX_CACHE_CLEAR=1: xoa .openjfx\cache) >> "%RL%"
    rd /s /q "%USERPROFILE%\.openjfx\cache" 2>nul
  )
)
rem Chay: luon dung java.exe (doi toi khi app tat + ghi log dung). javaw: khong block -> cmd tat ngay.
rem Chi POS_DEBUG=1 + POS_USE_JAVAW=1: dung javaw + start /wait
set "JAVW=0"
for %%E in ("!JAVA_CMD!") do set "JAB=%%~dpE"
if /i not "%POS_DEBUG%"=="1" (
  if exist "!JAB!java.exe" set "JAVA_CMD=!JAB!java.exe"
) else if /i "%POS_USE_JAVAW%"=="1" if exist "!JAB!javaw.exe" (
  set "JAVA_CMD=!JAB!javaw.exe"
  set "JAVW=1"
)

set "JFXP="
if defined J32 (
  for %%F in ("!JFXDIR!\javafx-*.jar") do (
    echo %%~nxF | findstr /i /c:"-win-x86" >nul
    if not errorlevel 1 call :add_jfx_path "%%~fF"
  )
) else (
  for %%F in ("!JFXDIR!\javafx-*.jar") do (
    echo %%~nxF | findstr /i /c:"-win-x86" >nul
    if errorlevel 1 call :add_jfx_path "%%~fF"
  )
)
if not defined JFXP (
  for %%F in ("!JFXDIR!\javafx-*.jar") do (
    call :add_jfx_path "%%~fF"
  )
)
(echo [JFX module-path] !JFXP!)>>"%RL%"
set "CP=%JAR%"
if exist "!LIBDIR!" (
  for %%F in ("!LIBDIR!\*.jar") do set "CP=!CP!;%%F"
)
set "JVM_JFX=--add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.glass.utils=ALL-UNNAMED --add-opens=javafx.graphics/javafx.css=ALL-UNNAMED --add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED"
if /i "%POS_THEME%"=="light" set "POS_EXTRA=-Dpos.theme=light"
rem Pipeline JavaFX: mac dinh es2,sw,d3d ^(J32^). Ghi de: set POS_PRISM_ORDER=es2,sw. Verbose: POS_PRISM_VERBOSE=1. Tat: POS_PRISM_SW=0
if /i "%POS_PRISM_SW%"=="0" (
  set "DOPRISM=0"
) else if /i "%POS_PRISM_SW%"=="1" (
  set "DOPRISM=1"
) else if defined J32 (
  set "DOPRISM=1"
) else (
  set "DOPRISM=0"
)
if "%DOPRISM%"=="1" (
  if not defined POS_PRISM_ORDER (set "POS_PRISM_ORDER=es2,sw,d3d")
  if defined POS_EXTRA (
    set "POS_EXTRA=!POS_EXTRA! -Dprism.order=!POS_PRISM_ORDER! -Dprism.forceGPU=false -Dglass.win.uiScale=1.0"
  ) else (
    set "POS_EXTRA=-Dprism.order=!POS_PRISM_ORDER! -Dprism.forceGPU=false -Dglass.win.uiScale=1.0"
  )
  if /i "%POS_PRISM_VERBOSE%"=="1" (set "POS_EXTRA=!POS_EXTRA! -Dprism.verbose=true")
  (echo. [run.bat: -Dprism.order=!POS_PRISM_ORDER! - neu van loi: cai vc_redist x86, driver; thu POS_PRISM_ORDER=sw hoac=es2,sw])>>"%RL%"
)

rem Ghi toan bo loi JVM vao chay_pos.log (1>nul truoc day lam POS khong biet li do)
ver >nul
(echo. & echo === JVM chay @ %date% %time% === & echo JAVA: !JAVA_CMD! & echo === & echo [run.bat: den day = da tim duoc java; doan duoi: output JVM ^(loi, SLF4J...^) hoac rong] & echo [run.bat: cua so cmd tat nhanh - dung run_win7_pos.bat hoac: set POS_DEBUG=1] & echo.)>>"%RL%"

rem Phai long if (POS_DEBUG) roi else (ghi log). CMD: if A if B (x) else (y) - neu A false thi Y khong chay, java co the khong bao gio chay, ma 0 gia!
if /i "%POS_DEBUG%"=="1" (
  if "!JAVW!"=="1" (
    (echo [POS_DEBUG+JAVAW: start /wait javaw] )>>"%RL%"
    start "POS" /wait "!JAVA_CMD!" --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base !JVM_JFX! !POS_EXTRA! -Dfile.encoding=UTF-8 -Dprism.lcdtext=false -cp "!CP!" com.pos.Main
  ) else (
    (echo [POS_DEBUG=1: in ra console VA ghi vao chay_pos.log] )>>"%RL%"
    "%JAVA_CMD%" --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base !JVM_JFX! !POS_EXTRA! -Dfile.encoding=UTF-8 -Dprism.lcdtext=false -cp "!CP!" com.pos.Main 1>>"%RL%" 2>&1
    type "%RL%"
  )
) else (
  (echo [run.bat: dang goi com.pos.Main, ghi vao chay_pos.log] )>>"%RL%"
  "%JAVA_CMD%" --module-path "!JFXP!" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base !JVM_JFX! !POS_EXTRA! -Dfile.encoding=UTF-8 -Dprism.lcdtext=false -cp "!CP!" com.pos.Main 1>>"%RL%" 2>&1
)
set "EXITCODE=!ERRORLEVEL!"
if not "%EXITCODE%"=="0" call :log_fail
if not "%EXITCODE%"=="0" if exist "%RL%" start "" notepad "%RL%"
if not "%EXITCODE%"=="0" if /i not "%POS_NO_PAUSE%"=="1" (
  echo.
  echo Loi! Da mo chay_pos.log. Doc file hoac bao lai nguoi cai POS.
  echo.
  pause
)
if "%EXITCODE%"=="0" (
  (echo. & echo [OK] ung dung thoat @ %date% %time% ma 0)>>"%RL%"
  (echo. [Ghi chu: ma 0 = JVM da thoat. Neu khong thay man hinh app: chay run_win7_pos.bat hoac bam dup run.bat voi set POS_DEBUG=1])>>"%RL%"
)

if /i "%POS_KEEP_OPEN%"=="1" (
  if "%EXITCODE%"=="0" (
    echo.
    echo [POS_KEEP_OPEN=1] Thoat thanh cong, Enter de dong cua so.
    echo.
    pause
  )
)

rem Ban may 7/ POS: gop vao file Temp (khi o thu muc du an ghi that bai)
if defined RL7 (
  (echo. & echo [--- gop chay_pos.log vao file Temp ben duoi ---] & echo.)>>"%RL7%" 2>&1
)
if exist "%RL%" (
  type "%RL%" >> "%RL7%" 2>&1
) else (
  if defined RL7 (echo [chay_pos: khong ghi duoc o thu muc du an, chi co boot o Temp])>>"%RL7%" 2>&1
)
if not "%EXITCODE%"=="0" if defined RL7 if exist "%RL7%" if /i not "%POS_NO_NOTEPAD7%"=="1" start "" notepad "%RL7%"

endlocal & exit /b %EXITCODE%

:add_jfx_path
if not exist "%~1" exit /b 0
if defined JFXP (set "JFXP=!JFXP!;%~1") else (set "JFXP=%~1")
exit /b 0

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
rem Phan "phien ban JDK" o duoi CHI la thong tin them, KHONG phai dong loi. Loi that = doan ngan/ Exception o tren trong file nay.
(echo. & echo [%date% %time%] [FAIL] ma !EXITCODE! JAVA: !JAVA_CMD! & echo --- Thong tin JDK ^(doi chieu, khong phai loi^): )>>"%RL%"
if defined JAVA_CMD (
  "%JAVA_CMD%" -version 1>>"%RL%" 2>&1
)
exit /b 0

:show_err
if defined RL7 (
  if exist "%RL%" (
    type "%RL%" >> "%RL7%" 2>&1
  ) else (
    echo [show_err: khong ghi duoc chay_pos.log o du an]>>"%RL7%" 2>&1
  )
)
if defined RL7 if exist "%RL7%" if /i not "%POS_NO_NOTEPAD7%"=="1" start "" notepad "%RL7%"
if exist "%RL%" if /i not "%POS_NO_NOTEPAD7%"=="1" start "" notepad "%RL%"
if /i not "%POS_NO_PAUSE%"=="1" (
  echo.
  echo Loi! Log du an: %RL%
  echo Log Temp may Win7/ POS: %RL7%
  echo.
  pause
)
endlocal
exit /b 1
