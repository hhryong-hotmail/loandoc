<#
.SYNOPSIS
 Quickly copy a local index.html into the Tomcat webapp directory for fast development checks.

.DESCRIPTION
 Copies the provided source HTML file to the webapp index location. Optionally restarts Tomcat.

.PARAMETER Source
 Path to local index.html (default: D:\LoanDoc\index.html)

.PARAMETER Webapp
 Destination path in Tomcat webapps (default: D:\apache-tomcat-9.0.98\webapps\server\index.html)

.PARAMETER Restart
 If provided, restarts Tomcat after copying (uses bin\shutdown.bat and bin\startup.bat).

.EXAMPLE
 .\quick_deploy_index.ps1 -Source 'D:\LoanDoc\index.html' -Restart
#>

param(
    [string]$Source = 'D:\LoanDoc\index.html',
    [string]$Webapp = 'D:\apache-tomcat-9.0.98\webapps\server\index.html',
    [switch]$Restart
)

function Write-Info($msg){ Write-Host "[info] $msg" }
function Write-Err($msg){ Write-Host "[error] $msg" -ForegroundColor Red }

Write-Info "Quick deploy: Source='$Source' -> Webapp='$Webapp'"

if (-not (Test-Path $Source)){
    Write-Err "Source file not found: $Source"
    exit 2
}

try{
    $destDir = Split-Path -Parent $Webapp
    if (-not (Test-Path $destDir)){
        Write-Info "Creating destination directory: $destDir"
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }

    Copy-Item -Path $Source -Destination $Webapp -Force
    Write-Info "Copied file to webapp location"

    if ($Restart) {
        $catalinaBase = 'D:\apache-tomcat-9.0.98'
        $shutdown = Join-Path $catalinaBase 'bin\shutdown.bat'
        $startup  = Join-Path $catalinaBase 'bin\startup.bat'
        Write-Info "Restarting Tomcat (shutdown -> startup)"
        if (Test-Path $shutdown) { & $shutdown } else { Write-Err "shutdown.bat not found: $shutdown" }
        Start-Sleep -Seconds 2
        if (Test-Path $startup) { & $startup } else { Write-Err "startup.bat not found: $startup" }
        Write-Info "Tomcat restart request sent"
    }

    Write-Info "Done. Open http://127.0.0.1:8080/server in your browser and Ctrl+F5 to bypass cache."
    exit 0
} catch {
    Write-Err "Failed to copy file: $_"
    exit 1
}
