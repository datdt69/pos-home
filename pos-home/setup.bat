@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo.
echo === CA DAT POS TU DONG (Win7 32-bit) ===
echo   Win7: can PowerShell 5.1 (cai: Windows Management Framework 5.1)
echo   Se tu dong don dep bundle cu, build moi va mo app.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" -ForceRebuild -AutoRun
set "E=%ERRORLEVEL%"
if not "%E%"=="0" (
  echo.
  echo Loi! Ma thoat: %E%
  pause
  exit /b %E%
)
endlocal & exit /b 0
