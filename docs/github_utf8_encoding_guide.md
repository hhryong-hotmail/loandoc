# GitHub 한글 인코딩 문제 해결 가이드

## 문제 원인

GitHub에서 한글이 깨지는 주요 원인:
1. Git이 파일을 잘못된 인코딩으로 처리
2. `.gitattributes` 파일이 없어서 Git이 파일 인코딩을 제대로 인식하지 못함
3. 파일이 실제로는 다른 인코딩(예: CP949, EUC-KR)으로 저장되어 있음
4. Git 설정에서 인코딩이 명시되지 않음

## 해결 방법

### 1. .gitattributes 파일 생성 (완료됨)

프로젝트 루트에 `.gitattributes` 파일이 생성되었습니다. 이 파일은 Git에게 모든 텍스트 파일을 UTF-8로 처리하도록 지시합니다.

### 2. Git 전역 설정 확인 및 수정

터미널에서 다음 명령어를 실행하여 Git 인코딩 설정을 확인하세요:

```bash
# 현재 설정 확인
git config --global core.quotepath false
git config --global i18n.commitencoding utf-8
git config --global i18n.logoutputencoding utf-8

# Windows PowerShell에서 추가 설정
git config --global core.autocrlf true
```

### 3. 이미 커밋된 파일의 인코딩 변환

이미 잘못된 인코딩으로 커밋된 파일이 있다면:

#### 방법 A: 파일을 UTF-8로 다시 저장

1. VS Code에서 파일 열기
2. 우측 하단의 인코딩 표시 확인 (예: "UTF-8", "Windows 949" 등)
3. 인코딩 클릭 → "인코딩하여 저장" → "UTF-8" 선택
4. 파일 저장 후 커밋

#### 방법 B: PowerShell을 사용한 일괄 변환

```powershell
# UTF-8 인코딩 설정
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 특정 파일을 UTF-8로 변환
$content = Get-Content -Path "파일경로" -Encoding Default
$content | Out-File -FilePath "파일경로" -Encoding UTF8 -NoNewline
```

### 4. 새 파일 저장 시 주의사항

#### VS Code 설정

`.vscode/settings.json` 파일에 다음 설정 추가:

```json
{
  "files.encoding": "utf8",
  "files.autoGuessEncoding": true,
  "files.eol": "\n"
}
```

#### 파일 저장 시 확인

- 파일 저장 전 우측 하단 인코딩 표시 확인
- "UTF-8"로 표시되어 있는지 확인
- 다른 인코딩이면 클릭하여 "UTF-8로 인코딩하여 저장" 선택

### 5. 커밋 메시지 인코딩

커밋 메시지도 UTF-8로 작성하도록 설정:

```bash
git config --global i18n.commitencoding utf-8
```

## 검증 방법

### 1. GitHub에서 확인

1. GitHub 저장소로 이동
2. 한글이 포함된 파일 열기
3. 한글이 정상적으로 표시되는지 확인

### 2. 로컬에서 확인

```bash
# 파일 인코딩 확인 (PowerShell)
Get-Content -Path "파일경로" -Encoding UTF8 | Select-Object -First 5
```

### 3. Git 상태 확인

```bash
# .gitattributes 파일이 적용되었는지 확인
git check-attr -a 파일명
```

## 문제가 지속되는 경우

### 이미 깨진 파일 복구

1. **Git 히스토리에서 복구**
   ```bash
   # 특정 커밋의 파일 확인
   git show 커밋해시:파일경로 > 복구된파일.txt
   ```

2. **인코딩 변환 스크립트 사용**
   - `fix_powershell_encoding.ps1` 참고
   - 파일을 UTF-8로 변환하는 스크립트 작성

3. **수동 변환**
   - 파일을 메모장으로 열기
   - "다른 이름으로 저장" → 인코딩: UTF-8 선택
   - 저장 후 Git에 커밋

## 권장 사항

1. ✅ **모든 텍스트 파일을 UTF-8로 저장**
2. ✅ **.gitattributes 파일 유지**
3. ✅ **Git 전역 설정 적용**
4. ✅ **VS Code 인코딩 설정 확인**
5. ✅ **커밋 전 파일 인코딩 확인**

## 추가 리소스

- [Git 인코딩 설정 공식 문서](https://git-scm.com/docs/git-config#Documentation/git-config.txt-i18ncommitEncoding)
- [GitHub 인코딩 가이드](https://docs.github.com/en/get-started/getting-started-with-git/configuring-git-to-handle-line-endings)

---

**마지막 업데이트:** 2024년 12월
