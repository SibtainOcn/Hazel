<#
.SYNOPSIS
    Cuts a Hazel release: bumps the versions, writes the store changelogs, tags, pushes.

.DESCRIPTION
    Every release moves the same four things, and every one of them is silent when it goes
    wrong. A version code that did not move produces APKs Android refuses to install over
    the last ones. A changelog file named after the old code leaves the new release with a
    blank changelog on F-Droid. A tag pushed before the commit builds the wrong tree.

    So this does all of it in one order, and refuses to start if the working tree is dirty
    or the tag already exists.

    Nothing here is destructive until the very last step. Use -DryRun to see the whole plan
    without touching anything, and note that the push is the only part that leaves the
    machine.

.PARAMETER Version
    The version being cut, without the leading v. For example 1.0.6.

.PARAMETER DryRun
    Print what would happen and change nothing.

.PARAMETER NoPush
    Do everything locally, including the commit and the tag, but do not push. Useful when
    you want to look at the commit before it becomes a release.

.EXAMPLE
    .\tools\release.ps1 -Version 1.0.6 -DryRun
    .\tools\release.ps1 -Version 1.0.6
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$')]
    [string]$Version,

    [switch]$DryRun,
    [switch]$NoPush
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Info($msg) { Write-Host "    $msg" -ForegroundColor Gray }
function Warn($msg) { Write-Host "    $msg" -ForegroundColor Yellow }
function Die($msg)  { Write-Host "`nSTOPPED: $msg" -ForegroundColor Red; exit 1 }

$tag = "v$Version"
$props = Join-Path $repo 'gradle.properties'
$changelog = Join-Path $repo 'CHANGELOG.md'

# ---------------------------------------------------------------- checks

Step "Checking the repository"

if (-not (Test-Path $props))     { Die "gradle.properties not found. Run this from the repo." }
if (-not (Test-Path $changelog)) { Die "CHANGELOG.md not found." }

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
Info "branch: $branch"
if ($branch -ne 'main') {
    Warn "You are not on main. A release is normally cut from main."
}

$dirty = git status --porcelain
if ($dirty -and -not $DryRun) {
    Die "The working tree has uncommitted changes. Commit or stash them first:`n$dirty"
}

git fetch --tags --quiet 2>$null
$existing = git tag --list $tag
if ($existing) { Die "Tag $tag already exists. Pick a new version; never move a published tag." }

# The version code only ever increases, so read the current one and add one.
$propsText = Get-Content $props -Raw
if ($propsText -notmatch '(?m)^HAZEL_VERSION_CODE=(\d+)\s*$') {
    Die "HAZEL_VERSION_CODE not found in gradle.properties."
}
$oldCode = [int]$Matches[1]
$newCode = $oldCode + 1

if ($propsText -notmatch '(?m)^HAZEL_VERSION_NAME=(.+?)\s*$') {
    Die "HAZEL_VERSION_NAME not found in gradle.properties."
}
$oldName = $Matches[1]

Info "version name: $oldName -> $Version"
Info "version code: $oldCode -> $newCode"

# Each architecture takes the last two digits, so one bump numbers the whole release.
$abis = [ordered]@{ universal = 0; 'armeabi-v7a' = 1; x86 = 2; x86_64 = 3; 'arm64-v8a' = 4 }
Info "APK version codes:"
foreach ($abi in $abis.Keys) {
    '      {0,-12} {1}' -f $abi, ($newCode * 100 + $abis[$abi]) | Write-Host -ForegroundColor Gray
}

# ---------------------------------------------------------------- changelog

Step "Checking CHANGELOG.md"

