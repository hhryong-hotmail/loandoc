# PowerShell script to update all user passwords
# Connects to PostgreSQL and updates passwords to lowercase version

$env:PGPASSWORD = "postgresql"
$dbHost = "localhost"
$dbPort = "5432"
$dbName = "loandoc"
$dbUser = "postgres"

# New password (lowercase): dptn1!1234
# We need to generate bcrypt hash for this password
# Bcrypt hash of "dptn1!1234" with cost 12

# This hash was pre-generated using bcrypt with cost 12 for "dptn1!1234"
# You'll need to generate this using a bcrypt tool or Java code
$newPasswordHash = '$2a$12$' + 'PLACEHOLDER'

Write-Host "Connecting to PostgreSQL database: $dbName"

# Get list of users first
$getUsersQuery = "SELECT user_id FROM user_account;"
psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -c $getUsersQuery

Write-Host "`nNote: To update passwords, you need to:"
Write-Host "1. Generate bcrypt hash for 'dptn1!1234'"
Write-Host "2. Run UPDATE query with the hash"
Write-Host "`nAlternatively, users can re-register with the new password format."

Remove-Item Env:\PGPASSWORD
