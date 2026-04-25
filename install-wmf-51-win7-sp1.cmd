@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"

rem =============================================================================
rem  Cai Windows Management Framework 5.1 (PowerShell 5.1) cho Windows 7 SP1
rem  va Windows Server 2008 R2 SP1. Can quyen Quan tri (Run as administrator).
rem  Can ket noi Internet de tai tu Microsoft. Link CDN lay tu Download Center
rem  (id=54616, id=42642) — co the doi khi Microsoft thay file.
rem  /Q  : cai WMF y lang (wusa /quiet), sau do can restart. Khong dung voi cai thu nghiem tren may live neu chua sao luu.
rem =============================================================================

set "WUSAQ="
if /I "%~1"=="/Q" set "WUSAQ=/quiet /norestart"
if /I "%~1"=="--quiet" set "WUSAQ=/quiet /norestart"

net session >nul 2>&1
if errorlevel 1 (
  echo.
  echo [LOI] Phai chay CMD/PowerShell voi quyen Administrator ^(Run as administrator^).
  echo.
  pause
  exit /b 1
)

set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS%" set "PS=powershell.exe"

rem Da co PowerShell 5.1 thi dung
"%PS%" -NoProfile -Command "if (([version]$PSVersionTable.PSVersion) -ge [version]'5.1'){ exit 0 } else { exit 1 }" >nul 2>&1
if not errorlevel 1 (
  echo PowerShell 5.1 da co san. Khong can cai WMF 5.1.
  echo Chay:  powershell  roi go  $PSVersionTable
  exit /b 0
)

ver | find " 6.1." >nul
if errorlevel 1 (
  echo.
  echo [CANH BAO] Script nay chu yeu cho Windows 7 SP1 / Server 2008 R2 ^(NT 6.1^).
  echo OS hien tai co the khac. Xem: https://www.microsoft.com/en-us/download/details.aspx?id=54616
  echo.
  set /p OK="Tiep tuc? (Y/N): "
  if /I not "!OK!"=="Y" exit /b 1
)

rem Kiem tra kien truc: chi ho tro x64 va x86
set "URL_WMF="
if /I "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
  set "URL_WMF=https://download.microsoft.com/download/6/f/5/6f5ff66c-6775-42b0-86c4-47d41f2da187/Win7AndW2K8R2-KB3191566-x64.zip"
  set "ZIP_NAME=Win7AndW2K8R2-KB3191566-x64.zip"
) else if /I "%PROCESSOR_ARCHITECTURE%"=="X86" (
  set "URL_WMF=https://download.microsoft.com/download/6/f/5/6f5ff66c-6775-42b0-86c4-47d41f2da187/Win7-KB3191566-x86.zip"
  set "ZIP_NAME=Win7-KB3191566-x86.zip"
) else (
  echo [LOI] Kien truc CPU khong phai x86/x64. Khong ho tro.
  exit /b 1
)

set "URL_NDP452=https://download.microsoft.com/download/e/2/1/e21644b5-2df2-47c2-91bd-63c560427900/NDP452-KB2901907-x86-x64-AllOS-ENU.exe"
set "NDP_NAME=NDP452-KB2901907-x86-x64-AllOS-ENU.exe"
set "WORK=%TEMP%\wmf51-%RANDOM%%RANDOM%"

rem .NET 4.5.2+ : Reg Release ^>= 378675  (Yeu cau cua WMF 5.1 tren Windows 7)
call :HasNet452
if errorlevel 1 (set "NEED_NDP=1") else (set "NEED_NDP=0")

if "!NEED_NDP!"=="1" (
  echo.
  echo Chua du .NET Framework 4.5.2+ ^(Reg Release ^< 378675^). Se tai va cai NDP 4.5.2 truoc...
  mkdir "%WORK%" 2>nul
  call :Down "%URL_NDP452%" "%WORK%\%NDP_NAME%"
  if errorlevel 1 (
    echo [LOI] Tai that bai. Kiem tra mang hoac TLS. Tu tai:  %URL_NDP452%
    goto :EndFail
  )
  echo Dang cai NDP 4.5.2 ^(vai phut^)...
  "%WORK%\%NDP_NAME%" /q /norestart
  if errorlevel 1 (
    echo [LOI] Cai NDP 4.5.2 that bai. Chay thu cai: %WORK%\%NDP_NAME%
    goto :EndFail
  )
  call :HasNet452
  if errorlevel 1 (
    echo.
    echo NDP 4.5.2 vua cai nhung he thong chua bao dang du .NET 4.5.2+ ^(Reg Release ^< 378675^) hoac can khoi dong.
    echo Khoi dong lai may, chay lai file cmd nay.
    goto :EndFail
  )
  echo.
  echo .NET 4.5.2+ OK. Tiep tuc cai WMF 5.1. Neu cai WMF bao loi, thu khoi dong truoc roi chay lai.
  echo.
) else (
  echo .NET Framework 4.5.2+ da nhan dang. Bo qua cai NDP 4.5.2.
  mkdir "%WORK%" 2>nul
)

