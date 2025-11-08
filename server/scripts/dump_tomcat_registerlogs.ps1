$logsDir = 'D:\apache-tomcat-9.0.98\logs'
if (-not (Test-Path $logsDir)) {
    Write-Host "Logs directory not found: $logsDir"
    exit 0
}
$latest = Get-ChildItem -Path $logsDir -Filter 'catalina.*.log' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $latest) {
    Write-Host "No catalina logs found in $logsDir"
    exit 0
}
Write-Host "Checking log: $($latest.FullName)"
Get-Content -Path $latest.FullName -Tail 2000 | Select-String -Pattern 'Register|REGISTER|Database connection established|DB insert executed|DB unavailable|File fallback|Environment variables|User registration successful|Unexpected error' -SimpleMatch | ForEach-Object { Write-Host $_.Line }
