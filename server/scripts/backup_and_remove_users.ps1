$p='D:\apache-tomcat-9.0.98\webapps\server\users.json'
if(Test-Path $p){
	$bak = $p + '.bak.' + (Get-Date -Format 'yyyyMMddHHmmss')
	Copy-Item -Path $p -Destination $bak -ErrorAction Stop
	Remove-Item -Path $p -ErrorAction Stop
	Write-Output "BACKED_UP:$bak"
} else {
	Write-Output "NOT_FOUND:$p"
}
