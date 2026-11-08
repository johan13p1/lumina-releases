# Builds LuminaClient: the Fabric mod and the Windows launcher installer.
#
#   .\build.ps1                    build everything (mod + installer)
#   .\build.ps1 -SkipInstaller     only build the mod and copy it into the launcher
#   .\build.ps1 -Publish           build and upload a new release to GitHub; every installed
#                                  launcher downloads it by itself (auto update)
#   .\build.ps1 -Version 1.2.0     set the version (launcher + mod) before building
#
# The version lives in launcher\package.json; the mod gets the same one. With -Publish and no
# -Version, the patch number is raised automatically when the current version is already released.
# Release notes come from the matching "## <version>" section in CHANGELOG.md (or -Notes "...").
# Publishing needs a GitHub token with write access to the releases repo: either in $env:GH_TOKEN
# or in .secrets\github-token.txt (that folder is never part of a build).
#
# JDK 21 (mod for 1.21.11), JDK 25 (mods for 26.2 and 26.3) and Node.js are downloaded into .tools\ on first
# run if they are missing.

param([switch]$SkipInstaller, [switch]$Publish, [string]$Version, [string]$Notes)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot
$tools = Join-Path $root '.tools'
$packageJson = Join-Path $root 'launcher\package.json'
# one Lumina mod per Minecraft version: project folder, JDK and jar name inside the launcher
$modBuilds = @(
	@{ Dir = 'mod'; Jdk = 'jdk21'; Jar = 'lumina-client.jar' },
	@{ Dir = 'mod-26.2'; Jdk = 'jdk25'; Jar = 'lumina-client-26.2.jar' },
	@{ Dir = 'mod-26.3'; Jdk = 'jdk25'; Jar = 'lumina-client-26.3.jar' }
)

function Ensure-Tool($name, $exe, $url, $folder) {
	$target = Join-Path $tools $folder
	if (Test-Path (Join-Path $target $exe)) { return }
	Write-Host "Downloading $name..."
	New-Item -ItemType Directory -Force $tools | Out-Null
	$zip = Join-Path $tools "$folder.zip"
	curl.exe -sSL -o $zip $url
	$tmp = Join-Path $tools "$folder.tmp"
	Expand-Archive -Force $zip $tmp
	Remove-Item $zip
	$inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
	Move-Item $inner.FullName $target
	Remove-Item -Recurse -Force $tmp
}

function Read-Package { Get-Content $packageJson -Raw -Encoding UTF8 | ConvertFrom-Json }

function Set-Version($v) {
	# text replace keeps the formatting of both files
	$json = Get-Content $packageJson -Raw -Encoding UTF8
	$json = $json -replace '("version"\s*:\s*")[^"]+(")', "`${1}$v`${2}"
	[IO.File]::WriteAllText($packageJson, $json, (New-Object Text.UTF8Encoding $false))
	foreach ($b in $modBuilds) {
		$gradleProps = Join-Path $root "$($b.Dir)\gradle.properties"
		$props = Get-Content $gradleProps -Raw -Encoding UTF8
		$props = $props -replace '(?m)^version=.*$', "version=$v"
		[IO.File]::WriteAllText($gradleProps, $props, (New-Object Text.UTF8Encoding $false))
	}
}

Ensure-Tool 'JDK 21' 'bin\java.exe' 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' 'jdk21'
Ensure-Tool 'JDK 25' 'bin\java.exe' 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse' 'jdk25'
Ensure-Tool 'Node.js' 'node.exe' 'https://nodejs.org/dist/v24.22.1/node-v24.22.1-win-x64.zip' 'node'

$env:JAVA_HOME = Join-Path $tools 'jdk21'
$env:PATH = "$env:JAVA_HOME\bin;$(Join-Path $tools 'node');$env:PATH"

# ---------------------------------------------------------------- version + release settings
$pkg = Read-Package
$publishTarget = @($pkg.build.publish)[0]
if ($Publish) {
	if (-not $publishTarget -or $publishTarget.owner -eq 'DEIN-GITHUB-NAME') {
		throw 'Trage zuerst deinen GitHub-Namen in launcher\package.json ein (build.publish.owner).'
	}
	if (-not $env:GH_TOKEN) {
		$tokenFile = Join-Path $root '.secrets\github-token.txt'
		if (-not (Test-Path $tokenFile)) { throw "Kein GitHub-Token gefunden. Lege ihn in .secrets\github-token.txt ab oder setze `$env:GH_TOKEN." }
		$env:GH_TOKEN = (Get-Content $tokenFile -Raw).Trim()
	}
	if (-not $Version) {
		# raise the patch number when this version is already out
		$latest = $null
		try {
			$headers = @{ Authorization = "Bearer $env:GH_TOKEN"; 'User-Agent' = 'LuminaClient-build' }
			$release = Invoke-RestMethod -Headers $headers "https://api.github.com/repos/$($publishTarget.owner)/$($publishTarget.repo)/releases/latest"
			$latest = [version]($release.tag_name.TrimStart('v'))
		} catch {
			if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -ne 404) { throw "GitHub nicht erreichbar: $($_.Exception.Message)" }
		}
		$current = [version]$pkg.version
		if ($latest -and $current -le $latest) {
			$Version = "$($latest.Major).$($latest.Minor).$($latest.Build + 1)"
			Write-Host "Version $current ist schon veröffentlicht -> neue Version $Version"
		}
	}
}
if ($Version) {
	if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Version muss wie 1.2.3 aussehen: $Version" }
	Set-Version $Version
	$pkg = Read-Package
}
$version = $pkg.version
# the mod always carries the launcher's version
Set-Version $version
Write-Host "LuminaClient $version" -ForegroundColor Cyan

