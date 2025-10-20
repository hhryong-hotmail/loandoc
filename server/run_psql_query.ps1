<#
Run a SQL file with psql. Avoid embedding passwords in scripts.
You can provide the password via environment variable PGPASSWORD before running
or run this script and enter the password interactively when prompted.
#>
# If PGPASSWORD is already set in the environment, we'll use it. Otherwise prompt.
if (-not $env:PGPASSWORD -or $env:PGPASSWORD -eq '') {
	Write-Host "PGPASSWORD not set in environment. You will be prompted for the DB password (input hidden)."
	$securePwd = Read-Host -AsSecureString "Postgres password"
	try {
		$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePwd)
		$plainPwd = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
		$env:PGPASSWORD = $plainPwd
		[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
	} catch {
		Write-Error "Failed to read password: $_"
		exit 1
	}
} else {
	Write-Host "Using PGPASSWORD from environment."
}

# Use psql from PATH; if not found, set full path to psql.exe here
$psqlCmd = 'psql'
$script = 'D:\LoanDoc\server\tmp_query.sql'
Write-Host "Running psql -f $script as user postgres"
& $psqlCmd -U postgres -d loandoc -f $script -A -F " | "
