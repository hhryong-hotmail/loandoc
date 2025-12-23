# GitHub Issues 생성 스크립트
# 사용법: .\create_github_issues.ps1
# 
# 사전 요구사항:
# 1. GitHub CLI (gh) 설치: https://cli.github.com/
# 2. GitHub 인증: gh auth login
# 3. 현재 디렉토리가 Git 저장소여야 함

$milestone = "2024-12-31"
$repo = "hhryong-hotmail/loandoc"

# 마일스톤 생성 (이미 있으면 스킵)
Write-Host "마일스톤 생성 중: $milestone" -ForegroundColor Cyan
gh api repos/$repo/milestones -X POST -f title="$milestone" -f due_on="2024-12-31T23:59:59Z" -f description="2024년 12월 31일까지 완료해야 할 프로젝트 목표" 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "마일스톤이 이미 존재하거나 생성에 실패했습니다. 계속 진행합니다..." -ForegroundColor Yellow
}

# Issues 목록
$issues = @(
    @{
        Title = "[목표 1] 은행별 예상금리를 table에 저장"
        Body = @"
## 목표
은행별 예상금리를 데이터베이스 테이블에 저장하는 기능 구현

## 작업 내용
- [ ] 데이터베이스 테이블 설계
- [ ] 은행별 예상금리 데이터 입력 기능
- [ ] API 엔드포인트 구현
- [ ] 테스트 작성

## 우선순위
높음

## 예상 완료일
2024년 12월 31일
"@
        Labels = @("enhancement", "database", "high-priority")
    },
    @{
        Title = "[목표 2] 은행별 금리가 같을 경우, 대출금액이 높을 수록 우선 순위"
        Body = @"
## 목표
은행별 금리가 동일한 경우, 대출금액이 높은 순서로 정렬하는 로직 구현

## 작업 내용
- [ ] 정렬 알고리즘 구현
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성

## 우선순위
높음

## 예상 완료일
2024년 12월 31일
"@
        Labels = @("enhancement", "algorithm", "high-priority")
    },
    @{
        Title = "[목표 3] 은행별 대출금액이 높을 수록 금리가 낮을 수록 우선 순위"
        Body = @"
## 목표
대출금액이 높고 금리가 낮을수록 우선순위가 높은 정렬 로직 구현

## 작업 내용
- [ ] 복합 정렬 알고리즘 구현 (금액 내림차순, 금리 오름차순)
- [ ] 가중치 계산 로직 구현
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성

## 우선순위
높음

## 예상 완료일
2024년 12월 31일
"@
        Labels = @("enhancement", "algorithm", "high-priority")
    },
    @{
        Title = "[목표 4] 은행별 금리, 금액이 같을 경우 통신 속도가 빠를 수록 우선 순위"
        Body = @"
## 목표
금리와 금액이 동일한 경우, 통신 속도를 기준으로 우선순위 결정

## 작업 내용
- [ ] 통신 속도 측정 기능 구현
- [ ] 3단계 정렬 로직 구현 (금리 → 금액 → 통신속도)
- [ ] 성능 테스트 작성

## 우선순위
중간

## 예상 완료일
2024년 12월 31일
"@
        Labels = @("enhancement", "algorithm", "performance")
    },
    @{
        Title = "[목표 5] github을 이용한 형상관리시스템 개발"
        Body = @"
## 목표
GitHub를 활용한 형상관리 시스템 구축 및 워크플로우 정립

## 작업 내용
- [ ] 브랜치 전략 수립 (Git Flow 등)
- [ ] Pull Request 템플릿 작성
- [ ] CI/CD 파이프라인 구축
- [ ] 코드 리뷰 프로세스 정립
- [ ] 문서화

## 우선순위
높음

## 예상 완료일
2024년 12월 31일
"@
        Labels = @("enhancement", "devops", "documentation", "high-priority")
    },
    @{
        Title = "[목표 6] 결재화면을 이용한 형상관리시스템 개발"
        Body = @"
## 목표
결재 화면을 통한 형상관리 시스템 개발

## 작업 내용
- [ ] 결재 화면 UI/UX 설계
- [ ] 결재 워크플로우 구현
- [ ] 결재 이력 관리
- [ ] 권한 관리 시스템 구현
- [ ] 테스트 작성

## 우선순위
중간

## 예상 완료일
2024년 12월 31일
"@
        Labels = @("enhancement", "feature", "ui")
    },
    @{
        Title = "[목표 7] 약관에서 필수/선택을 다시 한번 체크할 것"
        Body = @"
## 목표
약관의 필수/선택 항목 재검토 및 수정

## 작업 내용
- [ ] 현재 약관 항목 목록 작성
- [ ] 법적 요구사항 검토
- [ ] 필수/선택 항목 재분류
- [ ] UI에서 필수/선택 표시 업데이트
- [ ] 테스트 작성

## 우선순위
높음

## 예상 완료일
2024년 12월 31일
"@
        Labels = @("bug", "legal", "high-priority")
    }
)

# Issues 생성
Write-Host "`nGitHub Issues 생성 중..." -ForegroundColor Cyan
$createdIssues = @()

foreach ($issue in $issues) {
    Write-Host "`n생성 중: $($issue.Title)" -ForegroundColor Yellow
    
    # Issue 생성
    $bodyFile = [System.IO.Path]::GetTempFileName()
    $issue.Body | Out-File -FilePath $bodyFile -Encoding UTF8
    
    $labelsArg = $issue.Labels -join ","
    
    $result = gh issue create `
        --repo $repo `
        --title $issue.Title `
        --body-file $bodyFile `
        --label $labelsArg `
        --milestone $milestone `
        --json number,url,title
    
    if ($LASTEXITCODE -eq 0) {
        $issueData = $result | ConvertFrom-Json
        $createdIssues += $issueData
        Write-Host "✓ 생성 완료: #$($issueData.number) - $($issueData.url)" -ForegroundColor Green
    } else {
        Write-Host "✗ 생성 실패" -ForegroundColor Red
    }
    
    Remove-Item $bodyFile
}

# 결과 요약
Write-Host "`n" + "="*60 -ForegroundColor Cyan
Write-Host "생성 완료 요약" -ForegroundColor Cyan
Write-Host "="*60 -ForegroundColor Cyan
Write-Host "총 $($createdIssues.Count)개의 Issue가 생성되었습니다.`n" -ForegroundColor Green

foreach ($issue in $createdIssues) {
    Write-Host "  #$($issue.number) - $($issue.title)" -ForegroundColor White
    Write-Host "    $($issue.url)" -ForegroundColor Gray
}

Write-Host "`nGitHub에서 확인: https://github.com/$repo/issues" -ForegroundColor Cyan

