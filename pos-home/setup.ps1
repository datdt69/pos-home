#Requires -Version 5.1
# Cai dat nhanh: JDK 21 (neu thieu), build bang mvnw, tao shortcut Desktop.
# Chay: chuot phai > Run with PowerShell, hoac go setup.bat
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
Set-Location $Root

function Refresh-PathFromRegistry {
  $m = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
  $u = [System.Environment]::GetEnvironmentVariable("Path", "User")
  $segs = @()
  if ($m) { $segs += $m -split ";" }
  if ($u) { $segs += $u -split ";" }
  $segs = $segs | Where-Object { $_.Trim() } | ForEach-Object { $_.Trim() }
  if ($segs.Count -gt 0) { $env:Path = $segs -join ";" }
}

function Get-JavaMajorFromExe {
  param([Parameter(Mandatory = $true)][string]$javaExe)
  if (-not (Test-Path -LiteralPath $javaExe)) { return 0 }
  try {
    $raw = & $javaExe -version 2>&1
    $s = if ($raw -is [array]) { $raw -join " " } else { [string]$raw }
    if ($s -match 'version "1\.(\d+)"') { return [int]$Matches[1] }
    if ($s -match 'version "(\d+)"') { return [int]$Matches[1] }
  } catch { }
  return 0
}

# JavaHome tu registry (Sau cai MSI) — khong dung thuoc tinh "Path" (de tranh trung ten)
function Get-JavaHomeFromRegistry {
  $candidates = [System.Collections.Generic.List[string]]::new()
  $addReg = {
    param($regBase)
    if (-not (Test-Path -LiteralPath $regBase)) { return }
    Get-ChildItem -LiteralPath $regBase -ErrorAction SilentlyContinue | ForEach-Object {
      $prop = Get-ItemProperty -LiteralPath $_.PSPath -ErrorAction SilentlyContinue
      foreach ($f in @("JavaHome", "InstallPath", "InstallationPath")) {
        $p = $prop.$f
        if ($p -and (Test-Path (Join-Path $p "bin\java.exe"))) { [void]$candidates.Add($p) }
      }
    }
  }
  try {
    $addReg.Invoke("HKLM:\SOFTWARE\JavaSoft\JDK")
    $addReg.Invoke("HKLM:\SOFTWARE\WOW6432Node\JavaSoft\JDK")
    $addReg.Invoke("HKLM:\SOFTWARE\JavaSoft\Java Development Kit")
    $addReg.Invoke("HKLM:\SOFTWARE\WOW6432Node\JavaSoft\Java Development Kit")
  } catch { }
  try {
    $ecl = "HKLM:\SOFTWARE\Eclipse Adoptium\JDK"
    if (Test-Path -LiteralPath $ecl) {
      Get-ChildItem -LiteralPath $ecl -ErrorAction SilentlyContinue | ForEach-Object {
        $pV = (Get-ItemProperty -LiteralPath $_.PSPath -ErrorAction SilentlyContinue).Path
        if ($pV -and (Test-Path -LiteralPath $pV) -and ((Test-Path (Join-Path $pV "bin\java.exe")))) { [void]$candidates.Add($pV) }
        $hot = Join-Path $_.PSPath "hotspot"
        if (Test-Path -LiteralPath $hot) {
          Get-ChildItem -LiteralPath $hot -ErrorAction SilentlyContinue | ForEach-Object {
            $p2 = (Get-ItemProperty -LiteralPath $_.PSPath -ErrorAction SilentlyContinue).Path
            if ($p2 -and (Test-Path -LiteralPath $p2) -and (Test-Path (Join-Path (Join-Path $p2 "bin") "java.exe"))) { [void]$candidates.Add($p2) }
          }
        }
      }
    }
  } catch { }
  $seen = [System.Collections.Generic.HashSet[string]]::new()
  $out = [System.Collections.Generic.List[string]]::new()
  foreach ($c in $candidates) {
    if ([string]::IsNullOrWhiteSpace($c)) { continue }
    $t = $c.Trim()
    if ($seen.Add($t)) { [void]$out.Add($t) }
  }
  return $out
}

