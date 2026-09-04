[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path -LiteralPath $Path).Path
$violations = [Collections.Generic.List[string]]::new()

function Add-Violation([string]$Message) {
    $violations.Add($Message)
}

$allowedRootDirectories = @(
    '.github',
    'app',
    'core',
    'docs',
    'gradle',
    'iptv',
    'scripts',
    'sportmate'
)
$allowedRootFiles = @(
    '.editorconfig',
    '.gitattributes',
    '.gitignore',
    'build.gradle.kts',
    'CONTRIBUTING.md',
    'gradle.properties',
    'gradlew',
    'gradlew.bat',
    'INSTALL.md',
    'LICENSE',
    'LICENSE-APACHE-2.0.txt',
    'LICENSE-KXML2.txt',
    'PRIVACY.md',
    'README.md',
    'RELEASE_NOTES.md',
    'SECURITY.md',
    'settings.gradle.kts',
    'TESTING.md',
    'THIRD_PARTY_NOTICES.md'
)

$gitDirectory = Join-Path $releaseRoot '.git'
$candidatePaths = if (Test-Path -LiteralPath $gitDirectory) {
    @(& git -C $releaseRoot ls-files --cached --others --exclude-standard)
} else {
    @(Get-ChildItem -LiteralPath $releaseRoot -Recurse -Force -File |
        ForEach-Object { $_.FullName.Substring($releaseRoot.Length).TrimStart([char[]]'\/') })
}

foreach ($topLevelName in @($candidatePaths | ForEach-Object { ($_ -split '[\\/]')[0] } | Sort-Object -Unique)) {
    $isRootFile = $topLevelName -notin $allowedRootDirectories
    if ($isRootFile -and $allowedRootFiles -notcontains $topLevelName) {
        Add-Violation "${topLevelName}: unapproved top-level entry"
    }
}

$forbiddenExtensions = @(
    '.aab', '.apk', '.apks', '.bak', '.db', '.jks', '.keystore', '.log',
    '.m3u', '.m3u8', '.smbak', '.sqlite', '.xmltv', '.zip'
)
$forbiddenNames = @(
    'keystore.properties', 'local.properties', 'secrets.properties'
)
$textExtensions = @(
    '', '.css', '.gradle', '.html', '.java', '.js', '.json', '.kt', '.kts',
    '.md', '.pro', '.properties', '.ps1', '.sh', '.svg', '.toml', '.txt',
    '.xml', '.yml', '.yaml'
)

$files = @($candidatePaths | ForEach-Object {
    $candidate = Join-Path $releaseRoot $_
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { Get-Item -LiteralPath $candidate }
})

foreach ($file in $files) {
    $relativePath = $file.FullName.Substring($releaseRoot.Length).TrimStart([char[]]'\/')
    $extension = $file.Extension.ToLowerInvariant()

    if ($forbiddenNames -contains $file.Name -or $forbiddenExtensions -contains $extension) {
        Add-Violation "${relativePath}: prohibited private or generated file"
        continue
    }
    if ($relativePath -match '(^|[\\/])build([\\/]|$)' -or
        $relativePath -match '^(artifacts|assets|captures|concept_images|distribution|\.agents|\.codex|\.codex-artifacts|\.gradle|\.idea|\.kotlin|\.local)([\\/]|$)') {
        Add-Violation "${relativePath}: prohibited generated or internal path"
        continue
    }
    if ($relativePath -match '(?i)(handoff|implementation_plan|ui_and_system_improvement_plan)') {
        Add-Violation "${relativePath}: internal planning or handoff file"
        continue
    }
    if ($textExtensions -notcontains $extension) { continue }
    if ($relativePath -eq 'scripts\Test-PublicSourceContent.ps1' -or
        $relativePath -eq 'scripts/Test-PublicSourceContent.ps1') {
        continue
    }

    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName) {
        $lineNumber++
        $location = "${relativePath}:$lineNumber"
        if ($line -match '(?i)\b(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{30,}|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{10,})\b') {
            Add-Violation "${location}: credential-shaped token"
        }
        if ($line -match '-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----') {
            Add-Violation "${location}: private key material"
        }
        $isReservedCredentialFixture =
            $relativePath -match '(^|[\\/])src[\\/](test|androidTest)[\\/]' -and
            $line -match '(?i)@[^\s/]*(\.example|\.test)(:\d+)?([/\s]|$)'
        if ($line -match '(?i)https?://[^\s/:@]+:[^\s/@]+@' -and
            -not $isReservedCredentialFixture) {
            Add-Violation "${location}: URL contains embedded credentials"
        }
        if ($line -match '(?i)\b[A-Z]:\\(Users|SportMate|Android\\sdk)\\') {
            Add-Violation "${location}: machine-specific Windows path"
        }
        if ($line -match '(?i)(/root/[^\s]*backup|\b192\.168\.\d{1,3}\.\d{1,3}\b|\b[\w.-]+\.home\.arpa\b)') {
            Add-Violation "${location}: private network or backup location"
        }
        if ($line -match '(?i)\b[A-Z0-9._%+-]+@(gmail|hotmail|outlook|protonmail|yahoo)\.[A-Z]{2,}\b') {
            Add-Violation "${location}: personal email address"
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Error ("Public source audit failed:`n - " + ($violations -join "`n - "))
    exit 1
}

Write-Output "Public source audit passed for $($files.Count) files."
