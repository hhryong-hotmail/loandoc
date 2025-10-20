$src = 'd:\LoanDoc\server\target\server.war'
$dst = 'D:\apache-tomcat-9.0.98\webapps\server.war'
if (Test-Path $dst) {
    $bak = $dst + '.bak.' + (Get-Date -Format 'yyyyMMddHHmmss')
    Copy-Item -Path $dst -Destination $bak -ErrorAction Stop
    Write-Output "BACKED_UP:$bak"
}
Copy-Item -Path $src -Destination $dst -Force -ErrorAction Stop
Write-Output "DEPLOYED:$dst"