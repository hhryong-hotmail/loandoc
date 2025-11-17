$json = @'
{"loginId":"smoke","nationality":"china","remainMonths":6,"annualIncome":2000,"age":30,"workingMonths":12,"visaType":"E-9","healthInsurance":"지역"}
'@

Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/server/api/server/loan-estimate' -ContentType 'application/json' -Body $json | ConvertTo-Json -Depth 6