$clText = Get-Content $changelog -Raw
if ($clText -match "(?m)^##\s*\[$([regex]::Escape($Version))\]") {
    Info "a [$Version] section already exists, leaving it alone"
} elseif ($clText -match '(?m)^##\s*\[Unreleased\]\s*$') {
    $today = Get-Date -Format 'yyyy-MM-dd'
    Info "promoting [Unreleased] to [$Version] - $today"
    # The Unreleased heading stays, empty, above the new one, ready for the next release.
    $clText = $clText -replace '(?m)^##\s*\[Unreleased\]\s*$', "## [Unreleased]`r`n`r`n## [$Version] - $today"
    if (-not $DryRun) { Set-Content $changelog $clText -NoNewline -Encoding UTF8 }
} else {
    Die "CHANGELOG.md has neither a [$Version] section nor an [Unreleased] heading to promote."
}

# ---------------------------------------------------------------- write

Step "Updating gradle.properties"

$newProps = $propsText `
    -replace '(?m)^HAZEL_VERSION_NAME=.*$', "HAZEL_VERSION_NAME=$Version" `
    -replace '(?m)^HAZEL_VERSION_CODE=\d+\s*$', "HAZEL_VERSION_CODE=$newCode"

if ($DryRun) {
    Info "would write HAZEL_VERSION_NAME=$Version and HAZEL_VERSION_CODE=$newCode"
} else {
    Set-Content $props $newProps -NoNewline -Encoding UTF8
    Info "written"
}

Step "Generating the store changelogs"

$gradlew = Join-Path $repo 'gradlew.bat'
if ($DryRun) {
    Info "would run: .\gradlew.bat :app:generateFastlaneChangelogs -PVERSION_NAME=$Version"
} else {
    & $gradlew ':app:generateFastlaneChangelogs' "-PVERSION_NAME=$Version" --quiet
    if ($LASTEXITCODE -ne 0) { Die "generateFastlaneChangelogs failed. Nothing has been committed." }
    $written = Get-ChildItem 'fastlane/metadata/android/en-US/changelogs' -Filter "$newCode??.txt" -ErrorAction SilentlyContinue
    if (-not $written) {
        Warn "no changelog files matching $newCode??.txt were produced. Check the task output."
    } else {
        Info ("wrote: " + (($written | ForEach-Object { $_.Name }) -join ', '))
    }
}

# ---------------------------------------------------------------- commit and tag

Step "Committing and tagging"

if ($DryRun) {
    Info "would commit gradle.properties, CHANGELOG.md and the changelogs"
    Info "would tag $tag"
    if (-not $NoPush) { Info "would push the branch and the tag to origin" }
    Write-Host "`nDry run only. Nothing was changed." -ForegroundColor Green
    exit 0
}

git add gradle.properties CHANGELOG.md fastlane/metadata/android/en-US/changelogs
$staged = git diff --cached --name-only
if (-not $staged) { Die "Nothing to commit. Was this release already prepared?" }
Info ("staged: " + ($staged -join ', '))

git commit -q -m "Release $Version"
if ($LASTEXITCODE -ne 0) { Die "git commit failed." }

git tag -a $tag -m "Hazel $tag"
if ($LASTEXITCODE -ne 0) { Die "git tag failed. The commit is made; fix the tag and push by hand." }
Info "tagged $tag at $(git rev-parse --short HEAD)"

if ($NoPush) {
    Write-Host "`nDone locally. Nothing pushed." -ForegroundColor Green
    Write-Host "  git push origin $branch && git push origin $tag" -ForegroundColor Gray
    exit 0
}

Step "Pushing"
Warn "This publishes the release. The tag triggers the release workflow."

# main is protected and rejects a direct push, so the branch goes up only when it can.
git push origin $branch
if ($LASTEXITCODE -ne 0) {
    Warn "Pushing $branch was refused, which is expected when the branch is protected."
    Warn "Open a pull request for the release commit, merge it, then push the tag:"
    Warn "  git push origin $tag"
    exit 1
}

git push origin $tag
if ($LASTEXITCODE -ne 0) { Die "Pushing the tag failed. The commit is on $branch; push the tag by hand." }

Write-Host "`nReleased $tag." -ForegroundColor Green
Write-Host "  Watch the build:  gh run list --workflow=Release --limit 1" -ForegroundColor Gray
Write-Host "  F-Droid picks this up on its own once the recipe is accepted." -ForegroundColor Gray
