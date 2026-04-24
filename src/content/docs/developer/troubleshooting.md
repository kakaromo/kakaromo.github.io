---
title: 문제 해결
description: Portal 개발 중 자주 발생하는 빌드, gRPC, HEAD TCP, Redis, 프론트엔드, SSE 문제의 원인과 해결 방법
---

개발 중 자주 발생하는 문제와 해결 방법을 정리합니다.

## 빌드 문제

### macOS Sequoia — protoc 실행 실패 (silent fail)

**증상**: `./mvnw clean install` 시 protobuf 컴파일이 아무 에러 없이 실패하며, gRPC 관련 클래스가 생성되지 않음

**원인**: macOS Sequoia에서 `com.apple.provenance` extended attribute가 Maven이 다운로드한 `protocArtifact` 바이너리의 실행을 차단

**해결**: `pom.xml`에서 Homebrew로 설치한 protoc 사용:

```xml
<configuration>
    <!-- protocArtifact 대신 로컬 protoc 사용 -->
    <protocExecutable>/opt/homebrew/bin/protoc</protocExecutable>
</configuration>
```

Homebrew protoc 설치: `brew install protobuf`

---

### Java 버전 불일치

**증상**: `./mvnw clean install` 시 `Unsupported class file major version` 또는 `invalid source release: 17`

**원인**: JAVA_HOME이 Java 17이 아닌 다른 버전을 가리킴

**해결**:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
java -version  # 17 확인
./mvnw clean install
```

---

### Lombok 어노테이션 미인식

**증상**: IDE에서 `@Data`, `@Builder` 등이 인식되지 않거나, getter/setter가 없다는 컴파일 에러

**해결**:
- **IntelliJ**: Settings → Build → Compiler → Annotation Processors → Enable annotation processing 체크
- **VS Code**: Lombok extension 설치 (vscjava.vscode-java-pack에 포함)

---

## gRPC 연결 문제

### Go Agent 서버 연결 실패

**증상**: Agent 벤치마크 실행 시 `UNAVAILABLE: io exception` 또는 연결 타임아웃

**확인 순서**:

1. **Go Agent가 실행 중인지 확인**:
   ```bash
   # Agent 서버 디렉토리에서
   cd ~/project/agent
   go run main.go  # 또는 빌드된 바이너리 실행
   ```

2. **포트 확인** (기본 50051):
   ```bash
   lsof -i :50051
   ```

3. **Portal DB의 서버 설정 확인**: Admin → Server Management에서 Agent 서버의 host/port가 정확한지 확인

4. **테스트 연결**: Agent 페이지에서 서버 옆 "테스트" 버튼으로 연결 확인

---

### Proto 동기화 누락

**증상**: gRPC 호출 시 `UNIMPLEMENTED` 에러 또는 필드 누락

**원인**: Go Agent의 proto와 Java Portal의 proto 정의가 불일치

**해결**:

```bash
# 1. Go proto 파일 확인
cat ~/project/agent/proto/agent.proto

# 2. Java proto 파일과 비교 (java_package만 달라야 함)
diff ~/project/agent/proto/agent.proto src/main/proto/device_agent.proto

