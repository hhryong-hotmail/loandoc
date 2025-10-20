<#
PowerShell helper to add/remove environment variables and install/uninstall Tomcat as a Windows service.
Requires Administrator privileges for system environment changes and service install/remove.

Usage examples:
  # Set system environment variables (requires admin)
  .\tomcat_env_and_service.ps1 -TomcatHome 'D:\apache-tomcat-9.0.98' -Action set-env

  # Remove system env vars (requires admin)
  .\tomcat_env_and_service.ps1 -TomcatHome 'D:\apache-tomcat-9.0.98' -Action remove-env

  # Install Tomcat service (requires admin)
  .\tomcat_env_and_service.ps1 -TomcatHome 'D:\apache-tomcat-9.0.98' -Action install-service -ServiceName 'Tomcat9'

  # Uninstall Tomcat service (requires admin)
  .\tomcat_env_and_service.ps1 -TomcatHome 'D:\apache-tomcat-9.0.98' -Action uninstall-service -ServiceName 'Tomcat9'
#>
[CmdletBinding()]
param(
    [string]$TomcatHome = 'D:\apache-tomcat-9.0.98',
    [ValidateSet('set-env','remove-env','install-service','uninstall-service','restart-service')]
    [string]$Action = 'set-env',
    [string]$ServiceName = 'Tomcat9'
)

function Require-Admin {
    if(-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){
        Write-Error "This action requires Administrator privileges. Run PowerShell as Administrator."
        exit 1
    }
}

switch($Action){
    'set-env' {
        Require-Admin
        Write-Host "Setting system environment variables CATALINA_HOME and CATALINA_BASE to $TomcatHome"
        [Environment]::SetEnvironmentVariable('CATALINA_HOME', $TomcatHome, [EnvironmentVariableTarget]::Machine)
        [Environment]::SetEnvironmentVariable('CATALINA_BASE', $TomcatHome, [EnvironmentVariableTarget]::Machine)
        Write-Host "Environment variables set. You may need to reopen shells to pick them up."
    }
    'remove-env' {
        Require-Admin
        Write-Host "Removing system environment variables CATALINA_HOME and CATALINA_BASE"
        [Environment]::SetEnvironmentVariable('CATALINA_HOME', $null, [EnvironmentVariableTarget]::Machine)
        [Environment]::SetEnvironmentVariable('CATALINA_BASE', $null, [EnvironmentVariableTarget]::Machine)
        Write-Host "Removed."
    }
    'install-service' {
        Require-Admin
        $serviceBat = Join-Path -Path $TomcatHome -ChildPath 'bin\service.bat'
        if(-not (Test-Path $serviceBat)){
            Write-Error "service.bat not found at $serviceBat. Tomcat distribution may not include Windows service wrapper."
            exit 1
        }
        Write-Host "Installing Windows service named $ServiceName using service.bat"
        Push-Location $TomcatHome\bin
        & .\service.bat install $ServiceName
        Pop-Location
        Write-Host "Service install command executed. Check Windows Services MMC or run: sc query $ServiceName"
    }
    'uninstall-service' {
        Require-Admin
        $serviceBat = Join-Path -Path $TomcatHome -ChildPath 'bin\service.bat'
        if(-not (Test-Path $serviceBat)){
            Write-Error "service.bat not found at $serviceBat."
            exit 1
        }
        Write-Host "Uninstalling Windows service named $ServiceName"
        Push-Location $TomcatHome\bin
        & .\service.bat remove $ServiceName
        Pop-Location
        Write-Host "Service uninstall command executed."
    }
    'restart-service' {
        Require-Admin
        Write-Host "Restarting service $ServiceName"
        Restart-Service -Name $ServiceName -Force -ErrorAction Stop
        Write-Host "Service restarted."
    }
}
