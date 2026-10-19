# Builds the release APK and publishes it on the site (TPSArena_Site\releases\android\).
# Windows version of build_release.sh (same steps).
#
#   tools\build_release.ps1            build + copy
#   tools\build_release.ps1 -NoCopy    build only
param([switch]$NoCopy)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$site = Join-Path '..' 'TPSArena_Site'
$caSrc = Join-Path $site 'certs/tps-arena-ca.crt'
$caDst = 'app/src/main/res/raw/tps_arena_ca.crt'

if (Test-Path $caSrc) {
    $text = Get-Content -Raw $caSrc
    if ($text -notmatch 'BEGIN CERTIFICATE' -or $text -match 'PRIVATE KEY') {
        throw "$caSrc n'est pas un certificat public : abandon."
    }
    Copy-Item $caSrc $caDst -Force
    Write-Host "CA du site copiée dans $caDst"
} else {
    Write-Host "Pas de $caSrc : la CA déjà présente dans l'app est gardée."
}

& .\gradlew.bat --console=plain assembleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$apk = 'app/build/outputs/apk/release/app-release.apk'

if (-not $NoCopy) {
    $dest = Join-Path $site 'releases/android'
    New-Item -ItemType Directory -Force $dest | Out-Null
    Copy-Item $apk (Join-Path $dest 'TPSArenaCompanion.apk') -Force
    Write-Host "APK publié : $dest/TPSArenaCompanion.apk (le site le voit en 30 s)"
} else {
    Write-Host "APK : $apk"
}
