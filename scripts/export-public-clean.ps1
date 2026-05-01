param(
    [string]$OutputRoot,
    [string]$ZipPath,
    [switch]$NoZip
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$rootPath = $root.Path.TrimEnd("\")

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path (Split-Path $rootPath -Parent) "DeuteriumAPP-public"
}

$outputFullPath = [System.IO.Path]::GetFullPath($OutputRoot).TrimEnd("\")
if ($outputFullPath.Equals($rootPath, [System.StringComparison]::OrdinalIgnoreCase) -or
    $outputFullPath.StartsWith("$rootPath\", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputRoot must be outside the private workspace: $rootPath"
}

function Remove-DirectorySafely([string]$Path) {
    if (-not (Test-Path $Path)) {
        return
    }
    $resolved = (Resolve-Path $Path).Path.TrimEnd("\")
    if ($resolved.Length -lt 10) {
        throw "Refusing to remove suspiciously short path: $resolved"
    }
    if ($resolved.Equals($rootPath, [System.StringComparison]::OrdinalIgnoreCase) -or
        $resolved.StartsWith("$rootPath\", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove anything inside private workspace: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

function Test-ExcludedDirectory([System.IO.DirectoryInfo]$Directory) {
    if ($Directory.Name.StartsWith(".gradle", [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    $excluded = @(
        ".git",
        ".android",
        ".idea",
        ".tools",
        "build",
        "delivery",
        "dist",
        "local-maven",
        "local-resources"
    )
    return $excluded -contains $Directory.Name
}

function Test-ExcludedFile([System.IO.FileInfo]$File) {
    if ($File.FullName -match "\\docs\\(production-delivery|backend-plugin-maintenance)\.md$") {
        return $true
    }
    if ($File.Name -in @("local.properties", "application.conf")) {
        return $true
    }
    if ($File.Extension -in @(".apk", ".aab", ".zip", ".keystore", ".jks")) {
        return $true
    }
    if ($File.Extension -eq ".jar" -and $File.FullName -notmatch "\\gradle\\wrapper\\gradle-wrapper\.jar$") {
        return $true
    }
    return $false
}

function Get-PrivateSecretValues {
    $values = New-Object System.Collections.Generic.List[string]
    $configKeys = "database.password", "security.sessionTokenPepper", "security.verificationPepper", "pluginBridge.token"
    function Add-SecretValue([string]$Value) {
        $clean = $Value.Trim().Trim('"')
        if ([string]::IsNullOrWhiteSpace($clean)) {
            return
        }
        if ($clean -match "^CHANGE_ME") {
            return
        }
        if ($clean.Length -lt 6 -or $clean -match "^[.\-*]+$") {
            return
        }
        $values.Add($clean)
    }

    $secretRoots = @(
        (Join-Path $rootPath "delivery"),
        (Join-Path $rootPath "dist"),
        (Join-Path $rootPath "minecraft-plugin\src\main\local-resources")
    ) | Where-Object { Test-Path $_ }

    $secretRoots |
        ForEach-Object { Get-ChildItem -LiteralPath $_ -Recurse -File -Include "application.conf", "config.yml" } |
        Where-Object { $_.FullName -notmatch "\\jre\\" -and $_.FullName -notmatch "\\lib\\" } |
        ForEach-Object {
            $content = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
            foreach ($key in $configKeys) {
                $escaped = [Regex]::Escape($key)
                $match = [Regex]::Match($content, "(?m)^$escaped=(.+)$")
                if ($match.Success) {
                    Add-SecretValue $match.Groups[1].Value
                }
            }
            foreach ($match in [Regex]::Matches($content, '(?m)^\s*token:\s*"([^"]+)"')) {
                Add-SecretValue $match.Groups[1].Value
            }
        }

    return $values | Sort-Object -Unique
}

$privateSecretValues = Get-PrivateSecretValues

function Copy-DirectoryFiltered([string]$Source, [string]$Target) {
    New-Item -ItemType Directory -Force $Target | Out-Null
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        if ($_.PSIsContainer) {
            if (-not (Test-ExcludedDirectory $_)) {
                Copy-DirectoryFiltered $_.FullName (Join-Path $Target $_.Name)
            }
        } else {
            if (-not (Test-ExcludedFile $_)) {
                Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $Target $_.Name) -Force
            }
        }
    }
}

function Update-TextFile([string]$Path) {
    $extensions = @(".bat", ".cmd", ".conf", ".gradle", ".java", ".kt", ".kts", ".md", ".properties", ".ps1", ".txt", ".xml", ".yaml", ".yml")
    $extension = [System.IO.Path]::GetExtension($Path)
    if ($extensions -notcontains $extension) {
        return
    }

    $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    $content = $content -replace "deuterium\.s\.odn\.cc", "example.com"
    foreach ($secret in $privateSecretValues) {
        $content = $content -replace [Regex]::Escape($secret), "CHANGE_ME"
    }
    $content = $content -replace "(?m)^database\.user=.*$", "database.user=deuterium_app"
    $content = $content -replace "(?m)^database\.password=.*$", "database.password=CHANGE_ME"
    $content = $content -replace "(?m)^security\.sessionTokenPepper=.*$", "security.sessionTokenPepper=CHANGE_ME_TO_LONG_RANDOM_VALUE"
    $content = $content -replace "(?m)^security\.verificationPepper=.*$", "security.verificationPepper=CHANGE_ME_TO_ANOTHER_LONG_RANDOM_VALUE"
    $content = $content -replace "(?m)^pluginBridge\.token=.*$", "pluginBridge.token=CHANGE_ME_TO_LONG_RANDOM_VALUE"
    $content = $content -replace "(?m)^(\s*token:\s*)""[^""]*""", '$1"CHANGE_ME"'

    Set-Content -LiteralPath $Path -Value $content -Encoding UTF8
}

Remove-DirectorySafely $outputFullPath
New-Item -ItemType Directory -Force $outputFullPath | Out-Null

$include = @(
    ".gitignore",
    "AGENTS.md",
    "CONTEXT.md",
    "README.md",
    "android-app",
    "backend-api",
    "docs",
    "minecraft-plugin",
    "scripts"
)

foreach ($item in $include) {
    $source = Join-Path $rootPath $item
    if (-not (Test-Path $source)) {
        continue
    }
    $target = Join-Path $outputFullPath $item
    $sourceItem = Get-Item -LiteralPath $source -Force
    if ($sourceItem.PSIsContainer) {
        Copy-DirectoryFiltered $sourceItem.FullName $target
    } elseif (-not (Test-ExcludedFile $sourceItem)) {
        Copy-Item -LiteralPath $sourceItem.FullName -Destination $target -Force
    }
}

Get-ChildItem -LiteralPath $outputFullPath -Recurse -File | ForEach-Object {
    Update-TextFile $_.FullName
}

$secretPatterns = @("deuterium\.s\.odn\.cc")
foreach ($secret in $privateSecretValues) {
    if (-not [string]::IsNullOrWhiteSpace($secret)) {
        $secretPatterns += [Regex]::Escape($secret)
    }
}
$matches = Get-ChildItem -LiteralPath $outputFullPath -Recurse -File |
    Where-Object { $_.FullName -notmatch "\\.git\\" } |
    Select-String -Pattern ($secretPatterns -join "|") -CaseSensitive:$false

if ($matches) {
    $preview = $matches | Select-Object -First 10 | ForEach-Object { "$($_.Path):$($_.LineNumber): $($_.Line)" }
    throw "Public export still contains private markers:`n$($preview -join "`n")"
}

if (-not $NoZip) {
    if ([string]::IsNullOrWhiteSpace($ZipPath)) {
        $ZipPath = Join-Path $env:USERPROFILE "$([System.IO.Path]::GetFileName($outputFullPath)).zip"
    }
    $zipFullPath = [System.IO.Path]::GetFullPath($ZipPath)
    if ($zipFullPath.StartsWith("$outputFullPath\", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "ZipPath must not be inside OutputRoot."
    }
    if (Test-Path $zipFullPath) {
        Remove-Item -LiteralPath $zipFullPath -Force
    }
    Compress-Archive -Path $outputFullPath -DestinationPath $zipFullPath -CompressionLevel Optimal
    Write-Host "Public export generated at: $outputFullPath"
    Write-Host "Public export zip generated at: $zipFullPath"
} else {
    Write-Host "Public export generated at: $outputFullPath"
}

