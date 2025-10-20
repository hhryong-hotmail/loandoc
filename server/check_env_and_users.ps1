# Check env vars and users.json
Write-Output "ENABLE_FILE_FALLBACK=$($env:ENABLE_FILE_FALLBACK)"
Write-Output "DB_URL=$($env:DB_URL)"
Write-Output "DB_USER=$($env:DB_USER)"
$path = 'D:\apache-tomcat-9.0.98\webapps\server\users.json'
if (Test-Path $path) {
    Write-Output "FOUND:$path"
    $found = Select-String -Path $path -Pattern 'testuser' -SimpleMatch -Quiet
    if ($found) { Write-Output 'MATCH:testuser present in users.json' } else { Write-Output 'NO_MATCH:testuser not present' }
    Write-Output '---FILE_HEAD---'
    try {
        $c = Get-Content -Path $path -Raw
        if ($c.Length -gt 200) { Write-Output $c.Substring(0,200) } else { Write-Output $c }
    } catch {
        Write-Output "Cannot read file: $_"
    }
} else {
    Write-Output "NOT_FOUND:$path"
}
