$latest = Get-ChildItem 'D:\apache-tomcat-9.0.98\logs' -Filter 'catalina.*.log' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $latest) { Write-Host "No catalina logs found"; exit 0 }
$out = 'D:\LoanDoc\server\e2e_live_test_logs.txt'
Remove-Item -Path $out -ErrorAction SilentlyContinue
Write-Host "Checking log: $($latest.FullName)"
Get-Content -Path $latest.FullName -Tail 4000 | Select-String -Pattern 'e2e_live_test|Register|DB insert executed|User registration successful|File fallback|DB unavailable|SQLException|SQL State|Duplicate key' -SimpleMatch | ForEach-Object { Add-Content -Path $out -Value $_.Line }
Write-Host "Wrote results to: $out"
Get-Content -Path $out -Tail 200 | ForEach-Object { Write-Host $_ }
