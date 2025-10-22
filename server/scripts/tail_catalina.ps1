$log = 'D:\apache-tomcat-9.0.98\logs\catalina.2025-10-21.log'
if (-not (Test-Path $log)) { Write-Host "Log not found: $log"; exit 0 }
Write-Host "--- Tail of $log ---"
Get-Content -Path $log -Tail 400 | ForEach-Object { Write-Host $_ }
