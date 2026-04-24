---
title: 새 REST API 추가하기
description: Entity → Repository → Service → Controller → Frontend API 함수까지 — 새로운 REST API 엔드포인트를 추가하는 단계별 가이드
---

Portal에 새로운 REST API를 추가하는 전체 워크플로우입니다. 백엔드 Entity부터 프론트엔드 API 함수까지 6단계로 진행합니다.

## 전체 흐름

```
1. 데이터소스 선택
2. Entity 생성
3. Repository 생성
4. Service 생성 (+ 캐시)
5. Controller 생성
6. Frontend API 함수 추가
```

---

## Step 1: 데이터소스 선택

Portal은 3개의 MySQL 데이터소스를 사용합니다. 새 Entity가 어느 DB에 속하는지 먼저 결정하세요.

| DataSource | 패키지 | DB | 언제 사용? |
|------------|--------|-----|-----------|
| **testdb** | `com.samsung.move.testdb.*` | testdb (3306) | 레거시 시스템과 공유하는 테스트 데이터 |
| **portal** | `com.samsung.move.{admin,agent,auth,binmapper,...}` | portal (3307) | Portal 전용 데이터 |
| **ufsinfo** | `com.samsung.move.ufsinfo.*` | ufsinfo (3306) | UFS 참조 코드 (읽기 위주) |

:::tip[판단 기준]
- 다른 시스템과 데이터를 **공유**하는가? → testdb 또는 ufsinfo
- Portal만 사용하는 **새로운 데이터**인가? → portal
- 기존 테이블에 **추가 기능**인가? → 해당 테이블이 속한 DB
:::

**패키지가 DataSource를 결정합니다.** 각 `DataSourceConfig` 클래스의 `@EnableJpaRepositories(basePackages = ...)`에 지정된 패키지 경로에 Entity와 Repository를 생성해야 합니다.

---

## Step 2: Entity 생성

선택한 DataSource 패키지 내에 Entity 클래스를 생성합니다.

```java
package com.samsung.move.agent.entity;  // portal DB에 속하는 경우

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "portal_device_profiles")
public class DeviceProfile implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private String manufacturer;

    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
```

### 필수 규칙

| 규칙 | 이유 |
|------|------|
| `implements Serializable` | Redis 캐시 직렬화 호환 (JDK Serializer) |
| `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` | Lombok 표준 패턴 |
| `@Table(name = "...")` | 명시적 테이블 이름 지정 |
| `@Id` + `@GeneratedValue(IDENTITY)` | MySQL AUTO_INCREMENT 매핑 |

### portal DB의 테이블 명명 규칙

portal DB의 테이블은 `portal_` 접두사를 사용합니다 (예: `portal_agent_servers`, `portal_job_executions`). 컬럼명은 camelCase입니다.

---

## Step 3: Repository 생성

같은 패키지 내에 JPA Repository를 생성합니다.

```java
package com.samsung.move.agent.repository;

import com.samsung.move.agent.entity.DeviceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceProfileRepository extends JpaRepository<DeviceProfile, Long> {

    // 커스텀 쿼리 (필요 시)
    List<DeviceProfile> findByManufacturer(String manufacturer);

    boolean existsByDeviceName(String deviceName);
}
```

:::caution[패키지 위치 주의]
Repository가 해당 DataSource의 `basePackages`에 포함되지 않으면 Spring이 인식하지 못합니다. 예를 들어, portal DB를 사용하는 Entity의 Repository는 반드시 `PortalDataSourceConfig`의 `basePackages`에 포함된 패키지 내에 있어야 합니다.
:::

---

## Step 4: Service 생성

비즈니스 로직과 캐시를 담당하는 Service를 생성합니다.

```java
package com.samsung.move.agent.service;

import com.samsung.move.agent.entity.DeviceProfile;
import com.samsung.move.agent.repository.DeviceProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DeviceProfileService {

    private final DeviceProfileRepository repository;

    public List<DeviceProfile> findAll() {
        return repository.findAll();
    }

    @Cacheable(value = "deviceProfile", key = "#id", unless = "#result == null")
    public DeviceProfile findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @CacheEvict(value = "deviceProfile", key = "#entity.id")
    public DeviceProfile save(DeviceProfile entity) {
        return repository.save(entity);
    }

    @CacheEvict(value = "deviceProfile", key = "#id")
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
```

### 캐시 패턴

| 어노테이션 | 용도 | 조건 |
|-----------|------|------|
| `@Cacheable` | 읽기 시 캐시 확인 후 miss이면 DB 조회 | `unless = "#result == null"` (null 미캐싱) |
| `@CacheEvict` | 쓰기/삭제 시 관련 캐시 무효화 | key로 정확한 대상 지정 |

