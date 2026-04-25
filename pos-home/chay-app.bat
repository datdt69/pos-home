@echo off
rem Chay app tu thu muc target (pos-app.jar + jfx + lib sau mvn package)
setlocal EnableExtensions EnableDelayedExpansion
for %%I in ("%~dp0.") do set "ROOT=%%~fI"
set "APP_DIR=%ROOT%\target"
if not exist "%APP_DIR%\pos-app.jar" (
  echo [chay-app] Chua co pos-app.jar — dang build...
  pushd "%ROOT%"
  where mvn >nul 2>&1
  if not errorlevel 1 (
    call mvn -DskipTests package
    set "MV=!ERRORLEVEL!"
  ) else if exist "%ROOT%\mvnw.cmd" (
    call "%ROOT%\mvnw.cmd" -DskipTests package
    set "MV=!ERRORLEVEL!"
  ) else (
    echo [chay-app] Can Maven hoac mvnw.cmd. Chay setup.bat de cai day du.
    set "MV=1"
  )
  popd
  if not "!MV!"=="0" exit /b !MV!
)
if not exist "%APP_DIR%\pos-app.jar" (
  echo [chay-app] Loi: van khong co %APP_DIR%\pos-app.jar
  exit /b 1
)
cd /d "%APP_DIR%"
set "APP_DIR=%CD%"
call "%ROOT%\run.bat"
endlocal & exit /b %ERRORLEVEL%
