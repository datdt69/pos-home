#Requires -Version 5.1
# Cai dat 1 lan: JDK 11 (Zulu 32-bit neu may 32bit; Temurin 11 neu winget), build mvnw -Ppos32 khi can, shortcut Desktop.
# Chay: chuot phai > Run with PowerShell, hoac go setup.bat / CAI_1_LAN_POS.bat
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }

$Zulu11WinX86Url = "https://cdn.azul.com/zulu/bin/zulu11.76.21-ca-jdk11.0.25-win_i686.zip"

$Root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
Set-Location $Root

$MinJdk = 11

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

function Get-JavaHomeFromSystemEnv {
  $jh = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
  if ($jh) { return $jh }
  return [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
}

function Find-UseJava11UnderRoots {
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
  $roots += (Join-Path $env:LOCALAPPDATA "pos-jdk")
  foreach ($base in $roots) {
    if (-not (Test-Path -LiteralPath $base)) { continue }
    foreach ($d in (Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue)) {
      $je = Join-Path (Join-Path $d.FullName "bin") "java.exe"
      if (-not (Test-Path -LiteralPath $je)) { continue }
      $ma = Get-JavaMajorFromExe -javaExe $je
      if ($ma -ge $MinJdk) {
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

function Try-Java11InPath {
  foreach ($seg in ($env:Path -split ";")) {
    if ([string]::IsNullOrWhiteSpace($seg)) { continue }
    $d = $seg.Trim()
    if (-not (Test-Path -LiteralPath $d)) { continue }
    foreach ($je in @((Join-Path $d "java.exe"), (Join-Path $d "bin\java.exe"))) {
      if (-not (Test-Path -LiteralPath $je)) { continue }
      $ma = Get-JavaMajorFromExe -javaExe $je
      if ($ma -lt $MinJdk) { continue }
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

function Find-Java11WhereR {
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
  $pj = Join-Path $env:LOCALAPPDATA "pos-jdk"
  if (Test-Path -LiteralPath $pj) { [void]$tops.Add($pj) }
  if (-not (Get-Command where.exe -ErrorAction SilentlyContinue)) { return 0, $null, $null }
  foreach ($top in $tops) {
    if (-not (Test-Path -LiteralPath $top)) { continue }
    $lines = & where.exe /R $top java.exe 2>$null
    if (-not $lines) { continue }
    foreach ($line in $lines) {
      if ($line -notmatch '\\bin\\java\.exe$') { continue }
      $ma = Get-JavaMajorFromExe -javaExe $line
      if ($ma -ge $MinJdk) {
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
  if ($ma -lt $MinJdk) { return 0, $null, $null }
  $bin = Join-Path $r "bin"
  if ($env:Path -notlike "*$bin*") { $env:Path = $bin + ";" + $env:Path }
  $env:JAVA_HOME = $r
  return $ma, $je, $r
}

function Test-Is32BitWindows {
  return -not [System.Environment]::Is64BitOperatingSystem
}

function Get-NoWingetInstallHint {
  return @"
May khong co winget (Windows 7/8.1 thuong vay; winget can Windows 10+).

THU CONG (Windows 64-bit):
  1) https://adoptium.net/temurin/releases/?version=11
  2) Windows x64, JDK .msi, cai va them PATH, dong cua so PowerShell, chay lai setup.
"@
}

function InstallZulu11x86FromUrl {
  $base = Join-Path $env:LOCALAPPDATA "pos-jdk"
  if (Test-Path -LiteralPath $base) {
    foreach ($d in Get-ChildItem -Path $base -Directory -ErrorAction SilentlyContinue) {
      if ($d.Name -notlike 'zulu11*') { continue }
      $jtry = Join-Path $d.FullName "bin\java.exe"
      if (-not (Test-Path -LiteralPath $jtry)) { continue }
      $mj = Get-JavaMajorFromExe -javaExe $jtry
      if ($mj -ge $MinJdk) {
        $b = Join-Path $d.FullName "bin"
        if ($env:Path -notlike "*$b*") { $env:Path = $b + ";" + $env:Path }
        $env:JAVA_HOME = $d.FullName
        return $mj, $jtry, $d.FullName
      }
    }
  }
  if (-not (Test-Path -LiteralPath $base)) { New-Item -ItemType Directory -Force -Path $base | Out-Null }
  $zip = Join-Path $env:TEMP "zulu11-win-x86.zip"
  Write-Host ""
  Write-Host "May 32-bit: dang tai JDK 11 (Zulu) ... (can vao duoc cdn.azul.com)" -ForegroundColor Cyan
  try {
    Invoke-WebRequest -Uri $Zulu11WinX86Url -OutFile $zip -UseBasicParsing
  } catch {
    throw "Loi khi tai JDK: $_. Hay tai thu cong: $Zulu11WinX86Url va giai nen vao: $base"
  }
  Expand-Archive -Path $zip -DestinationPath $base -Force
  $sub = Get-ChildItem -Path $base -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'zulu11.*(win_i686|i686|x86)' } | Select-Object -First 1
  if (-not $sub) { $sub = Get-ChildItem -Path $base -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'zulu11*' } | Select-Object -First 1 }
  if (-not $sub) { throw "Giai nen xong: khong thay thu muc zulu11* trong $base" }
  $dest = $sub.FullName
  $je = Join-Path $dest "bin\java.exe"
  if (-not (Test-Path -LiteralPath $je)) { throw "Khong thay $je" }
  $ma = Get-JavaMajorFromExe -javaExe $je
  if ($ma -lt $MinJdk) { throw "JDK vua tai co phien ban $ma, can $MinJdk+" }
  $b = Join-Path $dest "bin"
  $env:Path = $b + ";" + $env:Path
  $env:JAVA_HOME = $dest
  return $ma, $je, $dest
}

function Install-Jdk11 {
  if (Test-Is32BitWindows) {
    $m0, $j0, $h0 = InstallZulu11x86FromUrl
    return $m0, $j0, $h0
  }
  if (Get-Command winget -ErrorAction SilentlyContinue) {
    Write-Host ""
    Write-Host 'Dang cai Eclipse Temurin JDK 11 (winget, may 64-bit)...' -ForegroundColor Cyan
    $argsW = @(
      "install", "-e", "--id", "EclipseAdoptium.Temurin.11.JDK",
      "--accept-package-agreements", "--accept-source-agreements"
    )
    $null = Start-Process -FilePath "winget" -ArgumentList $argsW -Wait -PassThru -NoNewWindow
    Write-Host 'Cho ghi nhanh sau khi cai MSI (5 giay)...' -ForegroundColor DarkGray
    Start-Sleep -Seconds 5
    for ($i = 0; $i -le 3; $i++) {
      Refresh-PathFromRegistry
      $ma, $jPath, $jHome = Apply-Java11ToSession
      if ($ma -ge $MinJdk) { return $ma, $jPath, $jHome }
      if ($i -lt 3) { Start-Sleep -Seconds 2 }
    }
    $aa, $bb, $cc = Apply-Java11ToSession
    if ($aa -ge $MinJdk) { return $aa, $bb, $cc }
  }
  Write-Host ""
  Write-Host (Get-NoWingetInstallHint) -ForegroundColor Yellow
  throw "Cai JDK 11 that bai. Lam theo huong dan o tren (Temurin 11 x64) roi chay lai setup."
}

function Get-JavaScanHint {
  $h = if (Get-Command java -ErrorAction SilentlyContinue) {
    $p = (Get-Command java).Source
    $v = Get-JavaMajorFromExe -javaExe $p
    "  java dang thay: $p  (loai $v. Can $MinJdk+).`n"
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

function Apply-Java11ToSession {
  Refresh-PathFromRegistry
  $ma, $jPath = Get-JavaMajor
  if ($ma -ge $MinJdk) { return $ma, $jPath, $env:JAVA_HOME }
  $sysJh = Get-JavaHomeFromSystemEnv
  if ($sysJh) {
    $a1, $b1, $c1 = Try-JdkRoot -root $sysJh
    if ($a1 -ge $MinJdk) { return $a1, $b1, $c1 }
  }
  $a2, $b2, $c2 = Try-Java11InPath
  if ($a2 -ge $MinJdk) { return $a2, $b2, $c2 }
  foreach ($jdkR in (Get-JavaHomeFromRegistry)) {
    if (-not $jdkR) { continue }
    $a3, $b3, $c3 = Try-JdkRoot -root $jdkR
    if ($a3 -ge $MinJdk) { return $a3, $b3, $c3 }
  }
  $m3, $j3, $h3 = Find-UseJava11UnderRoots
  if ($m3 -ge $MinJdk) { return $m3, $j3, $h3 }
  $m4, $j4, $h4 = Find-Java11WhereR
  if ($m4 -ge $MinJdk) { return $m4, $j4, $h4 }
  return 0, $null, $null
}

# --- chinh: JDK 11+ ---
$ver, $jPath, $jHome = Apply-Java11ToSession
if ($ver -ge $MinJdk) {
  Write-Host ("OK: Phat hien Java {0} ({1}) - dung, bo qua cai JDK." -f $ver, $jPath) -ForegroundColor Green
} else {
  if ($ver -gt 0) {
    Write-Host "Hien co Java $ver, ung dung can JDK $MinJdk+ . Dang cai them..." -ForegroundColor Yellow
  } else {
    if (Test-Is32BitWindows) {
      Write-Host "Chua co JDK $MinJdk+ tren may 32-bit. Se tai Zulu 11 (x86)..." -ForegroundColor Yellow
    } else {
      Write-Host "Chua co JDK $MinJdk+ trong PATH. Dang cai Temurin 11 (neu co winget)..." -ForegroundColor Yellow
    }
  }
  $ver2, $jPath2, $jHome2 = Install-Jdk11
  if ($ver2 -lt $MinJdk) {
    $hint = Get-JavaScanHint
    throw "Van khong chay duoc java $MinJdk+. Thu: dong PWSH, dat JAVA_HOME tro thu muc JDK-11+ (co bin\java.exe), chay lai setup.`n$hint"
  }
  Write-Host ("OK: Java {0} san sang ({1})" -f $ver2, $jPath2) -ForegroundColor Green
}

# --- mvn: profile may 32-bit (OpenJFX win-x86) ---
$mvnArgs = @("-DskipTests", "package")
if (Test-Is32BitWindows) {
  $mvnArgs = @("-Ppos32", "-DskipTests", "package")
  Write-Host ""
  Write-Host "Build: profile pos32 (JavaFX nen Windows 32-bit)..." -ForegroundColor DarkCyan
}

# --- build ---
$Mvnw = Join-Path $Root "mvnw.cmd"
if (-not (Test-Path $Mvnw)) { throw "Khong tim thay mvnw.cmd. Thu muc giai nen co bi thieu file khong?" }

$prebuilt = Join-Path $Root "target\pos-app.jar"
if (Test-Path $prebuilt) {
  Write-Host ""
  Write-Host 'Da co target\pos-app.jar: bo qua mvn package. Xoa JAR do neu can build lai.' -ForegroundColor Yellow
} else {
  Write-Host ""
  Write-Host "Dang chay: mvnw $($mvnArgs -join ' ') (lan dau se tai Maven, can mang)..." -ForegroundColor Cyan
  if ($env:JAVA_HOME) {
    Write-Host "  JAVA_HOME = $env:JAVA_HOME" -ForegroundColor DarkGray
  }
  & $Mvnw @mvnArgs
  if ($LASTEXITCODE -ne 0) { throw ("mvn package that bai, ma thoat: {0}. Dong POS dang chay (neu co) roi thu lai." -f $LASTEXITCODE) }
}

$jar1 = Join-Path $Root "target\pos-app.jar"
$jar2 = Join-Path $Root "pos-app.jar"
if (-not (Test-Path $jar1) -and -not (Test-Path $jar2)) {
  throw "Sau khi build chua thay pos-app.jar. Kiem tra log phia tren."
}

# --- shortcut ---
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
Write-Host "  (May 32-bit: da build voi -Ppos32; can JDK 11 x86, run.bat dung JAR + JavaFX 32)" -ForegroundColor DarkGray
Write-Host '  Ghi chu: file zip tu may khac - chuot phai > Properties > Unblock neu Windows chan.' -ForegroundColor DarkGray
Write-Host '  Vao Desktop, bam dup "POS nha".' -ForegroundColor Gray
Write-Host ""
