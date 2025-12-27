# GitHub 히스토리 한글 수정 스크립트 (v3 - 커밋 해시로 직접 가져오기)
$env:PGPASSWORD = 'postgres'
$psql = "D:\PostgreSQL\17\bin\psql.exe"
$dbName = "loandoc"
$user = "postgres"

# UTF-8 인코딩 설정
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# 커밋 해시 목록 가져오기
$hashes = & git log --format="%H" -34

$updateSql = "SET client_encoding TO 'UTF8';`n`n"

foreach ($hash in $hashes) {
    if ([string]::IsNullOrWhiteSpace($hash)) { continue }
    
    $hash = $hash.Trim()
    
    # 커밋 정보 가져오기
    $commitInfo = & git log -1 --format="%H|%an|%ad|%s" --date=iso $hash
    if ([string]::IsNullOrWhiteSpace($commitInfo)) { continue }
    
    $parts = $commitInfo -split '\|', 4
    if ($parts.Length -lt 4) { continue }
    
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
    $files = & git show --name-only --pretty=format: $hash 2>$null | Where-Object { $_ -ne '' -and $_ -notmatch '^$' } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
    $programName = ($files -join ', ')
    if ([string]::IsNullOrWhiteSpace($programName)) {
        $programName = "N/A"
    }
    
    # SQL 이스케이프 처리 (PostgreSQL의 E'' 문법 사용)
    $messageEscaped = $message -replace "\\", "\\\\" -replace "'", "''"
    $authorEscaped = $author -replace "\\", "\\\\" -replace "'", "''"
    $programNameEscaped = $programName -replace "\\", "\\\\" -replace "'", "''"
    
    # UPDATE 문 생성
    $updateSql += "UPDATE github_history `n"
    $updateSql += "SET program_name = E'$programNameEscaped',`n"
    $updateSql += "    change_reason = E'$messageEscaped',`n"
    $updateSql += "    developer_name = E'$authorEscaped',`n"
    $updateSql += "    important_code_content = E'$programNameEscaped'`n"
    $updateSql += "WHERE repo_name = 'hhryong-hotmail/loandoc' `n"
    $updateSql += "  AND change_datetime = '$pgDate';`n`n"
    
    Write-Host "Prepared update for: $hash - $message"
}

# SQL 파일로 저장
$sqlFile = "D:\LoanDoc\update_github_history.sql"
$updateSql | Out-File -FilePath $sqlFile -Encoding UTF8

# 실행
Write-Host "`nExecuting SQL updates..."
$output = & $psql -U $user -d $dbName -f $sqlFile 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "업데이트 완료!"
} else {
    Write-Host "오류 발생: $output"
}

# 임시 파일 삭제
Remove-Item -Path $sqlFile -ErrorAction SilentlyContinue
Write-Host "완료되었습니다."

