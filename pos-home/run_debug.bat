@echo off
rem Giu cua so CMD mo, in loi len man hinh (khong chi ghi chay_pos.log)
setlocal
set "POS_DEBUG=1"
cd /d "%~dp0"
call run.bat
echo.
echo --- run_debug: Enter de dong ---
pause
