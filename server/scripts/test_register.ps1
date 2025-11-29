<#
Simple PowerShell test script to POST to /api/register for quick smoke tests.
Usage:
  # default: posts to http://127.0.0.1:8080/api/register
  .\test_register.ps1 -UserId testuser -Password P@ssw0rd!

Parameters:
  -Url: base URL of the server (default http://127.0.0.1:8080)
  -UserId: user id to register
  -Password: password
  -Verbose: show response body

This uses Invoke-RestMethod for convenience and prints status code and response.
#>

param(
	[string]$Url = 'http://127.0.0.1:8080',
	[string]$UserId = 'testuser',
	[string]$Password = 'P@ssw0rd!',
	[switch]$VerboseResponse
)

$endpoint = "$Url/api/register"
$body = @{ id = $UserId; password = $Password } | ConvertTo-Json
Write-Output "POST $endpoint -> payload: $body"

try{
	$resp = Invoke-RestMethod -Uri $endpoint -Method Post -Body $body -ContentType 'application/json' -ErrorAction Stop
	Write-Output "Response (parsed):"; $resp | ConvertTo-Json -Depth 5 | Write-Output
} catch [System.Net.WebException] {
	$we = $_.Exception
	if($we.Response -ne $null){
		$reader = New-Object System.IO.StreamReader($we.Response.GetResponseStream())
		$text = $reader.ReadToEnd(); $reader.Close()
		Write-Output "HTTP error response body:"; Write-Output $text
	} else {
		Write-Output "Request failed: $($_.Exception.Message)"
	}
} catch {
	Write-Output "Unexpected error: $($_.Exception.Message)"
}
