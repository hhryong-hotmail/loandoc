# Auto-init, commit and push local repo to GitHub
# Run from any location; script will operate on D:\LoanDoc
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoDir = 'D:\LoanDoc'
$repoName = 'loandoc'
$githubOwner = 'hhryong-hotmail'
$public = $true

Write-Host "Working in: $repoDir"
Set-Location $repoDir

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
	Write-Error "git not found in PATH. Install Git and rerun."
	exit 1
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
	Write-Error "gh (GitHub CLI) not found in PATH. Install gh and login (gh auth login) and rerun."
	exit 1
}

if (-not (Test-Path (Join-Path $repoDir '.git'))) {
	Write-Host "Initializing git repository..."
	git init | Write-Host
} else {
	Write-Host "Git repository already initialized."
}

# Create .gitignore if missing
$gitignorePath = Join-Path $repoDir '.gitignore'
if (-not (Test-Path $gitignorePath)) {
	Write-Host "Creating .gitignore..."
	@'
# Maven
/target/
/.mvn/

# IDE
.vscode/
*.iml
.idea/

# OS
Thumbs.db
.DS_Store

# Tomcat
/webapps/
*.war

# Secrets
*.env
*.keystore
setenv.bat
'@ | Out-File -FilePath $gitignorePath -Encoding utf8
} else {
	Write-Host ".gitignore already exists; leaving it unchanged."
}

# Stage and commit
Write-Host "Staging files..."
git add --all

$commitMessage = 'Initial commit: loandoc project'
$commitOk = $false
try {
	Write-Host "Creating commit..."
	git commit -m "$commitMessage" | Write-Host
	$commitOk = $true
} catch {
	Write-Host "No commit created (possibly nothing to commit): $($_.Exception.Message)"
}

# Ensure main branch name
Write-Host "Setting branch name to main..."
try { git branch -M main } catch { Write-Host "branch -M may have failed or already set: $($_.Exception.Message)" }

# Create remote repository on GitHub and push
$fullName = "$githubOwner/$repoName"
Write-Host "Creating GitHub repository $fullName (if it doesn't exist) and pushing..."
$createArgs = @($fullName)
if ($public) { $createArgs += '--public' } else { $createArgs += '--private' }
$createArgs += '--source=.'
$createArgs += '--remote=origin'
$createArgs += '--push'
$createArgs += '--confirm'

try {
	gh repo create @createArgs | Write-Host
	Write-Host "Repository created/updated and pushed to origin successfully."
} catch {
	Write-Error "gh repo create failed: $($_.Exception.Message)"
	Write-Host "Attempting to add remote and push manually..."
	$originUrl = "https://github.com/$fullName.git"
	try {
		git remote remove origin 2>$null
	} catch { }
	git remote add origin $originUrl
	git push -u origin main
}

Write-Host "Done. Visit: https://github.com/$fullName"
