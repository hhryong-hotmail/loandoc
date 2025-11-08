<#
Check Tomcat shutdown port (8005) and related java processes.
Usage:
  .\tomcat_check_status.ps1 -TomcatHome 'D:\apache-tomcat-9.0.98'
#>
[CmdletBinding()]
param(
    [string]$TomcatHome = 'D:\apache-tomcat-9.0.98'
)

Write-Host "Checking port 8005 (Tomcat shutdown port)..."
$net = netstat -ano | findstr ":8005" 2>$null
if($net -eq $null -or $net -eq ''){
    Write-Host "No process is listening on port 8005. (shutdown port is not active)"
} else {
    Write-Host "Port 8005 usage:"
    $net
    # parse PID
    $parts = $net -split '\s+' | Where-Object { $_ -ne '' }
    $pid = $parts[-1]
    Write-Host "PID using 8005: $pid"
    Write-Host "Process info:"
    tasklist /FI "PID eq $pid"
    Write-Host "CommandLine (if available):"
    try{ wmic process where processid=$pid get CommandLine } catch { Write-Host "wmic not available or failed" }
}

Write-Host "\nChecking java processes (possible Tomcat instances):"
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "PID: $($_.Id)  CPU: $($_.CPU)  Handles: $($_.Handles)"
}

Write-Host "\nYou can also check Tomcat logs at: $TomcatHome\logs\"
