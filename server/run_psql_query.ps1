# Run a SQL file with psql using PGPASSWORD to avoid interactive prompt
$env:PGPASSWORD = 'postgres'
# Use psql from PATH; if not found, set full path to psql.exe here
$psqlCmd = 'psql'
$script = 'D:\LoanDoc\server\tmp_query.sql'
Write-Host "Running psql -f $script as user postgres"
& $psqlCmd -U postgres -d loandoc -f $script -A -F " | "