# 3. 차이가 있으면 동기화 후 양쪽 재컴파일
# Go:
cd ~/project/agent
PATH="$PATH:$HOME/go/bin" protoc --go_out=paths=source_relative:. --go-grpc_out=paths=source_relative:. proto/agent.proto && cp proto/*.go pb/

# Java:
cd ~/project/portal
./mvnw clean install
```

:::caution
Proto를 수정할 때 반드시 Go와 Java 양쪽을 동시에 업데이트하세요. `java_package` 옵션만 차이가 있고 나머지 메시지/서비스 정의는 동일해야 합니다.
:::

---

### Excel Service gRPC 연결 실패

**증상**: Excel 내보내기 시 `UNAVAILABLE` 에러

**확인**:
- Go Excel Service가 50052 포트에서 실행 중인지 확인
- `application.yaml`의 `spring.grpc.client.channels.excel-service.address`가 `static://localhost:50052`인지 확인

---

## HEAD TCP 연결 문제

### HEAD 서버 연결 실패

**증상**: 슬롯 모니터링 페이지에서 "연결 끊김" 또는 슬롯이 표시되지 않음

**확인 순서**:

1. **포트 공식 확인**: `10000 + suffix`
   - HEAD 포트 10001 → suffix "01"
   - Portal 리스닝 포트 10030 → suffix "30"

2. **방화벽**: Portal의 리스닝 포트(ServerSocket)가 HEAD 서버에서 접근 가능한지 확인. HEAD가 Portal로 역연결하므로, **양방향** 통신이 필요

3. **HEAD 서버 상태**: HEAD 서버가 실행 중이고 지정된 포트에서 수신 중인지 확인

4. **로그 확인**: Spring 로그에서 `HeadTcpClient` 관련 에러 메시지 확인

---

### 재연결 후 슬롯이 비어 있음

**증상**: 재연결 성공 메시지는 나오지만 슬롯 데이터가 없음

**원인**: HEAD 서버가 `initslots` 메시지를 아직 보내지 않음. 연결 직후 HEAD가 초기 데이터를 전송할 때까지 잠시 대기

---

## Redis 문제

### Serializable 누락 에러

**증상**: `java.io.NotSerializableException: com.samsung.move.xxx.entity.XxxEntity`

**원인**: Redis에 캐시하려는 Entity에 `implements Serializable`이 없음

**해결**:

```java
@Entity
public class XxxEntity implements Serializable {  // ← 추가
    // ...
}
```

---

### 클래스 버전 불일치 (배포 후)

**증상**: 배포 후 캐시 조회 시 `InvalidClassException` 또는 엔티티 필드 값이 이상함

**원인**: Entity 구조가 변경되었지만 Redis에 이전 버전의 직렬화된 데이터가 남아 있음. `JdkSerializationRedisSerializer`는 클래스 구조가 바뀌면 역직렬화에 실패

**해결**: Redis 캐시를 수동으로 비우기

```bash
redis-cli FLUSHALL
# 또는 특정 캐시만:
redis-cli --scan --pattern "deviceProfile*" | xargs redis-cli DEL
```

또는 Admin 페이지 → Cache 관리에서 캐시 무효화

:::tip
`RedisCacheConfig`에 `CacheErrorHandler`가 설정되어 있어 직렬화 에러가 발생해도 앱이 죽지 않고 DB에서 직접 조회합니다 (캐시 우회).
:::

---

## 프론트엔드 문제

### node_modules 손상

**증상**: `npm run dev` 시 모듈 import 에러 또는 이상한 타입 에러

**해결**:

```bash
cd frontend
rm -rf node_modules .svelte-kit
npm install
npm run dev
```

---

### Svelte 5 Runes 마이그레이션 이슈

**증상**: `$state`, `$derived`, `$effect` 관련 컴파일 에러

**흔한 실수**:

```svelte
<!-- ❌ 잘못된 사용 -->
<script>
    let count = $state(0);
    $: doubled = count * 2;  // Svelte 4 문법 — Svelte 5에서 동작하지 않음
</script>

<!-- ✅ 올바른 Svelte 5 사용 -->
<script>
    let count = $state(0);
    let doubled = $derived(count * 2);  // $derived 사용
</script>
```

- `$:` reactive statement → `$derived()` 또는 `$effect()`로 대체
- `export let prop` → `let { prop } = $props()`로 대체
- `$` store prefix → 더 이상 사용하지 않음 (Svelte 5 runes)

---

### TypeScript 엄격 모드 에러

**증상**: 타입 에러가 빌드를 차단

**일반적 해결**:
- `JSON.parse()` 결과에 타입 단언: `JSON.parse(data) as MyType`
- Optional 필드에 `?.` 사용: `obj?.field`
- 널 체크 후 접근: `if (data) { data.field }`

---

## SSE 연결 문제

### SSE 연결 즉시 끊김

**증상**: `EventSource` 연결 후 바로 `onerror` 발생

**확인**:
1. 백엔드가 실행 중인지 확인
2. URL 경로가 정확한지 확인 (`/api/head/slots/stream` 등)
3. 인증이 필요한 엔드포인트인지 확인 — SSE 연결도 인증 쿠키가 필요

---

### SSE 연결 자주 끊김

**증상**: 슬롯 모니터링이 몇 분마다 "연결 끊김" → "재연결" 반복

**원인 후보**:
1. **프록시 버퍼링**: nginx 등 리버스 프록시가 SSE 응답을 버퍼링하여 타임아웃 발생
   ```nginx
   # nginx 설정에 추가
   proxy_buffering off;
   proxy_cache off;
   proxy_set_header Connection '';
   proxy_http_version 1.1;
   chunked_transfer_encoding off;
   ```

2. **SseEmitter 타임아웃**: 백엔드에서 `new SseEmitter(0L)` 로 무제한 설정인지 확인 (0L = 타임아웃 없음)

3. **네트워크**: 방화벽이 장시간 유휴 연결을 끊는 경우

---

### SSE 이벤트 수신 안 됨

**증상**: 연결은 되지만 데이터가 오지 않음

**확인**:
- `EventSource.addEventListener('이벤트명', ...)` 의 이벤트명이 백엔드의 `.name("이벤트명")`과 일치하는지 확인
- 기본 `onmessage` 핸들러는 `name`이 없는 이벤트만 수신함. 명시적 이벤트명을 사용하면 `addEventListener`를 써야 함

```javascript
// ❌ name이 있는 이벤트는 onmessage로 받을 수 없음
eventSource.onmessage = (e) => { /* init/update 이벤트 수신 안 됨 */ };

// ✅ addEventListener로 명시적 이벤트명 지정
eventSource.addEventListener('init', (e) => { ... });
eventSource.addEventListener('update', (e) => { ... });
```
