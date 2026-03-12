---
title: 설치 및 실행
description: Portal 개발 환경 설정 및 실행 방법
---

## 필수 환경

| 소프트웨어 | 버전 | 용도 |
|-----------|------|------|
| Java | 17 | 백엔드 런타임 |
| Node.js | 22+ | 프론트엔드 빌드 |
| MySQL | 8+ | 데이터베이스 (testdb, UFSInfo, binmapper) |
| Redis | 7+ | 캐시 서버 |

## 데이터베이스 준비

3개의 MySQL 데이터베이스를 생성합니다.

```sql
CREATE DATABASE testdb;
CREATE DATABASE UFSInfo;
CREATE DATABASE binmapper;
```

스키마 초기화 SQL은 `sql/` 디렉토리에 있습니다:

```bash
mysql -u root testdb < sql/schema-testdb.sql
mysql -u root UFSInfo < sql/schema-ufsinfo.sql
mysql -u root binmapper < sql/schema-portal.sql
```

## 개발 환경 실행

### 백엔드

```bash title="터미널 1"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run -Dskip.frontend=true
```

### 프론트엔드

```bash title="터미널 2"
cd frontend
npm install
npm run dev
```

:::note
개발 모드에서 프론트엔드는 Vite 개발 서버(5173 포트)에서 실행되며, API 요청은 Spring Boot(8080 포트)로 프록시됩니다.
:::

## 프로덕션 빌드

프론트엔드 + 백엔드를 단일 JAR로 통합 빌드합니다.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean package
```

실행:

```bash
java -jar target/portal-*.jar
```

## 테스트

```bash
./mvnw test                                          # 전체 테스트
./mvnw test -Dtest="com.samsung.portal.ClassName"     # 단일 클래스
./mvnw test -Dtest="com.samsung.portal.ClassName#method"  # 단일 메서드
```

## 선택 사항: 외부 서비스

| 서비스 | 설정 위치 | 설명 |
|--------|-----------|------|
| Go Excel Service | `spring.grpc.client.channels.excel-service.address` | 포트 50052에서 실행 |
| MinIO | `minio.endpoint`, `minio.port` | S3 호환 스토리지 |
| guacd | `guacamole.guacd-host`, `guacamole.guacd-port` | 원격 접속 데몬 |
| Head Server | `portal_head_connections` 테이블 | TCP 통신 대상 |

설정은 `src/main/resources/application.yaml`에서 변경합니다.
