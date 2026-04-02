---
title: 코딩 컨벤션
description: Samsung Portal 프로젝트의 백엔드/프론트엔드 코딩 컨벤션, 패키지 구조, 명명 규칙, 커밋 메시지 가이드
---

Samsung Portal 프로젝트의 코딩 컨벤션입니다.

## 백엔드 (Spring Boot)

### 패키지 구조

도메인별로 패키지를 분리합니다. 각 패키지는 entity, repository, service, controller를 포함합니다.

```
com.samsung.portal
├── head/           # Head TCP 통신, SSE, 슬롯 상태
├── testdb/         # 테스트 데이터 CRUD
│   ├── compatibility/
│   ├── performance/
│   ├── set/
│   ├── slot/
│   └── excel/
├── ufsinfo/        # UFS 참조 데이터
├── binmapper/      # Binary Struct Mapper
├── tcgroup/        # TC 그룹 관리
├── minio/          # S3 스토리지
├── logbrowser/     # 로그 브라우저
├── guacamole/      # 원격 접속
├── admin/          # 관리자 대시보드
├── auth/           # 인증
└── config/         # DataSource, Security 등 설정
```

### 엔티티

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "table_name")
public class EntityName implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
}
```

- Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` 사용
- `implements Serializable` 필수 (Redis 캐싱 호환)
- JPA 어노테이션으로 테이블/컬럼 매핑

### Repository

```java
public interface EntityRepository extends JpaRepository<EntityName, Long> {
    // 필요 시 커스텀 쿼리 추가
}
```

### Service

```java
@Service
@Transactional
@RequiredArgsConstructor
public class EntityService {
    private final EntityRepository repository;

    @Cacheable(value = "entityCache", key = "#id", unless = "#result == null")
    public Optional<EntityName> findById(Long id) {
        return repository.findById(id);
    }
}
```

- `@Service`, `@Transactional` 기본 적용
- `@Cacheable` 적용 시 `unless = "#result == null"` 조건 필수

### Controller

```java
@RestController
@RequestMapping("/api/entities")
@RequiredArgsConstructor
public class EntityController {
    private final EntityService service;

    @GetMapping
    public ResponseEntity<List<EntityName>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PostMapping
    public ResponseEntity<EntityName> create(@RequestBody EntityName entity) {
        return ResponseEntity.ok(service.save(entity));
    }
}
```

- `@RestController` + `@RequestMapping("/api/...")` 패턴
- `ResponseEntity`로 응답 반환

---

## 프론트엔드 (SvelteKit)

### Svelte 5 Runes

Svelte 5의 Runes 문법을 사용합니다:

```svelte
<script lang="ts">
  // Props
  let { data, onSubmit }: { data: MyType; onSubmit: () => void } = $props();

  // 반응형 상태
  let count = $state(0);
  let items = $state<Item[]>([]);

  // 파생 값
  let total = $derived(items.length);
  let filtered = $derived(items.filter(i => i.active));

  // 부수효과
  $effect(() => {
    console.log('count changed:', count);
  });
</script>
```

| Rune | 용도 |
|------|------|
| `$state` | 반응형 상태 변수 |
| `$derived` | 파생 값 (computed) |
| `$effect` | 부수효과 (watch) |
| `$props` | 컴포넌트 Props |

### API 클라이언트

`client.ts`의 `request()` 래퍼를 사용합니다. XSRF 토큰 자동 추가, 401 리다이렉트를 처리합니다.

```typescript
import { request } from '$lib/api/client';

// GET
const data = await request<MyType[]>('/api/entities');

// POST
const result = await request<MyType>('/api/entities', {
  method: 'POST',
  body: JSON.stringify(newEntity)
});
```

### 컴포넌트 파일 구조