# ---------------------------------------------------------------- mods (one per Minecraft version)
New-Item -ItemType Directory -Force (Join-Path $root 'launcher\resources') | Out-Null
foreach ($b in $modBuilds) {
	Write-Host "`n== Building the Lumina mod ($($b.Dir))" -ForegroundColor Cyan
	$env:JAVA_HOME = Join-Path $tools $b.Jdk
	Push-Location (Join-Path $root $b.Dir)
	try {
		& .\gradlew.bat build --console=plain
		if ($LASTEXITCODE -ne 0) { throw "Mod build failed ($($b.Dir))" }
	} finally { Pop-Location }

	$jar = Get-ChildItem (Join-Path $root "$($b.Dir)\build\libs") -Filter "lumina-$version.jar" | Select-Object -First 1
	if (-not $jar) { throw "$($b.Dir)\build\libs\lumina-$version.jar fehlt" }
	Copy-Item $jar.FullName (Join-Path $root "launcher\resources\$($b.Jar)") -Force
	Write-Host "Mod: $($b.Dir)\$($jar.Name) -> launcher\resources\$($b.Jar)"
}
$env:JAVA_HOME = Join-Path $tools 'jdk21'

if ($SkipInstaller -and -not $Publish) { return }

# ---------------------------------------------------------------- release notes
$notesFile = Join-Path $root 'launcher\build\release-notes.md'
if (-not $Notes) {
	$changelog = Join-Path $root 'CHANGELOG.md'
	if (Test-Path $changelog) {
		$text = Get-Content $changelog -Raw -Encoding UTF8
		$m = [regex]::Match($text, "(?ms)^##\s+v?$([regex]::Escape($version))\b[^\n]*\n(.*?)(?=^##\s|\z)")
		if ($m.Success) { $Notes = $m.Groups[1].Value.Trim() }
	}
}
if ($Notes) { [IO.File]::WriteAllText($notesFile, $Notes, (New-Object Text.UTF8Encoding $false)) }
elseif (Test-Path $notesFile) { Remove-Item $notesFile }

# ---------------------------------------------------------------- GitHub release (draft first)
# electron-builder uploads its files in parallel and every upload may try to create the release;
# GitHub then refuses the second one ("Published releases must have a valid tag") and the build
# fails halfway (seen with 1.3.26). So the release exists as a draft before the upload starts and
# is published once every file is there.
$ghApi = "https://api.github.com/repos/$($publishTarget.owner)/$($publishTarget.repo)"
$ghHeaders = @{ Authorization = "Bearer $env:GH_TOKEN"; 'User-Agent' = 'LuminaClient-build'; Accept = 'application/vnd.github+json' }
function Find-Release {
	# drafts are only listed for the owner, and not found by tag
	@(Invoke-RestMethod -Headers $ghHeaders "$ghApi/releases?per_page=50") | Where-Object { $_.tag_name -eq "v$version" } | Select-Object -First 1
}
if ($Publish) {
	$release = Find-Release
	if ($release -and -not $release.draft) { throw "v$version ist auf GitHub schon veroeffentlicht. Nimm eine neue Version (-Version)." }
	if (-not $release) {
		$body = if ($Notes) { $Notes } else { '' }
		$draft = @{ tag_name = "v$version"; name = "Lumina $version"; body = $body; draft = $true } | ConvertTo-Json
		Invoke-RestMethod -Method Post -Headers $ghHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($draft)) "$ghApi/releases" | Out-Null
		Write-Host "Release-Entwurf v$version angelegt"
	}
}

# ---------------------------------------------------------------- launcher
Write-Host "`n== Building the launcher" -ForegroundColor Cyan
Push-Location (Join-Path $root 'launcher')
try {
	if (-not (Test-Path 'node_modules\electron-updater')) {
		& npm install --no-audit --no-fund
		if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
	}
	if (-not (Test-Path 'node_modules\electron\dist\electron.exe')) { & node node_modules\electron\install.js }
	$publishMode = if ($Publish) { 'always' } else { 'never' }
	& npx electron-builder --win --x64 --publish $publishMode
	if ($LASTEXITCODE -ne 0) { throw 'Launcher build failed' }
} finally { Pop-Location }

$setup = Join-Path $root "launcher\dist\LuminaClient-Setup-$version.exe"
Write-Host "`nDone: $setup" -ForegroundColor Green
if ($Publish) {
	$release = Find-Release
	$names = @($release.assets | ForEach-Object { $_.name })
	foreach ($need in @("LuminaClient-Setup-$version.exe", 'latest.yml')) {
		if ($names -notcontains $need) { throw "$need fehlt im Release v$version - der Entwurf bleibt unveroeffentlicht." }
	}
	if ($release.draft) {
		$publishBody = @{ draft = $false; make_latest = 'true' } | ConvertTo-Json
		Invoke-RestMethod -Method Patch -Headers $ghHeaders -ContentType 'application/json' -Body $publishBody "$ghApi/releases/$($release.id)" | Out-Null
	}
	Write-Host "Veroeffentlicht: https://github.com/$($publishTarget.owner)/$($publishTarget.repo)/releases/tag/v$version" -ForegroundColor Green
	Write-Host 'Alle installierten Launcher laden das Update beim naechsten Start (oder spaetestens nach 3 Stunden).'
}
