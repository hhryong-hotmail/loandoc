# Dashboard (게시판) 기능 구현

## 개요
외국인 근로자 대출 중개 포털에 완전한 CRUD 기능을 가진 게시판을 구현했습니다.

## 구현 내용

### 1. 백엔드 API (server.js)
다음 REST API 엔드포인트를 추가했습니다:

- **GET /api/dashboard** - 모든 게시글 조회 (생성일 기준 내림차순)
- **GET /api/dashboard/:id** - 특정 게시글 조회
- **POST /api/dashboard** - 새 게시글 작성
- **PUT /api/dashboard/:id** - 게시글 수정
- **DELETE /api/dashboard/:id** - 게시글 삭제

#### 데이터베이스 구조
```sql
CREATE TABLE dashboard (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    author VARCHAR(100) DEFAULT 'Anonymous',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Fallback 메커니즘
- PostgreSQL 데이터베이스가 연결되지 않을 경우 자동으로 인메모리 저장소 사용
- 개발 및 테스트 환경에서 DB 없이도 동작 가능

### 2. 프론트엔드 (dashboard.html)

#### 주요 기능
1. **게시글 목록 보기**
   - 모든 게시글을 카드 형식으로 표시
   - 제목, 작성자, 작성일, 내용 미리보기 포함
   - 최신 게시글이 상단에 표시

2. **게시글 상세보기**
   - 게시글 클릭 시 전체 내용 표시
   - 작성자, 작성일, 수정일 메타데이터 표시
   - 수정, 삭제 버튼 제공

3. **게시글 작성**
   - 모달 폼을 통한 새 게시글 작성
   - 제목, 작성자, 내용 입력
   - 실시간 유효성 검사

4. **게시글 수정**
   - 기존 게시글 내용 불러오기
   - 제목과 내용만 수정 가능 (작성자는 고정)
   - 수정일 자동 업데이트

5. **게시글 삭제**
   - 삭제 확인 다이얼로그
   - 삭제 후 목록으로 자동 이동

#### UI/UX 특징
- 반응형 디자인 (모바일/태블릿/데스크톱 지원)
- 직관적인 모달 인터페이스
- 성공/에러 메시지 표시
- 로딩 상태 표시
- XSS 공격 방지 (HTML 이스케이프)

### 3. 추가 파일

#### create_dashboard_table.sql
데이터베이스 테이블 생성 및 샘플 데이터 삽입 스크립트
```bash
psql -U postgres -d loandoc -f create_dashboard_table.sql
```

## 사용 방법

### 서버 시작
```bash
cd /home/user/webapp
node server.js
```

### 접속
브라우저에서 다음 주소로 접속:
- 로컬: http://127.0.0.1:8080/dashboard.html
- 공개 URL: https://8080-iu5hy5o4o2wq7rkz9zrh8-a402f90a.sandbox.novita.ai/dashboard.html

### API 테스트 예제

#### 게시글 목록 조회
```bash
curl http://127.0.0.1:8080/api/dashboard
```

#### 새 게시글 작성
```bash
curl -X POST http://127.0.0.1:8080/api/dashboard \
  -H "Content-Type: application/json" \
  -d '{"title":"제목","content":"내용","author":"작성자"}'
```

#### 게시글 수정
```bash
curl -X PUT http://127.0.0.1:8080/api/dashboard/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"수정된 제목","content":"수정된 내용"}'
```

#### 게시글 삭제
```bash
curl -X DELETE http://127.0.0.1:8080/api/dashboard/1
```

## 테스트 결과
✅ 게시글 목록 조회 - 성공
✅ 게시글 상세 조회 - 성공
✅ 게시글 작성 - 성공
✅ 게시글 수정 - 성공
✅ 게시글 삭제 - 성공

## 보안 고려사항
- XSS 공격 방지를 위한 HTML 이스케이프
- SQL Injection 방지를 위한 파라미터화된 쿼리
- CORS 설정
- Content-Security-Policy 헤더

## 향후 개선 사항
- 사용자 인증 및 권한 관리
- 페이지네이션
- 검색 기능
- 댓글 기능
- 파일 첨부 기능
- 좋아요/조회수 기능

## 기술 스택
- **Backend**: Node.js, Express.js, PostgreSQL, pg
- **Frontend**: Vanilla JavaScript, HTML5, CSS3
- **API**: RESTful API
