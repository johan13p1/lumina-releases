# Lumina's link to Spotify, through Windows' own media session (what the volume flyout shows too), so it
# needs no Spotify login. Prints one JSON line per update; reads play_pause, next, previous and
# "seek <seconds>" from stdin. It ends by itself when stdin closes, which happens when the game exits.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
	$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

# called by reflection because PowerShell cannot pick the overload for the COM objects Windows hands out
$asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object {
	$_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1
} | Select-Object -First 1

function Await($operation, [Type]$resultType) {
	$task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
	if (-not $task.Wait(5000)) { throw 'Windows media session did not answer' }
	$task.Result
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$playing = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus, Windows.Media.Control, ContentType = WindowsRuntime]::Playing

$manager = Await ($managerType::RequestAsync()) $managerType
$stdin = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)

function Find-Spotify {
	foreach ($session in $manager.GetSessions()) {
		if ($session.SourceAppUserModelId -match 'spotify') { return $session }
	}
	$null
}

function Read-Cover($properties) {
	if ($null -eq $properties.Thumbnail) { return $null }
	$stream = Await ($properties.Thumbnail.OpenReadAsync()) $streamType
	try {
		$source = $asStream.Invoke($null, @($stream))
		$bytes = New-Object System.IO.MemoryStream
		$source.CopyTo($bytes)
		[Convert]::ToBase64String($bytes.ToArray())
	} finally {
		$null = [System.Runtime.InteropServices.Marshal]::ReleaseComObject($stream)
	}
}

function Send-Update($session) {
	$properties = Await ($session.TryGetMediaPropertiesAsync()) $propertiesType
	$info = $session.GetPlaybackInfo()
	$timeline = $session.GetTimelineProperties()
	$isPlaying = $info.PlaybackStatus -eq $playing
	$position = $timeline.Position.TotalSeconds
	# Spotify reports its position only now and then, together with the time it was true
	if ($isPlaying) { $position += ([DateTimeOffset]::UtcNow - $timeline.LastUpdatedTime).TotalSeconds }
	$update = @{
		open = $true
		title = $properties.Title
		artist = $properties.Artist
		playing = $isPlaying
		position = $position
		duration = $timeline.EndTime.TotalSeconds
		canSeek = $info.Controls.IsPlaybackPositionEnabled
	}
	# A new song's cover can arrive after its title, and a paused Spotify may have none at all. So ask
	# until one comes, then a few more times in case that was still the old song's.
	$track = "$($properties.Title)|$($properties.Artist)"
	if ($track -ne $script:track) {
		$script:track = $track
		$script:coverReads = 4
	}
	if ($script:coverReads -gt 0) {
		$cover = Read-Cover $properties
		if ($cover) {
			$script:coverReads--
			if ($cover -ne $script:cover) {
				$script:cover = $cover
				$update.cover = $cover
			}
		}
	}
	[Console]::Out.WriteLine(($update | ConvertTo-Json -Compress))
	[Console]::Out.Flush()
}

function Run-Command($session, [string]$command) {
	switch -Regex ($command) {
		'^play_pause$' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
		'^next$' { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
		'^previous$' { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
		'^seek (\d+(\.\d+)?)$' {
			$ticks = [long]([double]::Parse($Matches[1], [Globalization.CultureInfo]::InvariantCulture) * 10000000)
			$null = Await ($session.TryChangePlaybackPositionAsync($ticks)) ([bool])
		}
	}
}

$wasOpen = $null
$pending = $stdin.ReadLineAsync()
while ($true) {
	$session = $null
	try {
		$session = Find-Spotify
		if ($null -ne $session) {
			Send-Update $session
			$wasOpen = $true
		} elseif ($wasOpen -ne $false) {
			[Console]::Out.WriteLine('{"open":false}')
			[Console]::Out.Flush()
			$script:track = $null
			$script:cover = $null
			$wasOpen = $false
		}
	} catch {
		[Console]::Error.WriteLine($_)
	}
	# wakes up right away for a command; a closed Spotify is looked for less often
	if ($pending.Wait($(if ($null -ne $session) { 1000 } else { 3000 }))) {
		$command = $pending.Result
		if ($null -eq $command) { exit 0 }
		try {
			if ($null -ne $session) {
				Run-Command $session $command.Trim()
				# Spotify needs a moment; reporting right away would show the old state again
				Start-Sleep -Milliseconds 250
			}
		} catch {
			[Console]::Error.WriteLine($_)
		}
		$pending = $stdin.ReadLineAsync()
	}
}
