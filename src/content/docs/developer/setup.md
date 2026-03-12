---
title: 개발 환경 설정
description: macOS에서 Samsung Portal 개발 환경 구축, Java 17, Node.js, IDE 설정 및 빌드/실행 방법
---

Samsung Portal 개발에 필요한 환경 설정을 안내합니다.

## 필수 환경

| 요구사항 | 버전 |
|----------|------|
| Java | 17 |
| Maven | 3.9.9 (프로젝트에 wrapper 포함) |
| Node.js | 20+ |
| npm | 10+ |

## macOS 환경 설정

### Java 17 설치

```bash
brew install openjdk@17
```

JAVA_HOME을 설정합니다. 셸 설정 파일(`~/.zshrc`)에 추가하세요:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
```

### Node.js 설치

```bash
brew install node
```

### macOS Sequoia protoc 이슈

macOS Sequoia에서는 `com.apple.provenance` xattr 때문에 Maven protobuf 플러그인의 `protocArtifact`가 silent fail할 수 있습니다. 이 경우 Homebrew로 설치한 protoc를 직접 사용하도록 설정합니다:

```bash
brew install protobuf
```

`pom.xml`의 protobuf-maven-plugin에서 `protocArtifact` 대신 `protocExecutable`을 사용합니다:

```xml
<protocExecutable>/opt/homebrew/bin/protoc</protocExecutable>
```

---

## IDE 설정

### IntelliJ IDEA

1. **Lombok 플러그인** 설치
   - Settings > Plugins > "Lombok" 검색 및 설치
2. **Annotation Processing** 활성화
   - Settings > Build, Execution, Deployment > Compiler > Annotation Processors
   - "Enable annotation processing" 체크
3. **Java SDK** 설정
   - Project Structure > SDKs > Java 17 추가
   - Project Structure > Project > SDK를 Java 17로 설정

---

## 프론트엔드 설정

```bash
cd frontend
npm install
```

## 빌드 및 실행

### 개발 모드

백엔드와 프론트엔드를 각각 실행합니다:

**백엔드** (터미널 1):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

**프론트엔드** (터미널 2):

```bash
cd frontend
npm run dev
```

프론트엔드 개발 서버는 기본적으로 `http://localhost:5173`에서 실행되며, API 요청은 백엔드(`http://localhost:8080`)로 프록시됩니다.

### 프로덕션 빌드

```bash
# 프론트엔드 빌드
cd frontend && npm run build

# 백엔드 빌드 (프론트엔드 결과물을 static/에 포함)
./mvnw clean package
```

### 유용한 Maven 명령어

```bash
./mvnw clean install          # 전체 빌드
./mvnw test                   # 전체 테스트
./mvnw test -Dtest="ClassName"          # 단일 클래스 테스트
./mvnw test -Dtest="ClassName#method"   # 단일 메서드 테스트
./mvnw spring-boot:build-image          # OCI 컨테이너 이미지 빌드
```

---

## 외부 서비스

개발 환경에서 다음 서비스가 필요합니다 (선택적):

| 서비스 | 포트 | 용도 |
|--------|------|------|
| MySQL | 3306, 3307 | testdb/UFSInfo, binmapper DB |
| Redis | 6379 | 캐시 |
| MinIO | 9000 | S3 스토리지 |
| guacd | 4822 | 원격 접속 데몬 |
| Go Excel Service | 50052 | Excel Export (gRPC) |
| Head Server | 10001, 10030 | 테스트 제어 서버 |

:::tip
Head 서버 없이 UI를 개발할 때는 Admin 페이지에서 `testMode`를 활성화하면 TCP 연결 없이 슬롯 페이지를 사용할 수 있습니다.
:::
