$tests = @(
    @{ name = 'E9-China'; body = '{"loginId":"tester","nationality":"China","remainMonths":12,"annualIncome":2000,"age":30,"workingMonths":12,"visaType":"E9","healthInsurance":"REGION"}' },
    @{ name = 'E9-India'; body = '{"loginId":"tester","nationality":"India","remainMonths":12,"annualIncome":2000,"age":30,"workingMonths":12,"visaType":"E9","healthInsurance":"REGION"}' },
    @{ name = 'F4-China'; body = '{"loginId":"tester","nationality":"China","remainMonths":12,"annualIncome":2000,"age":30,"workingMonths":12,"visaType":"F4","healthInsurance":"REGION"}' },
    @{ name = 'F4-Uzbekistan'; body = '{"loginId":"tester","nationality":"Uzbekistan","remainMonths":12,"annualIncome":2000,"age":30,"workingMonths":12,"visaType":"F4","healthInsurance":"REGION"}' }
)

foreach ($t in $tests) {
    Write-Host "========================"
    Write-Host "Test: $($t.name)"
    try {
        $resp = Invoke-RestMethod -Uri 'http://localhost:8080/server/api/server/loan-estimate' -Method Post -ContentType 'application/json' -Body $t.body -ErrorAction Stop
        $json = $resp | ConvertTo-Json -Depth 10
        Write-Host $json
    } catch {
        Write-Host "ERROR: $($_.Exception.Message)"
        if ($_.Exception.Response -ne $null) {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $bodyText = $reader.ReadToEnd()
            Write-Host 'Response body:'
            Write-Host $bodyText
        } else {
            Write-Host $_.Exception.ToString()
        }
    }
}