echo.
echo Dang tai WMF 5.1... ^(!ZIP_NAME!^)
call :Down "!URL_WMF!" "%WORK%\!ZIP_NAME!"
if errorlevel 1 (
  echo [LOI] Tai zip WMF that bai. Kiem tra mang. URL: !URL_WMF!
  goto :EndFail
)

set "EXDIR=%WORK%\zipout"
mkdir "%EXDIR%" 2>nul
set "ZPATH=%WORK%\!ZIP_NAME!"
set "DOUT=%EXDIR%"
rem Giai nen bang Shell.Application (khong can PS 5)
"%PS%" -NoProfile -ExecutionPolicy Bypass -Command "$s=New-Object -ComObject Shell.Application; $z=$s.Namespace($env:ZPATH); if(-not $z){ exit 1 }; $d=$s.Namespace($env:DOUT); if(-not $d){ exit 1 }; $d.CopyHere($z.Items(), 20); exit 0"
if errorlevel 1 (
  echo [LOI] Giai nen zip that bai. Thu cai: https://www.microsoft.com/en-us/download/details.aspx?id=54616
  goto :EndFail
)

set "MSUFILE="
for /r "%EXDIR%" %%F in (*.msu) do if not defined MSUFILE set "MSUFILE=%%F"
if not defined MSUFILE (
  echo [LOI] Khong thay file .msu sau khi giai nen.
  goto :EndFail
)

echo.
echo Dang cai: !MSUFILE!
if defined WUSAQ (
  wusa "!MSUFILE!" %WUSAQ%
) else (
  start /wait wusa "!MSUFILE!"
)
set "ER=%errorlevel%"

if "!ER!"=="3010" (
  echo.
  echo Cai xong, bat buoc khoi dong lai. ^(ma 3010^)
  if defined WUSAQ shutdown /r /t 60 /c "WMF 5.1: khoi dong de hoan tat."
  if not defined WUSAQ echo Vui long khoi dong lai thu cong.
) else if "!ER!"=="0" (
  echo.
  echo Cai xong. Neu van chua co PS 5.1, thu khoi dong lai.
) else (
  echo.
  echo wusa thoat ma !ER! — xem log Event Viewer / https://support.microsoft.com
)

rmdir /s /q "%WORK%" 2>nul
exit /b 0

:HasNet452
rem Trang thai Release 378675 = .NET 4.5.2+ (Yeu cau cua goi WMF 5.1 tren Windows 7)
"%PS%" -NoProfile -Command "$a=@('HKLM:\SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full','HKLM:\SOFTWARE\Wow6432Node\Microsoft\NET Framework Setup\NDP\v4\Full'); foreach($k in $a){ try { $o=Get-ItemProperty $k -ErrorAction Stop; if($o.Release -ge 378675){ exit 0 } } catch { } } ; exit 1" >nul 2>&1
if errorlevel 1 exit /b 1
exit /b 0

:Down
set "DURL=%~1"
set "DOUTF=%~2"
echo  Tai: %DOUTF%
certutil -urlcache -split -f "%DURL%" "%DOUTF%" >nul 2>&1
if not errorlevel 1 if exist "%DOUTF%" exit /b 0
if exist "%DOUTF%" del /f /q "%DOUTF%" 2>nul
bitsadmin /transfer "WMF51DL%RANDOM%%RANDOM%" /download /priority high "%DURL%" "%DOUTF%" >nul 2>&1
if not errorlevel 1 if exist "%DOUTF%" exit /b 0
exit /b 1

:EndFail
rmdir /s /q "%WORK%" 2>nul
echo.
echo Doc them:  https://support.microsoft.com/en-us/topic/918077a1-ebc1-289f-bc04-8cc4546eafd0
pause
exit /b 1
