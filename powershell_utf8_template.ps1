# PowerShell 스크립트 한글 인코딩 문제 해결 템플릿
# 이 코드를 스크립트의 맨 위에 추가하세요

# UTF-8 인코딩 설정 (스크립트 시작 부분에 추가)
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 | Out-Null

# 이후 스크립트 코드...

