@echo off
REM PostgreSQL 데이터베이스 연결 테스트 스크립트

REM 한글 표시를 위한 코드 페이지 설정
chcp 65001 >nul

echo ========================================
echo PostgreSQL 데이터베이스 연결 테스트
echo ========================================
echo.

set PGPASSWORD=postgres
set PG_BIN=D:\PostgreSQL\17\bin
set PG_USER=postgres
set PG_DB=loandoc

echo 연결 정보:
echo   호스트: localhost
echo   포트: 5432
echo   데이터베이스: %PG_DB%
echo   사용자: %PG_USER%
echo.

cd /d "%PG_BIN%"

echo [1] 연결 테스트...
.\psql.exe -U %PG_USER% -d %PG_DB% -c "SELECT 'Connection OK' as status, current_database() as database, current_user as user;"
if %ERRORLEVEL% NEQ 0 (
    echo 오류: 데이터베이스 연결 실패
    pause
    exit /b 1
)
echo.

echo [2] bank_info 테이블 조회...
.\psql.exe -U %PG_USER% -d %PG_DB% -c "SELECT * FROM bank_info WHERE use_it = 1 ORDER BY id;"
if %ERRORLEVEL% NEQ 0 (
    echo 오류: 쿼리 실행 실패
    pause
    exit /b 1
)
echo.

echo ========================================
echo 연결 테스트 완료
echo ========================================
echo.

pause

