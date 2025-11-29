@echo off
REM 8080 포트 사용 현황을 확인하는 스크립트
netstat -ano | findstr 8080
pause