# GitHub 히스토리 한글 수정 스크립트
$env:PGPASSWORD = 'postgres'
$psql = "D:\PostgreSQL\17\bin\psql.exe"
$dbName = "loandoc"
$user = "postgres"

# UTF-8 인코딩 설정
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# 최근 커밋 가져오기 (UTF-8 명시)
$commits = & git -c core.quotepath=false log --format="%H|%an|%ad|%s" --date=iso -34 --encoding=UTF-8

foreach ($commit in $commits) {
    if ([string]::IsNullOrWhiteSpace($commit)) { continue }
    
    $parts = $commit -split '\|', 4
    if ($parts.Length -lt 4) { continue }
    
    $hash = $parts[0].Trim()
    $author = $parts[1].Trim()
    $dateStr = $parts[2].Trim()
    $message = $parts[3].Trim()
    
    # 날짜 파싱
    try {
        $dateObj = [DateTime]::Parse($dateStr)
        $pgDate = $dateObj.ToString("yyyy-MM-dd HH:mm:ss")
    } catch {
        continue
    }
    
    # 변경된 파일 목록 가져오기
    $files = & git -c core.quotepath=false show --name-only --pretty=format: $hash 2>$null | Where-Object { $_ -ne '' -and $_ -notmatch '^$' } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
    $programName = ($files -join ', ')
    if ([string]::IsNullOrWhiteSpace($programName)) {
        $programName = "N/A"
    }
    
    # SQL 이스케이프 처리
    $messageEscaped = $message -replace "'", "''"
    $authorEscaped = $author -replace "'", "''"
    $programNameEscaped = $programName -replace "'", "''"
    
    # UPDATE 문 실행 (해시와 날짜로 매칭)
    $sql = @"
UPDATE github_history 
SET program_name = '$programNameEscaped',
    change_reason = '$messageEscaped',
    developer_name = '$authorEscaped',
    important_code_content = '$programNameEscaped'
WHERE repo_name = 'hhryong-hotmail/loandoc' 
  AND change_datetime = '$pgDate'
  AND (change_reason LIKE '%$hash%' OR change_reason != '$messageEscaped');
"@
    
    Write-Host "Updating: $hash - $message"
    $output = & $psql -U $user -d $dbName -c "SET client_encoding TO 'UTF8'; $sql" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: $output"
    }
}

Write-Host "완료되었습니다."

