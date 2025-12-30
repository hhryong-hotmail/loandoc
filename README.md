# LoanDoc 프로젝트

대출 신청 및 관리 시스템

## 📋 프로젝트 목표 (2024년 12월 31일까지)

### 진행 상황
- [ ] 1. 은행별 예상금리를 table에 저장
- [ ] 2. 은행별 금리가 같을 경우, 대출금액이 높을 수록 우선 순위
- [ ] 3. 은행별 대출금액이 높을 수록 금리가 낮을 수록 우선 순위
- [ ] 4. 은행별 금리, 금액이 같을 경우 통신 속도가 빠를 수록 우선 순위
- [x] 5. github을 이용한 형상관리시스템 개발
- [x] 6. 결재화면을 이용한 형상관리시스템 개발
- [ ] 7. 약관에서 필수/선택을 다시 한번 체크할 것

**마일스톤: 2024년 12월 31일**

---

## 📁 프로젝트 구조

```
LoanDoc/
├── server/          # Java Servlet 기반 서버 (Tomcat)
├── docs/            # 문서 및 업무처리흐름도
├── server.js        # Node.js 서버 (있는 경우)
└── README.md        # 이 파일
```

## 🚀 시작하기

자세한 서버 설정 및 빌드 방법은 [server/README.md](server/README.md)를 참조하세요.

## 📝 작업 관리

### GitHub Issues 사용
각 목표에 대해 GitHub Issue를 생성하여 상세 작업을 추적할 수 있습니다:
- Issue 제목: 목표 번호와 설명
- Label: `enhancement`, `feature`, `bug` 등
- Milestone: `2024-12-31` 마일스톤 생성
- Assignee: 담당자 지정

### GitHub Projects 사용
GitHub Projects 보드를 생성하여 칸반 스타일로 작업을 관리할 수 있습니다:
- To Do
- In Progress
- Review
- Done

---

## 📅 진행 상황 업데이트

각 작업이 완료되면 이 README의 체크리스트를 업데이트하고, 관련 커밋 메시지에 `Closes #이슈번호`를 포함하세요.

---

**마지막 업데이트:** 2024년 11월

