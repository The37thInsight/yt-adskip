# Build a signed release and publish it on GitHub Releases (Obtainium on the phone picks it up).
# Usage: .\release.ps1 0.2.0
param([Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path "$HOME\.android-keys\yt-adskip.properties")) {
    throw "Release key missing: $HOME\.android-keys\yt-adskip.properties (see README, 'Engångsuppsättning')."
}
if (git status --porcelain) { throw "Working tree is not clean; commit first." }

$gradleFile = "app\build.gradle.kts"
$src = [IO.File]::ReadAllText("$PSScriptRoot\$gradleFile")
$code = [int][regex]::Match($src, 'versionCode = (\d+)').Groups[1].Value + 1
$src = $src -replace 'versionCode = \d+', "versionCode = $code" -replace 'versionName = "[^"]*"', ('versionName = "' + $Version + '"')
[IO.File]::WriteAllText("$PSScriptRoot\$gradleFile", $src)

.\gradlew.bat test assembleRelease --console=plain
if ($LASTEXITCODE) { throw "Build failed" }

$apk = "app\build\outputs\apk\release\yt-adskip-$Version.apk"
Copy-Item "app\build\outputs\apk\release\app-release.apk" $apk -Force

git commit -am "Release v$Version"
git tag "v$Version"
git push --follow-tags
gh release create "v$Version" $apk --title "v$Version" --notes "versionCode $code"
