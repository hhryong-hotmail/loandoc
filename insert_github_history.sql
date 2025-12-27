-- GitHub 히스토리 데이터 삽입
-- UTF-8 인코딩으로 저장

-- 기존 데이터 삭제 (재실행 시)
-- DELETE FROM github_history;

-- 최근 커밋 데이터 삽입
INSERT INTO github_history (database_name, repo_name, change_datetime, program_name, change_reason, developer_name, important_code_content)
VALUES 
('loandoc', 'hhryong-hotmail/loandoc', '2025-12-27 14:43:21', 'server/src/main/java/com/loandoc/BankInfoServlet.java, server/src/main/java/com/loandoc/LoanEstimateServlet.java, server/src/main/webapp/loanAppl.html, server/src/main/webapp/loanTest.html', '테스트 모드 기능 개선 및 loanTest.html 추가', 'LoanDoc Developer', 'BankInfoServlet: 인코딩 처리 개선, LoanEstimateServlet: testMode 로직 개선, loanAppl.html: UI 개선, loanTest.html: 테이블 관리 기능 추가')
ON CONFLICT DO NOTHING;

INSERT INTO github_history (database_name, repo_name, change_datetime, program_name, change_reason, developer_name, important_code_content)
VALUES 
('loandoc', 'hhryong-hotmail/loandoc', '2025-12-25 17:24:22', 'server/src/main/webapp/loanAppl.html', 'Improve loan result sorting and communication status handling', 'LoanDoc Developer', '대출 결과 정렬 로직 개선 및 통신 상태 처리')
ON CONFLICT DO NOTHING;