# JAVA_HOME luu o Cap may / User (khong giong session hien tai)
function Get-JavaHomeFromSystemEnv {
  $jh = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
  if ($jh) { return $jh }
  return [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
}

# Tim tat ca bin\java.exe duoi 1 thu muc goc, kiem phienban (khong phu thuoc ten thu muc jdk-21*)
function Find-UseJava21UnderRoots {
  $roots = @(
    (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
    (Join-Path $env:ProgramFiles "Microsoft"),
    (Join-Path $env:ProgramFiles "Java")
  )
  $pf86 = [Environment]::GetFolderPath("ProgramFilesX86")
  if ($pf86) { $roots += (Join-Path $pf86 "Eclipse Adoptium") }
  $localPrograms = Join-Path $env:LOCALAPPDATA "Programs"
  if (Test-Path -LiteralPath $localPrograms) {
    $roots += (Join-Path $localPrograms "Eclipse Adoptium")
    $roots += (Join-Path $localPrograms "Microsoft")
  }
  foreach ($base in $roots) {
    if (-not (Test-Path -LiteralPath $base)) { continue }
    foreach ($d in (Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue)) {
      $je = Join-Path (Join-Path $d.FullName "bin") "java.exe"
      if (-not (Test-Path -LiteralPath $je)) { continue }
      $ma = Get-JavaMajorFromExe -javaExe $je
      if ($ma -ge 21) {
        $bin = Join-Path $d.FullName "bin"
        if ($env:Path -notlike "*$bin*") { $env:Path = $bin + ";" + $env:Path }
        $jdkRoot = $d.FullName
        $env:JAVA_HOME = $jdkRoot
        return $ma, $je, $jdkRoot
      }
    }
  }
  return 0, $null, $null
}

# Thu TUNG phan PATH: co khi java dau tien la ban 8, ban 21 nam thu muc sau
function Try-Java21InPath {
  foreach ($seg in ($env:Path -split ";")) {
    if ([string]::IsNullOrWhiteSpace($seg)) { continue }
    $d = $seg.Trim()
    if (-not (Test-Path -LiteralPath $d)) { continue }
    foreach ($je in @((Join-Path $d "java.exe"), (Join-Path $d "bin\java.exe"))) {
      if (-not (Test-Path -LiteralPath $je)) { continue }
      $ma = Get-JavaMajorFromExe -javaExe $je
      if ($ma -lt 21) { continue }
      if ($je -notmatch '\\bin\\java\.exe$') { continue }
      $binDir = Split-Path -Parent $je
      $jdkRoot = Split-Path -Parent $binDir
      $env:JAVA_HOME = $jdkRoot
      if ($env:Path -notlike "*$binDir*") { $env:Path = $binDir + ";" + $env:Path }
      return $ma, $je, $jdkRoot
    }
  }
  return 0, $null, $null
}

# Dung where.exe /R tren thu muc hep (Eclipse, Microsoft) — can tim bin\java.exe
function Find-Java21WhereR {
  $tops = [System.Collections.ArrayList]@(
    (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
    (Join-Path $env:ProgramFiles "Microsoft"),
    (Join-Path $env:ProgramFiles "OpenJDK"),
    (Join-Path $env:ProgramFiles "Java")
  )
  $lpE = Join-Path (Join-Path $env:LOCALAPPDATA "Programs") "Eclipse Adoptium"
  if (Test-Path -LiteralPath $lpE) { [void]$tops.Add($lpE) }
  $lpM = Join-Path (Join-Path $env:LOCALAPPDATA "Programs") "Microsoft"
  if (Test-Path -LiteralPath $lpM) { [void]$tops.Add($lpM) }
  if (-not (Get-Command where.exe -ErrorAction SilentlyContinue)) { return 0, $null, $null }
  foreach ($top in $tops) {
    if (-not (Test-Path -LiteralPath $top)) { continue }
    $lines = & where.exe /R $top java.exe 2>$null
    if (-not $lines) { continue }
    foreach ($line in $lines) {
      if ($line -notmatch '\\bin\\java\.exe$') { continue }
      $ma = Get-JavaMajorFromExe -javaExe $line
      if ($ma -ge 21) {
        $bin = Split-Path -Parent $line
        $jdkRoot = Split-Path -Parent $bin
        if ($env:Path -notlike "*$bin*") { $env:Path = $bin + ";" + $env:Path }
        $env:JAVA_HOME = $jdkRoot
        return $ma, $line, $jdkRoot
      }
    }
  }
  return 0, $null, $null
}

function Try-JdkRoot {
  param([string]$root)
  if ([string]::IsNullOrWhiteSpace($root)) { return 0, $null, $null }
  $r = $root.Trim()
  $je = Join-Path $r "bin\java.exe"
  if (-not (Test-Path -LiteralPath $je)) { return 0, $null, $null }
  $ma = Get-JavaMajorFromExe -javaExe $je
  if ($ma -lt 21) { return 0, $null, $null }
  $bin = Join-Path $r "bin"
  if ($env:Path -notlike "*$bin*") { $env:Path = $bin + ";" + $env:Path }
  $env:JAVA_HOME = $r
  return $ma, $je, $r
}

function Get-JavaScanHint {
  $h = if (Get-Command java -ErrorAction SilentlyContinue) {
    $p = (Get-Command java).Source
    $v = Get-JavaMajorFromExe -javaExe $p
    "  java dang thay: $p  (loai $v. Can 21+).`n"
  } else { "  Khong thay 'java' trong PATH sau khi doc lai registry.`n" }
  $jh = "  [May] JAVA_HOME: $([Environment]::GetEnvironmentVariable('JAVA_HOME','Machine'))`n  [User] JAVA_HOME: $([Environment]::GetEnvironmentVariable('JAVA_HOME','User'))`n"
  return $h + $jh
}

function Get-JavaMajor {
  if (Get-Command java -ErrorAction SilentlyContinue) {
    $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($javaExe) {
      $ma = Get-JavaMajorFromExe -javaExe $javaExe
      if ($ma -ge 1) { return $ma, $javaExe }
    }
  }
  return 0, $null
}

# PATH hien thoi + JAVA_HOME (may) + tung o PATH + registry + o dia + where /R
function Apply-Java21ToSession {
  Refresh-PathFromRegistry
  $ma, $jPath = Get-JavaMajor
  if ($ma -ge 21) { return $ma, $jPath, $env:JAVA_HOME }
  $sysJh = Get-JavaHomeFromSystemEnv
  if ($sysJh) {
    $a1, $b1, $c1 = Try-JdkRoot -root $sysJh
    if ($a1 -ge 21) { return $a1, $b1, $c1 }
  }
  $a2, $b2, $c2 = Try-Java21InPath
  if ($a2 -ge 21) { return $a2, $b2, $c2 }
  foreach ($jdkR in (Get-JavaHomeFromRegistry)) {
    if (-not $jdkR) { continue }
    $a3, $b3, $c3 = Try-JdkRoot -root $jdkR
    if ($a3 -ge 21) { return $a3, $b3, $c3 }
  }
  $m3, $j3, $h3 = Find-UseJava21UnderRoots
  if ($m3 -ge 21) { return $m3, $j3, $h3 }
  $m4, $j4, $h4 = Find-Java21WhereR
  if ($m4 -ge 21) { return $m4, $j4, $h4 }
  return 0, $null, $null
}

function Install-Temurin21 {
  if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    throw 'May tinh nay khong co winget. Hay cai JDK 21 Temurin hoac OpenJDK tu https://adoptium.net/ va chon them vao PATH, roi chay lai setup.'
  }
  Write-Host ""
  Write-Host 'Dang cai Eclipse Temurin JDK 21 (winget). Co the hoi phep Admin...' -ForegroundColor Cyan
  $argsW = @(
    "install", "-e", "--id", "EclipseAdoptium.Temurin.21.JDK",
    "--accept-package-agreements", "--accept-source-agreements"
  )
  $null = Start-Process -FilePath "winget" -ArgumentList $argsW -Wait -PassThru -NoNewWindow
  Write-Host 'Cho ghi nhanh sau khi cai MSI (5 giay)...' -ForegroundColor DarkGray
  Start-Sleep -Seconds 5
  for ($i = 0; $i -le 3; $i++) {
    Refresh-PathFromRegistry
    $ma, $jPath, $jHome = Apply-Java21ToSession
    if ($ma -ge 21) { return $ma, $jPath, $jHome }
    if ($i -lt 3) { Start-Sleep -Seconds 2 }
  }
  $aa, $bb, $cc = Apply-Java21ToSession
  return $aa, $bb, $cc
}

# --- chinh: kiem tra JDK >= 21 ---
$ver, $jPath, $jHome = Apply-Java21ToSession
if ($ver -ge 21) {
  Write-Host ("OK: Phat hien Java {0} ({1}) - dung, bo qua cai JDK." -f $ver, $jPath) -ForegroundColor Green
} else {
  if ($ver -gt 0) {
    Write-Host "Hien co Java $ver, ung dung can JDK 21 tro len. Dang cai JDK 21 them..." -ForegroundColor Yellow
  } else {
    Write-Host "Chua co Java 21+ trong PATH. Dang cai Eclipse Temurin 21..." -ForegroundColor Yellow
  }
  $ver2, $jPath2, $jHome2 = Install-Temurin21
  if ($ver2 -lt 21) {
    $hint = Get-JavaScanHint
    throw "Van khong chay duoc java phien ban 21+. Thu: DONG het cua so PowerShell, mo lai setup.bat. Hoac dat bien may/user JAVA_HOME tro dung thu muc JDK-21 (co thu muc bin\java.exe).`n$hint"
  }
  Write-Host ("OK: Java {0} san sang ({1})" -f $ver2, $jPath2) -ForegroundColor Green
}

# --- build: dung mvnw (khong can Maven cai he thong) ---
$Mvnw = Join-Path $Root "mvnw.cmd"
if (-not (Test-Path $Mvnw)) { throw "Khong tim thay mvnw.cmd. Thu muc giai nen co bi thieu file khong?" }

$prebuilt = Join-Path $Root "target\pos-app.jar"
if (Test-Path $prebuilt) {
  Write-Host ""
  Write-Host 'Da co target\pos-app.jar: bo qua mvn package. Xoa JAR do neu can build lai.' -ForegroundColor Yellow
} else {
  Write-Host ""
  Write-Host 'Dang chay: mvnw -DskipTests package (lan dau se tai Maven, can mang)...' -ForegroundColor Cyan
  if ($env:JAVA_HOME) {
    Write-Host "  JAVA_HOME = $env:JAVA_HOME" -ForegroundColor DarkGray
  }
  & $Mvnw "-DskipTests" "package"
  if ($LASTEXITCODE -ne 0) { throw ("mvn package that bai, ma thoat: {0}. Dong POS dang chay roi thu lai." -f $LASTEXITCODE) }
}

$jar1 = Join-Path $Root "target\pos-app.jar"
$jar2 = Join-Path $Root "pos-app.jar"
if (-not (Test-Path $jar1) -and -not (Test-Path $jar2)) {
  throw "Sau khi build chua thay pos-app.jar. Kiem tra log phia tren."
}

# --- shortcut: Desktop, target run.bat trong thu muc du an ---
$RunBat = Join-Path $Root "run.bat"
if (-not (Test-Path $RunBat)) { throw "Khong tim thay run.bat" }

$desk = [Environment]::GetFolderPath("Desktop")
if ([string]::IsNullOrEmpty($desk)) { $desk = $env:USERPROFILE }
$linkPath = Join-Path $desk "POS nha.lnk"
try {
  $W = New-Object -ComObject WScript.Shell
  $sc = $W.CreateShortcut($linkPath)
  $sc.TargetPath = $RunBat
  $sc.WorkingDirectory = $Root
  $sc.Description = "Ung dung ban hang POS nha. JavaFX."
  $sc.IconLocation = "$env:SystemRoot\System32\imageres.dll,76"
  $sc.Save()
} catch {
  throw "Tao shortcut that bai: $_"
}

Write-Host ""
Write-Host "=== XONG ===" -ForegroundColor Green
Write-Host "  Shortcut: $linkPath"
Write-Host '  Ghi chu: file tai ve tu may khac, co the bao cach ly - chuot phai, Properties, Unblock.' -ForegroundColor DarkGray
Write-Host '  Vao Desktop, bam dup shortcut POS nha. Co the hien cua so nhanh.' -ForegroundColor Gray
Write-Host ""
