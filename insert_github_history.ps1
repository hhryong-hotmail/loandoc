# GitHub 히스토리를 데이터베이스에 입력하는 스크립트
$env:PGPASSWORD = 'postgres'
$psql = "D:\PostgreSQL\17\bin\psql.exe"
$dbName = "loandoc"
$user = "postgres"
$repoName = "hhryong-hotmail/loandoc"

# 최근 20개 커밋 가져오기
$commits = git log --format="%H|%an|%ad|%s" --date=iso -20

foreach ($commit in $commits) {
    if ([string]::IsNullOrWhiteSpace($commit)) { continue }
    
    $parts = $commit -split '\|', 4
    if ($parts.Length -lt 4) { continue }
    
    $hash = $parts[0].Trim()
    $author = $parts[1].Trim()
    $dateStr = $parts[2].Trim()
    $message = $parts[3].Trim()
    
    # 날짜 파싱 (ISO 형식에서 PostgreSQL TIMESTAMP로 변환)
    try {
        $dateObj = [DateTime]::Parse($dateStr)
        $pgDate = $dateObj.ToString("yyyy-MM-dd HH:mm:ss")
    } catch {
        Write-Host "날짜 파싱 실패: $dateStr"
        continue
    }
    
    # 변경된 파일 목록 가져오기
    $files = git show --name-only --pretty=format: $hash 2>$null | Where-Object { $_ -ne '' -and $_ -notmatch '^$' } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
    $programName = ($files -join ', ')
    if ([string]::IsNullOrWhiteSpace($programName)) {
        $programName = "N/A"
    }
    
    # 중요 코드 내용 (간단히 파일명만)
    $importantCode = $programName
    
    # SQL 이스케이프 처리
    $messageEscaped = $message -replace "'", "''"
    $authorEscaped = $author -replace "'", "''"
    $programNameEscaped = $programName -replace "'", "''"
    $importantCodeEscaped = $importantCode -replace "'", "''"
    
    # program_name이 너무 길면 자르기 (200자로 제한하되, 파일명이 많으면 요약)
    if ($programName.Length -gt 200) {
        $filesArray = $files | Select-Object -First 5
        $programName = ($filesArray -join ', ')
        if ($files.Count -gt 5) {
            $programName += " 외 " + ($files.Count - 5) + "개 파일"
        }
    }
    $programNameEscaped = $programName -replace "'", "''"
    
    # 중요 코드 내용도 동일하게 처리
    $importantCode = $programName
    $importantCodeEscaped = $importantCode -replace "'", "''"
    
    # SQL 실행 (중복 체크를 위해 hash 기반으로 확인)
    $checkSql = "SELECT COUNT(*) FROM github_history WHERE repo_name = '$repoName' AND change_datetime = '$pgDate';"
    $result = & $psql -U $user -d $dbName -t -A -c $checkSql 2>&1
    $resultClean = ($result -replace '[^\d]', '').Trim()
    $count = 0
    if ([int]::TryParse($resultClean, [ref]$count)) {
        # 파싱 성공
    }
    
    if ($count -eq 0) {
        $sql = @"
INSERT INTO github_history (database_name, repo_name, change_datetime, program_name, change_reason, developer_name, important_code_content)
VALUES ('loandoc', '$repoName', '$pgDate', '$programNameEscaped', '$messageEscaped', '$authorEscaped', '$importantCodeEscaped');
"@
        
        Write-Host "Inserting: $hash - $message"
        $output = & $psql -U $user -d $dbName -c $sql 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Error inserting: $output"
        }
    } else {
        Write-Host "Skipping duplicate: $hash - $message"
    }
}

Write-Host "완료되었습니다."

