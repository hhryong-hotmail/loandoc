# GitHub 프로젝트 관리 가이드

## 개요
이 문서는 LoanDoc 프로젝트의 목표를 GitHub Issues와 Projects를 통해 체계적으로 관리하는 방법을 설명합니다.

## 목표 관리 방법

### 1. README.md 체크리스트
- 프로젝트 루트의 `README.md`에 전체 목표가 체크리스트로 정리되어 있습니다.
- 각 작업이 완료되면 체크박스를 업데이트하세요: `- [x]` (완료) 또는 `- [ ]` (미완료)

### 2. GitHub Issues
각 목표는 개별 GitHub Issue로 관리됩니다.

#### Issues 생성 방법

**방법 1: 자동 생성 (권장)**
```powershell
# GitHub CLI가 설치되어 있어야 합니다
# 설치: https://cli.github.com/
# 인증: gh auth login

.\create_github_issues.ps1
```

**방법 2: 수동 생성**
1. GitHub 저장소로 이동: https://github.com/hhryong-hotmail/loandoc
2. "Issues" 탭 클릭
3. "New issue" 클릭
4. "목표 작업" 템플릿 선택
5. 내용 작성 후 제출

#### Issue 관리
- **Label 사용**: `enhancement`, `bug`, `high-priority`, `database`, `algorithm` 등
- **Milestone 설정**: 모든 Issue는 `2024-12-31` 마일스톤에 연결
- **Assignee 지정**: 담당자 지정
- **Progress 추적**: Issue 내부에 체크리스트로 세부 작업 관리

### 3. GitHub Projects
칸반 보드를 사용하여 작업 진행 상황을 시각적으로 관리합니다.

#### Projects 보드 생성 방법

1. **GitHub 저장소에서 Projects 생성**
   - 저장소 메인 페이지 → "Projects" 탭
   - "New project" 클릭
   - "Board" 템플릿 선택
   - 프로젝트 이름: "LoanDoc 2024 목표"

2. **컬럼 설정**
   ```
   - 📋 Backlog (대기)
   - 🔄 In Progress (진행 중)
   - 👀 Review (검토 중)
   - ✅ Done (완료)
   ```

3. **Issues 연결**
   - 각 Issue를 적절한 컬럼으로 드래그 앤 드롭
   - 진행 상황에 따라 컬럼 이동

4. **필터 설정**
   - Milestone: `2024-12-31` 필터 적용
   - Label로 추가 필터링 가능

#### Projects 자동화 (선택사항)
GitHub Actions를 사용하여 Issue 상태에 따라 자동으로 컬럼 이동:
- Issue가 열리면 → "In Progress"
- PR이 생성되면 → "Review"
- Issue가 닫히면 → "Done"

### 4. Pull Request 워크플로우

1. **브랜치 생성**
   ```bash
   git checkout -b feature/목표1-은행별-예상금리
   ```

2. **작업 수행 및 커밋**
   ```bash
   git add .
   git commit -m "feat: 은행별 예상금리 테이블 추가"
   ```

3. **Pull Request 생성**
   - GitHub에서 PR 생성
   - PR 템플릿에 따라 내용 작성
   - 관련 Issue 번호 언급: `Closes #1`

4. **코드 리뷰 및 병합**
   - 리뷰 후 승인
   - 병합 시 Issue 자동 닫힘

5. **README 업데이트**
   - 작업 완료 시 README.md의 체크리스트 업데이트

## 진행 상황 추적

### 주간 리뷰
매주 다음을 확인하세요:
- [ ] 완료된 작업 체크
- [ ] 진행 중인 작업 상태 업데이트
- [ ] 차질이 있는 작업 식별 및 조치
- [ ] 다음 주 작업 계획 수립

### 마일스톤 리뷰
2024년 12월 31일 전에:
- [ ] 모든 목표 완료 여부 확인
- [ ] 미완료 작업 분석
- [ ] 다음 단계 계획 수립

## 유용한 GitHub 명령어

```bash
# Issues 목록 확인
gh issue list --milestone "2024-12-31"

# 특정 Issue 확인
gh issue view <번호>

# Issue 상태 변경
gh issue close <번호>
gh issue reopen <번호>

# Projects 보드 확인
gh project view
```

## 참고 자료
- [GitHub Issues 가이드](https://docs.github.com/en/issues)
- [GitHub Projects 가이드](https://docs.github.com/en/issues/planning-and-tracking-with-projects)
- [GitHub CLI 문서](https://cli.github.com/manual/)

