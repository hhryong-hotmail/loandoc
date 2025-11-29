try {
	$body = '{ "loginId":"tester","nationality":"China","remainMonths":12,"annualIncome":2000,"age":30,"workingMonths":12,"visaType":"E9","healthInsurance":"REGION" }'
	$resp = Invoke-RestMethod -Uri 'http://localhost:8080/server/api/server/loan-estimate' -Method Post -ContentType 'application/json' -Body $body -ErrorAction Stop
	$resp | ConvertTo-Json -Depth 10
} catch {
	Write-Host ('ERROR: ' + $_.Exception.Message)
	if ($_.Exception.Response -ne $null) {
		$stream = $_.Exception.Response.GetResponseStream()
		$reader = New-Object System.IO.StreamReader($stream)
		$bodyText = $reader.ReadToEnd()
		Write-Host 'Response body:'
		Write-Host $bodyText
	} else {
		Write-Host $_.Exception.ToString()
	}
	exit 1
}
