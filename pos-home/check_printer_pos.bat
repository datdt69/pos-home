@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title POS - Kiem tra may in COM

set "LOG=%TEMP%\pos_printer_check.log"
set "TP=%TEMP%\pos_test_print.txt"

del /q "%LOG%" 2>nul
del /q "%TP%" 2>nul

(
  echo [BOOT] %date% %time%
  echo APP_DIR=%CD%
  echo.
)>>"%LOG%"

echo ===========================================
echo   KIEM TRA MAY IN POS (WIN7/32)
echo ===========================================
echo.

echo [1] Danh sach cong COM (mode)
echo [1] Danh sach cong COM (mode)>>"%LOG%"
mode
mode>>"%LOG%" 2>&1
echo.

echo [2] Ten thiet bi COM (wmic)
echo [2] Ten thiet bi COM (wmic)>>"%LOG%"
wmic path Win32_PnPEntity where "Name like '%%(COM%%'" get Name
wmic path Win32_PnPEntity where "Name like '%%(COM%%'" get Name>>"%LOG%" 2>&1
echo.

(
  echo ===============================
  echo TEST POS PRINT %date% %time%
  echo ===============================
  echo Neu in ra dong nay la COM dung.
)> "%TP%"

set "P="
set /p P=Nhap cong can test (vd COM3). Bo trong de thu COM1..COM12: 
if not defined P goto :test_all
call :test_one "%P%"
goto :done

:test_all
for %%C in (COM1 COM2 COM3 COM4 COM5 COM6 COM7 COM8 COM9 COM10 COM11 COM12) do (
  call :test_one "%%C"
)
goto :done

:test_one
set "PORT=%~1"
echo ---- Test !PORT! ----
echo ---- Test !PORT! ---->>"%LOG%"
mode !PORT!>>"%LOG%" 2>&1
copy /b "%TP%" !PORT! >nul 2>&1
if errorlevel 1 (
  echo FAIL !PORT!
  echo FAIL !PORT!>>"%LOG%"
) else (
  echo OK   !PORT!  ^<= neu may in nhay/ra giay thi dung cong nay
  echo OK   !PORT!>>"%LOG%"
)
echo.>>"%LOG%"
exit /b 0

:done
echo.
echo Xong. Log: %LOG%
echo.
if exist "%LOG%" start "" notepad "%LOG%"
echo Gui cho dev file log tren de chot COM + config.
echo.
pause
endlocal & exit /b 0
