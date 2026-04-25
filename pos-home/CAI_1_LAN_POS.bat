@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title Cai dat POS 1 lan (JDK 11, may 32 bit tu dong)
echo.
echo  === CA DAT POS 1 LAN ===
echo   (Tai JDK neu can, build, tao shortcut Desktop)
echo   May Windows 7 32-bit: dung nhu binh thuong - chay Admin neu Windows chan ghi nhanh
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
