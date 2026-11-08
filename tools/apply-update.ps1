# Installs LuminaClient 1.8.1 into the installed launcher as soon as LuminaClient.exe is closed,
# then starts the launcher again. Only program files are replaced; worlds, mods, accounts and
# settings stay untouched. Can also be run by hand:
#   powershell -ExecutionPolicy Bypass -File "%LOCALAPPDATA%\Programs\LuminaClient-update-1.8.1\apply-update.ps1"
param(
	[string]$Inst = (Join-Path $env:LOCALAPPDATA 'Programs\LuminaClient'),
	[string]$Backup = (Join-Path $env:LOCALAPPDATA 'Programs\LuminaClient-backup-1.8.0-vor-1.8.1'),
	[string]$ProcessName = 'LuminaClient'
)

$ErrorActionPreference = 'Stop'
$stage = $PSScriptRoot
$log = Join-Path $stage 'apply-update.log'
$files = @('LuminaClient.exe', 'resources\app.asar', 'resources\lumina-client.jar', 'resources\lumina-client-26.2.jar', 'resources\lumina-client-26.3.jar')

function Log($msg) { Add-Content -Path $log -Value "$(Get-Date -Format s) $msg" }
function Hash($path) { (Get-FileHash $path -Algorithm SHA256).Hash }

function Applied {
	foreach ($f in $files) {
		$dst = Join-Path $Inst $f
		if (-not (Test-Path $dst)) { return $false }
		if ((Hash (Join-Path $stage $f)) -ne (Hash $dst)) { return $false }
	}
	return $true
}

$asar = Join-Path $Inst 'resources\app.asar'
$startHash = Hash $asar
$backedUp = $false
$copying = $false
$installed = $false
Log 'waiting for the launcher to close'
while ($true) {
	while (Get-Process $ProcessName -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 1 }
	# its helper processes need a moment to let go of the files; a quick reopen is caught here
	Start-Sleep -Seconds 3
	if (Get-Process $ProcessName -ErrorAction SilentlyContinue) { continue }
	# something newer was installed while waiting: never put an older version back
	if (-not $copying -and (Hash $asar) -ne $startHash) {
		Log 'the installed launcher changed while waiting, nothing to do'
		break
	}
	try {
		if (-not $backedUp) {
			foreach ($f in $files) {
				$old = Join-Path $Backup $f
				New-Item -ItemType Directory -Force -Path (Split-Path $old) | Out-Null
				Copy-Item (Join-Path $Inst $f) $old -Force
			}
			$backedUp = $true
			Log "old files saved in $Backup"
		}
		$copying = $true
		foreach ($f in $files) {
			Copy-Item (Join-Path $stage $f) (Join-Path $Inst $f) -Force
		}
		if (Applied) {
			Log '1.8.1 installed'
			$installed = $true
			break
		}
		Log 'copied, but files differ - retrying'
		Start-Sleep -Seconds 2
	} catch {
		Log "copy failed, retrying: $($_.Exception.Message)"
		Start-Sleep -Seconds 2
	}
}
if ($installed) {
	Start-Process (Join-Path $Inst 'LuminaClient.exe')
	Log 'launcher started'
}
