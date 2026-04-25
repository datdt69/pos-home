@echo off
rem May Win7/ POS: giu cua so CMD, POS_DEBUG=1, sau khi chay xong mo chay_pos_may7.log o Temp (neu co).
set "POS_DEBUG=1"
cd /d "%~dp0"
call run.bat
set "_EX=%errorlevel%"
echo.
echo [Win7/ POS] Log du phong: %TMP%\chay_pos_may7.log
echo chay_pos.log: cung thu muc run.bat
if exist "%TMP%\chay_pos_may7.log" start "" notepad "%TMP%\chay_pos_may7.log"
echo.
echo Ma thoat: %_EX%  (Enter de dong) ---
pause
exit /b %_EX%
