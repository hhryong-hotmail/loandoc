$logsDir = 'D:\apache-tomcat-9.0.98\logs'
$out = 'D:\LoanDoc\server\all_logs_search.txt'
if (-not (Test-Path $logsDir)) { Write-Host "Logs dir not found: $logsDir"; exit 0 }
Remove-Item -Path $out -ErrorAction SilentlyContinue
$patterns = @('Register','REGISTER','register','Database connection established','DB insert executed','DB unavailable','File fallback','Environment variables','User registration successful','Unexpected error','user registration successful','DB insert')
Get-ChildItem -Path $logsDir -File -Recurse | ForEach-Object {
    $file = $_.FullName
    foreach ($p in $patterns) {
        try {
            Select-String -Path $file -Pattern $p -SimpleMatch | ForEach-Object { "$($file):$($_.LineNumber): $($_.Line)" } | Out-File -FilePath $out -Append -Encoding utf8
        } catch {
            # ignore
        }
    }
}
if (Test-Path $out) { Write-Host "Wrote results to: $out" } else { Write-Host "No matches found" }
