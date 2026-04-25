@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title Cai dat POS 1 lan (JDK 11, may 32 bit tu dong)
echo.
echo  === CA DAT POS 1 LAN ===
echo   Win7: can PowerShell 5.1 (WMF 5.1) - loi bao cau nay: dang dung PowerShell 2, can nang
echo   (Tai JDK, build, shortcut Desktop) - may 32bit: chay Admin neu can
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"
set "E=%ERRORLEVEL%"
if not "%E%"=="0" (
  echo.
  echo Loi! Ma thoat: %E%
  pause
  exit /b %E%
)
pause
endlocal & exit /b 0
