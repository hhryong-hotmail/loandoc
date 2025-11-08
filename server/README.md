# LOANDOC Server (Tomcat)

간단한 Java Servlet 기반 서버 예제입니다. 이 서버는 학습/테스트 용도로만 사용하세요. 비밀번호를 평문으로 저장하므로 실제 서비스에는 적합하지 않습니다.

## 개요
- Java Servlet 기반의 작은 웹 애플리케이션(WAR)입니다.
- 이 저장소의 `server` 모듈은 Java 21을 대상으로 빌드되도록 업그레이드되었습니다.

## 요구사항
- JDK 21 (LTS) — 로컬 또는 CI에서 Maven 빌드에 사용되어야 합니다
- Maven 3.6+ (권장 최신 3.x)
- Tomcat 또는 다른 서블릿 컨테이너(배포 시)

> 참고: 시스템에 여러 Java 버전이 설치된 경우, Maven이 사용하는 JDK가 Java 21인지 `mvn -v`로 확인하세요. (예: Java version: 21.x)

## 로컬 개발 및 빌드
1. JDK 21 설치 및 환경 변수 설정 (PowerShell 예시):

```powershell
# 세션에만 적용
$env:JAVA_HOME = 'C:\Users\<you>\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
mvn -v
```

2. 빌드 (테스트 생략):

```powershell
mvn clean package -DskipTests
```

생성된 WAR: `target/server.war`

### Maven toolchains (선택)
만약 시스템-wide JDK 변경이 불가능하면 Maven toolchains를 사용해 특정 JDK를 지정할 수 있습니다. 예시 파일은 `toolchains.xml.example`에 포함되어 있습니다. 이를 복사하여 `%USERPROFILE%\.m2\toolchains.xml`로 두고 `<jdkHome>`을 로컬 JDK 21 경로로 수정하세요.

## 엔드포인트
- POST /server/api/register
   - 요청 JSON: {"id": "user1", "password": "Passw0rd!"}
   - 응답 JSON: {"ok": true} 또는 {"ok": false, "error": "message"}

## 로컬 저장소
- 서버는 `users.json` 파일을 웹앱 루트(실행 권한이 있는 위치)에 생성하여 등록된 사용자 목록을 저장합니다.

## 보안 경고 (중요)
- 이 예제는 학습용이며, 비밀번호를 해시하지 않고 평문으로 저장합니다.
- 실제 서비스에서는 반드시 HTTPS, 비밀번호 해시(bcrypt 등), 안전한 데이터 저장(데이터베이스), 입력 검증, 인증/인가, CORS/CSRF 보호 등을 적용하세요.

## CI 권장 설정
- CI에서 Java 21로 빌드하도록 워크플로를 설정하세요. 예: GitHub Actions에서 `actions/setup-java`를 사용해 `distribution: 'temurin', java-version: '21'`로 설정.

## 변경 로그 (Upgrade to Java 21)
- pom.xml을 업데이트하여 컴파일 타깃을 Java 21로 변경했습니다 (`maven.compiler.plugin`에서 `<release>21</release>` 사용).
- 오래된 `maven-war-plugin` 관련 API 불일치 문제를 해결하기 위해 `maven-war-plugin`을 3.3.2로 추가/업데이트했습니다.
- 로컬 빌드에서 Java 21 환경에서 `mvn clean package`로 WAR 파일 생성이 성공하는 것을 확인했습니다.

## 향후 작업
- (권장) 컴파일 경고(`unchecked` 관련) 검토 및 코드 개선
- CI 파이프라인에 Java 21 빌드/테스트 추가
- 보안 개선: 비밀번호 해시 및 안전한 저장소로 전환

---
작업을 원하시면 제가 README에 CI 예시(GitHub Actions)를 추가하거나, 컴파일 경고를 직접 분석해 코드 수정 제안을 드리겠습니다.
