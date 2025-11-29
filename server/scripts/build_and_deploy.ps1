<#
.SYNOPSIS
 Build the war with Maven and deploy to Tomcat (uses deploy_war.ps1)

.PARAMETER SkipTests
 If provided, skips tests during mvn package (default: true)

.PARAMETER Restart
 If provided, restarts Tomcat after deploy

.EXAMPLE
 .\build_and_deploy.ps1 -SkipTests -Restart
#>

param(
    [switch]$SkipTests = $true,
    [switch]$Restart
)

function Write-Info($msg){ Write-Host "[info] $msg" }
function Write-Err($msg){ Write-Host "[error] $msg" -ForegroundColor Red }

# Ensure script executes from scripts directory so relative operations (deploy_war.ps1) work reliably
Set-Location 'D:\LoanDoc\server\scripts'
# Move one level up to server for build/deploy operations
Set-Location '..\'

# Build args as an array so PowerShell invokes mvn with separate arguments
$mvnArgs = @('-B','clean','package')
if ($SkipTests) { $mvnArgs += '-DskipTests' }

Write-Info "Running: mvn $($mvnArgs -join ' ')"
try {
    & mvn @($mvnArgs)
    if ($LASTEXITCODE -ne 0) { throw "mvn failed with code $LASTEXITCODE" }
} catch {
    Write-Err "Maven build failed: $_"
    exit 1
}

if (-not (Test-Path '.\target\server.war')){
    Write-Err "WAR not found in target\server.war"
    exit 2
}

Write-Info "Deploying WAR using deploy_war.ps1"
try{
    & .\scripts\deploy_war.ps1
} catch {
    Write-Err "deploy_war.ps1 failed: $_"
    exit 3
}

if ($Restart) {
    $catalinaBase = 'D:\apache-tomcat-9.0.98'
    $shutdown = Join-Path $catalinaBase 'bin\shutdown.bat'
    $startup  = Join-Path $catalinaBase 'bin\startup.bat'
    Write-Info "Restarting Tomcat (shutdown -> startup)"
    
    # Set environment variables for Tomcat scripts
    $env:CATALINA_HOME = $catalinaBase
    $env:CATALINA_BASE = $catalinaBase
    
    # Change to bin directory before running shutdown/startup
    Push-Location (Join-Path $catalinaBase 'bin')
    try {
        if (Test-Path $shutdown) { 
            & $shutdown 
        } else { 
            Write-Err "shutdown.bat not found: $shutdown" 
        }
        Start-Sleep -Seconds 3
        if (Test-Path $startup) { 
            & $startup 
        } else { 
            Write-Err "startup.bat not found: $startup" 
        }
    } finally {
        Pop-Location
    }
    Write-Info "Tomcat restart requested"
}

Write-Info "Build and deploy completed. Open http://172.30.1.84:8080/server in browser and Ctrl+F5."
exit 0
