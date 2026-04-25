@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo.
echo === CA DAT POS (can mang lan dau) ===
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
