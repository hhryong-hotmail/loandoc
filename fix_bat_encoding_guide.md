# .bat 파일 한글 인코딩 문제 해결 가이드

## 문제 원인

.bat 파일에서 한글이 깨지는 이유는 **파일 인코딩**과 **콘솔 코드 페이지** 설정 때문입니다.

### ld.bat에서 정상 작동하는 이유
- 파일이 **ANSI (CP949)** 또는 **UTF-8 BOM** 인코딩으로 저장되어 있음
- 스크립트 시작 부분에 `chcp 65001` 또는 적절한 코드 페이지 설정이 있음

### loandatabase.bat에서 깨지는 이유
- 파일이 **UTF-8 (BOM 없음)** 또는 다른 인코딩으로 저장되어 있음
- 코드 페이지 설정이 없거나 잘못 설정됨

## 해결 방법

### 방법 1: 파일 인코딩을 ANSI (CP949)로 변경
1. 파일을 메모장이나 VS Code로 열기
2. "다른 이름으로 저장" 선택
3. 인코딩을 **ANSI** 또는 **UTF-8 BOM**으로 선택
4. 저장

### 방법 2: 스크립트 시작 부분에 코드 페이지 설정 추가

```batch
@echo off
REM 한글 표시를 위한 코드 페이지 설정
chcp 65001 >nul

REM 이후 스크립트 코드...
```

### 방법 3: UTF-8 BOM으로 저장하고 코드 페이지 설정

```batch
@echo off
REM UTF-8 인코딩 설정
chcp 65001 >nul
REM 또는
REM chcp 949 >nul  (한국어 코드 페이지)

REM 이후 스크립트 코드...
```

## 권장 사항

Windows 배치 파일의 경우:
- **파일 인코딩**: ANSI (CP949) 또는 UTF-8 BOM
- **스크립트 시작 부분**: `chcp 65001 >nul` 추가

## 비교

### 정상 작동하는 예 (ld.bat)
```batch
@echo off
chcp 65001 >nul
echo 한글이 정상적으로 표시됩니다.
```

### 깨지는 예 (loandatabase.bat - 수정 전)
```batch
@echo off
echo 한글이 깨져서 표시됩니다.
```

### 수정 후 (loandatabase.bat)
```batch
@echo off
chcp 65001 >nul
echo 한글이 정상적으로 표시됩니다.
```

