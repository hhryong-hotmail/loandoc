# GitHub 히스토리를 SQL 파일로 생성하는 스크립트
$outputFile = "D:\LoanDoc\insert_all_github_history.sql"
$repoName = "hhryong-hotmail/loandoc"

# UTF-8 인코딩 설정
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# SQL 파일 초기화
@"
-- GitHub 히스토리 데이터 삽입
-- 자동 생성된 파일
SET client_encoding TO 'UTF8';

"@ | Out-File -FilePath $outputFile -Encoding UTF8

# 최근 50개 커밋 가져오기 (UTF-8 인코딩 명시)
$commits = & git -c core.quotepath=false log --format="%H|%an|%ad|%s" --date=iso -50 --encoding=UTF-8

$insertCount = 0
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
        Write-Host "날짜 파싱 실패: $dateStr"
        continue
    }
    
    # 변경된 파일 목록 가져오기
    $files = git show --name-only --pretty=format: $hash 2>$null | Where-Object { $_ -ne '' -and $_ -notmatch '^$' } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
    $programName = ($files -join ', ')
    if ([string]::IsNullOrWhiteSpace($programName)) {
        $programName = "N/A"
    }
    
    # SQL 이스케이프 처리
    $messageEscaped = $message -replace "'", "''"
    $authorEscaped = $author -replace "'", "''"
    $programNameEscaped = $programName -replace "'", "''"
    
    # SQL INSERT 문 생성
    $sql = @"
INSERT INTO github_history (database_name, repo_name, change_datetime, program_name, change_reason, developer_name, important_code_content)
VALUES ('loandoc', '$repoName', '$pgDate', '$programNameEscaped', '$messageEscaped', '$authorEscaped', '$programNameEscaped')
ON CONFLICT DO NOTHING;

"@
    
    $sql | Out-File -FilePath $outputFile -Encoding UTF8 -Append
    $insertCount++
    Write-Host "Generated SQL for: $hash - $message"
}

Write-Host "`n총 $insertCount 개의 INSERT 문이 생성되었습니다: $outputFile"