```
frontend/src/lib/
├── api/            # API 클라이언트 함수
├── components/     # 공용 컴포넌트
│   ├── ui/         # shadcn-svelte 기본 컴포넌트
│   ├── data-table/ # DataTable 관련
│   ├── perf-content/ # 성능 차트 컴포넌트
│   └── perf-chart/   # ECharts 래퍼
├── stores/         # Svelte stores
└── types/          # TypeScript 타입 정의
```

---

## Proto 컴파일 워크플로우

Portal은 Go Agent 서버 및 Go Excel Service와 gRPC로 통신합니다. 양쪽 프로젝트의 proto 파일을 동기화된 상태로 유지해야 합니다.

### Proto 파일 위치

| 프로젝트 | 경로 |
|----------|------|
| Go Agent | `~/project/agent/proto/agent.proto` |
| Java Portal | `src/main/proto/device_agent.proto` |

두 파일은 동일한 서비스/메시지 정의를 공유하며, Java 쪽에만 `java_package` 옵션이 추가됩니다.

### Go proto 컴파일

```bash
cd ~/project/agent
PATH="$PATH:$HOME/go/bin" protoc \
  --go_out=paths=source_relative:. \
  --go-grpc_out=paths=source_relative:. \
  proto/agent.proto \
  && cp proto/*.go pb/
```

- `protoc-gen-go`, `protoc-gen-go-grpc` 플러그인이 `$HOME/go/bin`에 설치되어 있어야 합니다.
- 생성된 `.go` 파일을 `pb/` 디렉토리로 복사합니다.

### Java proto 컴파일

Maven 빌드 시 `protobuf-maven-plugin`이 자동으로 컴파일합니다.

```bash
./mvnw clean install
```

macOS Sequoia 환경에서는 `pom.xml`에 `protocExecutable=/opt/homebrew/bin/protoc`을 설정해야 합니다 (xattr 이슈 회피).

:::caution
Proto 파일을 수정할 때는 반드시 Go와 Java 양쪽 모두 업데이트하고 컴파일해야 합니다. `java_package` 옵션만 차이가 있고 나머지 정의는 동일해야 합니다.
:::

---

## Agent gRPC 서비스 추가 패턴

새로운 Agent gRPC RPC를 추가할 때의 전체 워크플로우입니다.

### 1. Proto 정의 추가

`agent.proto`에 새 RPC와 메시지를 정의합니다 (Go/Java 양쪽 동일).

```protobuf
service DeviceAgent {
  rpc NewMethod(NewRequest) returns (NewResponse);
}

message NewRequest { ... }
message NewResponse { ... }
```

### 2. Go 서버 구현

Go Agent 서버에서 RPC 핸들러를 구현합니다.

```go
func (s *server) NewMethod(ctx context.Context, req *pb.NewRequest) (*pb.NewResponse, error) {
    // 구현
}
```

### 3. Java gRPC Client 추가

`AgentGrpcClient.java`에 새 메서드를 추가합니다.

```java
public NewResponse newMethod(String host, int port, NewRequest request) {
    var channel = connectionManager.getChannel(host, port);
    var stub = DeviceAgentGrpc.newBlockingStub(channel);
    return stub.newMethod(request);
}
```

### 4. Controller 엔드포인트 추가

`AgentController.java`에 REST API를 추가합니다.

```java
@PostMapping("/new-method")
public ResponseEntity<?> newMethod(@RequestBody NewMethodDto dto) {
    // AgentGrpcClient 호출
}
```

### 5. 프론트엔드 연동

SvelteKit에서 API를 호출하고 UI를 구현합니다.

---

## 커밋 메시지

커밋 메시지는 한국어로 작성합니다. prefix는 영어를 유지합니다.

```
feat: 새 기능 설명
fix: 버그 수정 설명
refactor: 리팩터링 설명
docs: 문서 변경
chore: 빌드/설정 변경
```

**예시:**

```
feat: MinIO 버킷 가시성 관리 기능 추가
fix: LogViewer 바이너리 파일 강제 열기 오류 수정
refactor: Head 연결 관리를 DB로 이관
```
