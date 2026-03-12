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
