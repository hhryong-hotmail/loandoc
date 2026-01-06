# GitHub History Node.js 서버

3333 포트에서 실행되는 Node.js 서버로 `github_history` 테이블을 조회 및 업데이트할 수 있습니다.

## 설치 방법

1. Node.js가 설치되어 있어야 합니다 (v14 이상 권장)

2. 의존성 설치:
```bash
npm install
```

## 실행 방법

```bash
npm start
```

또는

```bash
node server.js
```

서버가 `http://localhost:3333`에서 실행됩니다.

## 환경 변수

다음 환경 변수를 설정하여 데이터베이스 연결을 변경할 수 있습니다:

- `DB_HOST`: PostgreSQL 호스트 (기본값: localhost)
- `DB_PORT`: PostgreSQL 포트 (기본값: 5432)
- `DB_NAME`: 데이터베이스 이름 (기본값: loandoc)
- `DB_USER`: 데이터베이스 사용자 (기본값: postgres)
- `DB_PASSWORD`: 데이터베이스 비밀번호 (기본값: postgres)

예시:
```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=loandoc DB_USER=postgres DB_PASSWORD=postgres npm start
```

## API 엔드포인트

### GET /api/github-history
목록 조회

**쿼리 파라미터:**
- `q`: 검색어 (프로그램명/변경사유/개발자)
- `status`: 상태 필터 (전체, 대기중, 제출됨, 승인됨, 반려됨)
- `env_type`: 환경 타입 (전체, 테스트, 운영)
- `stage_type`: 단계 타입 (전체, 개발, 테스트, 배포)

**응답:**
```json
{
  "ok": true,
  "rows": [...]
}
```

### GET /api/github-history/:id
단일 항목 조회

**응답:**
```json
{
  "ok": true,
  "row": {...}
}
```

### PUT /api/github-history/:id
항목 업데이트

**요청 본문:**
```json
{
  "approval_number": "20250101-001",
  "target_server": "server1",
  "env_type": "운영",
  "stage_type": "배포",
  "approver": "홍길동",
  "work_content": "작업 내용",
  "test_apply_date": "2025-01-01T10:00:00.000Z",
  "prod_apply_date": "2025-01-02T10:00:00.000Z",
  "prod_scheduled_date": "2025-01-03T10:00:00.000Z",
  "submitted_date": "2025-01-01T09:00:00.000Z",
  "approved_date": "2025-01-01T11:00:00.000Z",
  "approval_reason": "승인 사유",
  "rejected_date": null,
  "rejection_reason": null
}
```

**응답:**
```json
{
  "ok": true,
  "message": "업데이트되었습니다.",
  "row": {...}
}
```

## 웹 화면

브라우저에서 `http://localhost:3333/githubHistory.html`로 접속하여 웹 화면을 사용할 수 있습니다.

## 파일 구조

```
.
├── package.json          # Node.js 의존성 정의
├── server.js            # Express 서버 및 API 엔드포인트
├── public/              # 정적 파일 (HTML, CSS, JS)
│   └── githubHistory.html
└── README_NODE_SERVER.md # 이 파일
```
