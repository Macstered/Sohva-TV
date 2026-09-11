[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+-beta\.\d+$')]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [ValidateRange(2, 2147483647)]
    [int]$VersionCode,
    [string]$Adb,
    [string]$BuildTools
)

$ErrorActionPreference = 'Stop'
$sohvaTools = & (Join-Path $PSScriptRoot 'Resolve-SohvaAndroidTools.ps1') -Adb $Adb -BuildTools $BuildTools
$sohvaApk = (Resolve-Path -LiteralPath $Path).Path
$sohvaViolations = [Collections.Generic.List[string]]::new()
function Add-SohvaViolation([string]$Message) { $sohvaViolations.Add($Message) }

$sohvaBadging = (& $sohvaTools.Aapt dump badging $sohvaApk 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect APK metadata.' }
if ($sohvaBadging -notmatch ("package: name='com\.streammate\.tv' versionCode='$VersionCode' versionName='" + [regex]::Escape($Version) + "'")) {
    Add-SohvaViolation 'Unexpected package or version.'
}
if ($sohvaBadging -notmatch "application-label:'Sohva TV'" -or $sohvaBadging -match 'application-debuggable') {
    Add-SohvaViolation 'Unexpected label or debuggable release.'
}
$sohvaPermissions = @(& $sohvaTools.Aapt dump permissions $sohvaApk |
    Select-String "^uses-permission: name='([^']+)'" |
    ForEach-Object { $_.Matches[0].Groups[1].Value })
$sohvaAllowedPermissions = @(
    'android.permission.INTERNET',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
    'android.permission.WAKE_LOCK',
    'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.RECEIVE_BOOT_COMPLETED',
    # User-approved update installation and optional programme reminders.
    'android.permission.REQUEST_INSTALL_PACKAGES',
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.SYSTEM_ALERT_WINDOW',
    'com.streammate.tv.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
)
foreach ($sohvaPermission in $sohvaPermissions) {
    if ($sohvaAllowedPermissions -notcontains $sohvaPermission) {
        Add-SohvaViolation "Unexpected permission: $sohvaPermission"
    }
}

$sohvaSignature = & $sohvaTools.ApkSigner verify --verbose --print-certs $sohvaApk 2>&1
$sohvaSignatureExit = $LASTEXITCODE
$sohvaSignature = $sohvaSignature -join "`n"
if ($sohvaSignatureExit -ne 0 -or $sohvaSignature -notmatch 'Verified using v2 scheme .*: true' -or
    $sohvaSignature -notmatch '985e87a4978e61cb1509dd857fa37db41087bb78f42ac5de64e28f4a0af9ecd6') {
    Add-SohvaViolation 'APK signature or certificate mismatch.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$sohvaArchive = [IO.Compression.ZipFile]::OpenRead($sohvaApk)
$sohvaLatin1 = [Text.Encoding]::GetEncoding(28591)
$sohvaScannedBytes = 0L
$sohvaEntryNames = [Collections.Generic.List[string]]::new()
$sohvaPayload = [Text.StringBuilder]::new()
try {
    foreach ($sohvaEntry in $sohvaArchive.Entries) {
        $sohvaEntryNames.Add($sohvaEntry.FullName)
        if ($sohvaEntry.FullName -match '(?i)(\.jks$|\.keystore$|\.p12$|\.pem$|\.env$|\.m3u8?$|\.xmltv$|credentials?|secrets?|passwords?|user[-_]?data|backup.*\.smbak$)') {
            Add-SohvaViolation "Sensitive-looking archive entry: $($sohvaEntry.FullName)"
        }
        if ($sohvaEntry.Length -gt 64MB) { Add-SohvaViolation "Unexpectedly large archive entry: $($sohvaEntry.FullName)"; continue }
        $sohvaStream = $sohvaEntry.Open()
        $sohvaMemory = [IO.MemoryStream]::new()
        try {
            $sohvaStream.CopyTo($sohvaMemory)
            $sohvaBytes = $sohvaMemory.ToArray()
            $sohvaScannedBytes += $sohvaBytes.Length
            [void]$sohvaPayload.Append($sohvaLatin1.GetString($sohvaBytes))
            [void]$sohvaPayload.Append("`n")
        } finally { $sohvaStream.Dispose(); $sohvaMemory.Dispose() }
    }
} finally { $sohvaArchive.Dispose() }

$sohvaText = $sohvaPayload.ToString()
$sohvaPatterns = [ordered]@{
    'Windows home path' = '(?i)[A-Z]:[\\/]Users[\\/]'
    # Requiring a trailing separator avoids treating app routes such as
    # /home/HomeDestination as a developer home directory.
    'Unix home path' = '/(?:home|Users)/[a-z0-9._-]+[\\/]'
    # Loopback (127.0.0.1) is a generic library constant, not a private host.
    'private IPv4 address' = '(?<![0-9])(10(?:\.[0-9]{1,3}){3}|169\.254(?:\.[0-9]{1,3}){2}|172\.(?:1[6-9]|2[0-9]|3[01])(?:\.[0-9]{1,3}){2}|192\.168(?:\.[0-9]{1,3}){2})(?![0-9])'
    'credential-bearing URL' = '(?i)https?://[^\s/:@]+:[^\s/@]+@|[?&](?:user(?:name)?|pass(?:word)?|token|api[_-]?key|key)=[^\s&#]+'
    'private key material' = '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
    'OpenAI-style secret' = '(?i)\bsk-[A-Za-z0-9_-]{20,}\b'
    'Google-style API key' = '\bAIza[0-9A-Za-z_-]{30,}\b'
    'JWT value' = '\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b'
}
# Do not embed a developer's identity or checkout location in a public script.
$sohvaCheckout = Split-Path -Parent $PSScriptRoot
$sohvaPatterns['checkout path'] = '(?i)' + [regex]::Escape($sohvaCheckout).Replace('\\', '[\\/]')
if ($env:USERNAME -and $env:USERNAME.Length -ge 4) {
    $sohvaPatterns['local username'] = '(?i)\b' + [regex]::Escape($env:USERNAME) + '\b'
}
foreach ($sohvaPattern in $sohvaPatterns.GetEnumerator()) {
    if ([regex]::IsMatch($sohvaText, $sohvaPattern.Value)) { Add-SohvaViolation "Found $($sohvaPattern.Key)." }
}

# Compare locally stored signing passwords without ever printing them. The
# public certificate identity/key alias and keystore path are not secret values.
$sohvaPropertiesPath = Join-Path (Split-Path -Parent $PSScriptRoot) '.local/streammate-signing/keystore.properties'
if (Test-Path -LiteralPath $sohvaPropertiesPath) {
    foreach ($sohvaLine in Get-Content -LiteralPath $sohvaPropertiesPath) {
        if ($sohvaLine -match '^(storePassword|keyPassword)=(.*)$') {
            $sohvaValue = $Matches[2].Trim()
            if ($sohvaValue.Length -ge 8 -and $sohvaText.Contains($sohvaValue)) {
                Add-SohvaViolation 'A local release-signing property value appears in the APK.'
            }
        }
    }
}
foreach ($sohvaVariable in Get-ChildItem Env:) {
    if ($sohvaVariable.Name -match '(?i)(token|secret|password|api[_-]?key)' -and
        $sohvaVariable.Value.Length -ge 12 -and $sohvaText.Contains($sohvaVariable.Value)) {
        Add-SohvaViolation "An environment secret value appears in the APK (variable: $($sohvaVariable.Name))."
    }
}

# AGP records a source revision. Permit only its placeholder path and a hex SHA;
# an expanded machine path or repository URL is not allowed.
$sohvaVersionMetadata = [regex]::Match($sohvaText, 'repositories \{\s*system: GIT\s*local_root_path: "([^"]+)"\s*revision: "([^"]+)"\s*\}')
if (-not $sohvaVersionMetadata.Success -or $sohvaVersionMetadata.Groups[1].Value -ne '$PROJECT_DIR' -or
    $sohvaVersionMetadata.Groups[2].Value -notmatch '^[0-9a-f]{40}$') {
    Add-SohvaViolation 'Version-control metadata contains unexpected source information.'
}

if ($sohvaViolations.Count -gt 0) {
    Write-Error ("Public APK safety audit failed:`n - " + ($sohvaViolations -join "`n - "))
    exit 1
}
Write-Output "Public APK safety audit passed: $($sohvaEntryNames.Count) entries and $sohvaScannedBytes uncompressed bytes inspected."
Write-Output "Permissions: $($sohvaPermissions -join ', ')"
Write-Output 'No scanned credential, machine-path, private-address pattern or compared local secret was found. This is not an exhaustive security audit.'
