@echo off
setlocal EnableExtensions
title POS Win7 Launcher
cd /d "%~dp0"

if not exist "run.bat" (
  echo [Loi] Khong tim thay run.bat trong thu muc hien tai.
  pause
  exit /b 1
)

rem Giu cua so CMD + in log ra man hinh de de bat loi tren Win7 POS.
set "POS_DEBUG=1"
set "POS_KEEP_OPEN=1"
call run.bat
set "_EX=%errorlevel%"

echo.
echo [Win7/POS] Ma thoat: %_EX%
echo [Win7/POS] Log app: %CD%\chay_pos.log
echo [Win7/POS] Log du phong: %TMP%\chay_pos_may7.log
if exist "%TMP%\chay_pos_may7.log" (
  start "" notepad "%TMP%\chay_pos_may7.log"
)

if not "%_EX%"=="0" (
  echo.
  echo Goi y: neu chua cai moi truong, chay setup.bat roi thu lai.
)

echo.
echo Enter de dong cua so...
pause >nul
exit /b %_EX%
