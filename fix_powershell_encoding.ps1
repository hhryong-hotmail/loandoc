# PowerShell 스크립트 한글 인코딩 수정 가이드
# 이 스크립트는 다른 PowerShell 스크립트의 한글 인코딩 문제를 해결하는 방법을 보여줍니다.

# 방법 1: 스크립트 시작 부분에 UTF-8 인코딩 설정 추가
# 모든 PowerShell 스크립트 파일의 시작 부분에 다음 코드를 추가하세요:

# UTF-8 인코딩 설정
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 | Out-Null

# 방법 2: Write-Host 대신 [Console]::WriteLine 사용
# Write-Host "한글 텍스트" 대신:
# [Console]::WriteLine("한글 텍스트")

# 방법 3: Out-File 사용 시 -Encoding UTF8 명시
# "텍스트" | Out-File -FilePath "파일명.txt" -Encoding UTF8

Write-Host "PowerShell 한글 인코딩 설정 완료"