캐시를 사용하지 않을 경우 어노테이션을 생략하면 됩니다.

캐시를 사용할 경우 `RedisCacheConfig`에 TTL을 등록해야 합니다:

```java
// config/RedisCacheConfig.java 의 cacheManager() 메서드 내
Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
    "deviceProfile", defaultConfig.entryTtl(Duration.ofMinutes(10))
    // ... 기존 캐시들
);
```

---

## Step 5: Controller 생성

REST 엔드포인트를 생성합니다.

```java
package com.samsung.move.agent.controller;

import com.samsung.move.agent.entity.DeviceProfile;
import com.samsung.move.agent.service.DeviceProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/device-profiles")
@RequiredArgsConstructor
public class DeviceProfileController {

    private final DeviceProfileService service;

    @GetMapping
    public ResponseEntity<List<DeviceProfile>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceProfile> findById(@PathVariable Long id) {
        DeviceProfile entity = service.findById(id);
        if (entity == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(entity);
    }

    @PostMapping
    public ResponseEntity<DeviceProfile> create(@RequestBody DeviceProfile entity) {
        return ResponseEntity.ok(service.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceProfile> update(@PathVariable Long id, @RequestBody DeviceProfile entity) {
        entity.setId(id);
        return ResponseEntity.ok(service.save(entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
```

### CSRF & 권한

Spring Security 설정에 의해:
- **GET** → 인증된 사용자 모두 허용
- **POST/PUT/DELETE/PATCH** → **ADMIN 역할** 필요 + CSRF 토큰 필수

별도 설정은 필요 없습니다 — `SecurityConfig`의 글로벌 규칙이 적용됩니다.

### 에러 응답 패턴

비즈니스 에러(중복 등)를 클라이언트에 전달할 때:

```java
@PostMapping
public ResponseEntity<?> create(@RequestBody DeviceProfile entity) {
    if (repository.existsByDeviceName(entity.getDeviceName())) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "이미 존재하는 디바이스입니다: " + entity.getDeviceName()));
    }
    return ResponseEntity.ok(service.save(entity));
}
```

프론트엔드 `client.ts`가 409 응답의 `error` 필드를 파싱하여 사용자에게 표시합니다.

---

## Step 6: 프론트엔드 API 함수

### API 함수 작성

`frontend/src/lib/api/` 디렉토리에 타입과 함수를 추가합니다.

```typescript
// frontend/src/lib/api/deviceProfile.ts
import { get, post, put, del } from './client.js';

export interface DeviceProfile {
    id: number;
    deviceName: string;
    manufacturer: string;
    description?: string;
    createdAt?: string;
}

export function fetchDeviceProfiles(): Promise<DeviceProfile[]> {
    return get('/device-profiles');
}

export function fetchDeviceProfile(id: number): Promise<DeviceProfile> {
    return get(`/device-profiles/${id}`);
}

export function createDeviceProfile(
    data: Omit<DeviceProfile, 'id' | 'createdAt'>
): Promise<DeviceProfile> {
    return post('/device-profiles', data);
}

export function updateDeviceProfile(
    id: number,
    data: Omit<DeviceProfile, 'id' | 'createdAt'>
): Promise<DeviceProfile> {
    return put(`/device-profiles/${id}`, data);
}

export function deleteDeviceProfile(id: number): Promise<void> {
    return del(`/device-profiles/${id}`);
}
```

### 컴포넌트에서 사용

```svelte
<script lang="ts">
    import { fetchDeviceProfiles, type DeviceProfile } from '$lib/api/deviceProfile.js';
    import { onMount } from 'svelte';

    let profiles = $state<DeviceProfile[]>([]);

    onMount(async () => {
        profiles = await fetchDeviceProfiles();
    });
</script>
```

**CSRF 토큰은 `client.ts`가 자동으로 처리합니다.** POST/PUT/DELETE 요청 시 쿠키에서 `XSRF-TOKEN`을 읽어 헤더에 추가하므로, 프론트엔드 코드에서 별도 처리가 필요 없습니다.

---

## 체크리스트

완성 후 확인할 사항:

- [ ] Entity가 올바른 DataSource 패키지에 위치
- [ ] `implements Serializable` 포함
- [ ] Repository가 DataSourceConfig의 `basePackages`에 포함
- [ ] `@Transactional` 적용
- [ ] 캐시 사용 시 `RedisCacheConfig`에 TTL 등록
- [ ] Controller 경로가 `/api/`로 시작
- [ ] 프론트엔드 API 함수에 타입 정의
- [ ] POST/PUT/DELETE가 ADMIN 권한으로 보호됨 (기본 적용)
