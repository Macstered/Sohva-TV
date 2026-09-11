[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Path,

    [switch] $FailFast
)

$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path -LiteralPath $Path).Path

$allowedNames = @(
    '.gitignore',
    'CNAME',
    'FUNDING.yml',
    'LICENSE'
)
$allowedExtensions = @(
    '.apk',
    '.css',
    '.html',
    '.ico',
    '.jpeg',
    '.jpg',
    '.js',
    '.md',
    '.png',
    '.sha256',
    '.svg',
    '.txt',
    '.webp'
)
$textExtensions = @('.css', '.html', '.js', '.md', '.sha256', '.svg', '.txt')
$approvedHosts = @(
    'api-sports.io',
    'central.sonatype.com',
    'developer.android.com',
    'developer.themoviedb.org',
    'github.com',
    'fsf.org',
    'www.gnu.org',
    'macstered.github.io',
    'luontra.fi',
    'source.android.com',
    'www.apache.org',
    'www.luontra.fi',
    'www.themoviedb.org',
    'www.tvmaze.com'
)
$approvedEmails = @('hello@luontra.fi')

$violations = [System.Collections.Generic.List[string]]::new()
function Add-AuditViolation {
    param([Parameter(Mandatory = $true)][string] $Message)

    $violations.Add($Message)
    if ($FailFast) {
        throw 'Public release content audit rejected the tree.'
    }
}

$fileCount = 0
foreach ($file in (Get-ChildItem -LiteralPath $releaseRoot -Recurse -File -Force |
    Where-Object { $_.FullName -notmatch '[\\/]\.git[\\/]' })) {
    $fileCount++
    $relativePath = $file.FullName.Substring($releaseRoot.Length).TrimStart([char[]]'\/')
    $extension = $file.Extension.ToLowerInvariant()
    $nameAllowed = $allowedNames -contains $file.Name
    if (-not $nameAllowed -and $allowedExtensions -notcontains $extension) {
        Add-AuditViolation "${relativePath}: unapproved file type"
        continue
    }

    if ($file.Name -match '(?i)(credential|secret|password|keystore|backup|\.env)' -or
        $extension -in @('.jks', '.keystore', '.m3u', '.m3u8', '.xmltv')) {
        Add-AuditViolation "${relativePath}: prohibited sensitive file name or type"
        continue
    }

    if ($textExtensions -notcontains $extension -and -not $nameAllowed) {
        continue
    }

    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName) {
        $lineNumber++
        $location = "${relativePath}:$lineNumber"

        if ($line -match '(?i)\b(authorization|x-apisports-key)\s*:') {
            Add-AuditViolation "${location}: authorization header"
        }
        if ($line -match '(?i)\bbearer\s+[A-Za-z0-9._~+/-]{12,}=*') {
            Add-AuditViolation "${location}: bearer credential"
        }
        if ($line -match '(?i)https?://[^\s/:@]+:[^\s/@]+@') {
            Add-AuditViolation "${location}: URL contains embedded credentials"
        }
        if ($line -match '(?i)[?&](user(name)?|pass(word)?|token|api[_-]?key|key)=[^\s&#)]+') {
            Add-AuditViolation "${location}: URL contains a credential parameter"
        }
        if ($line -match '(?i)\b(10(?:\.\d{1,3}){3}|127(?:\.\d{1,3}){3}|169\.254(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2}|192\.168(?:\.\d{1,3}){2})\b') {
            Add-AuditViolation "${location}: private or local IP address"
        }
        if ($line -match '(?i)\b[\w.-]+\.(home\.arpa|local)\b') {
            Add-AuditViolation "${location}: private host name"
        }

        foreach ($urlMatch in [regex]::Matches($line, 'https?://[^\s)>\]"'']+')) {
            $uri = $null
            $candidate = $urlMatch.Value.TrimEnd('.', ',', ';')
            if (-not [Uri]::TryCreate($candidate, [UriKind]::Absolute, [ref] $uri) -or
                $approvedHosts -notcontains $uri.DnsSafeHost.ToLowerInvariant()) {
                Add-AuditViolation "${location}: URL host is not approved"
            }
        }
        foreach ($emailMatch in [regex]::Matches($line, '(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b')) {
            if ($approvedEmails -notcontains $emailMatch.Value.ToLowerInvariant()) {
                Add-AuditViolation "${location}: contact address is not approved"
            }
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Error ("Public release content audit failed:`n - " + ($violations -join "`n - "))
    exit 1
}

Write-Output "Public release content audit passed for $fileCount files."
