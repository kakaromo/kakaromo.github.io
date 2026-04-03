---
title: Redis 캐시
description: Redis 캐시 설정, JDK 직렬화 선택 이유, 캐시 목록, 엔티티 요구사항 및 트러블슈팅을 설명합니다.
---

## Redis 설정

### application.yaml

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

### RedisCacheConfig

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
            .defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new JdkSerializationRedisSerializer()))
            .disableCachingNullValues()
            .entryTtl(Duration.ofMinutes(10));

        // 캐시별 TTL 설정 ...
    }
}
```

### Auto-Configuration 제외

`DataRedisRepositoriesAutoConfiguration`을 제외하여 Spring Data Redis가 JPA 리포지토리를 가로채는 것을 방지합니다.

```java
@SpringBootApplication(exclude = {
    DataRedisRepositoriesAutoConfiguration.class,
})
```

## JdkSerializationRedisSerializer 사용 이유

Jackson 기반 직렬화 대신 **JDK 직렬화**를 선택한 배경과 트레이드오프를 상세히 기술합니다.

### 문제: Jackson 직렬화의 실패

처음에는 `GenericJackson2JsonRedisSerializer`를 사용하려 했으나, 다음 문제들이 발생했습니다:

**1. Hibernate 지연 로딩 프록시**

JPA `@ManyToOne(fetch = LAZY)` 관계가 있는 엔티티를 캐시에 저장할 때, 실제 엔티티 대신 Hibernate 프록시 객체(`HibernateProxy`)가 직렬화됩니다. Jackson은 이 프록시를 정상적인 엔티티로 인식하지 못하고 직렬화에 실패합니다.

```
// 에러 예시
com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
  No serializer found for class org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor
```

**2. `activateDefaultTyping` null 이슈**

Jackson에서 다형성 타입 처리를 위해 `activateDefaultTyping`을 활성화하면, `@Cacheable(unless = "#result == null")` 조건으로 null을 반환할 때 타입 정보 처리에서 `NullPointerException`이 발생합니다.

**3. 복잡한 엔티티 그래프**

`PerformanceHistory` → `PerformanceTestCase` → `PerformanceParser` 같은 연쇄 참조 구조에서 Jackson의 순환 참조 감지와 타입 힌트가 충돌합니다.

### 해결: JDK 직렬화

`JdkSerializationRedisSerializer`는 Java의 기본 `ObjectOutputStream`/`ObjectInputStream`을 사용합니다. Hibernate 프록시도 `Serializable`이면 문제없이 직렬화됩니다.

```java
.serializeValuesWith(RedisSerializationContext.SerializationPair
    .fromSerializer(new JdkSerializationRedisSerializer()))
```

### 트레이드오프

| 장점 | 단점 |
|------|------|
| Hibernate 프록시 호환 | Redis에 바이너리로 저장 (사람이 읽을 수 없음) |
| 설정 단순 | 엔티티 구조 변경 시 기존 캐시 역직렬화 실패 |
| null 처리 안정적 | `redis-cli`로 직접 데이터 확인 어려움 |

### 배포 시 주의사항

엔티티 필드 추가/삭제/타입 변경 후 배포하면 기존 Redis 캐시와 호환되지 않습니다. 배포 후 `redis-cli FLUSHALL` 또는 Admin 캐시 관리 페이지에서 캐시를 비워야 합니다.

`RedisCacheConfig`에 `CacheErrorHandler`가 설정되어 있어 역직렬화 에러가 발생해도 애플리케이션이 중단되지 않고 DB에서 직접 조회합니다 (캐시 우회).

## 캐시 목록

### TestDB 캐시 (TTL: 10분)

| 캐시 이름 | 엔티티 | 대상 메서드 |
|-----------|--------|------------|
| `slotInfomation` | SlotInfomation | `findById` |
| `setInfomation` | SetInfomation | `findById` |
| `compatibilityTestRequest` | CompatibilityTestRequest | `findById` |
| `compatibilityTestCase` | CompatibilityTestCase | `findById` |
| `compatibilityHistory` | CompatibilityHistory | `findById` |
| `performanceTestRequest` | PerformanceTestRequest | `findById` |
| `performanceTestCase` | PerformanceTestCase | `findById` |
| `performanceHistory` | PerformanceHistory | `findById` |

### UFSInfo 캐시 (TTL: 1시간)

| 캐시 이름 | 엔티티 | 대상 메서드 |
|-----------|--------|------------|
| `cellType` | CellType | `findAll` |
| `controller` | Controller | `findAll` |
| `density` | Density | `findAll` |
| `nandSize` | NandSize | `findAll` |
| `nandType` | NandType | `findAll` |
| `ufsVersion` | UfsVersion | `findAll` |

:::tip
UFSInfo는 참조 데이터로 변경이 드물어 1시간 TTL을 적용합니다.
:::

### binmapper 캐시

binmapper DB의 엔티티도 `findAll` 등의 메서드에 캐시가 적용됩니다.

## 엔티티 요구사항

Redis 캐시를 사용하려면 모든 엔티티가 `Serializable` 인터페이스를 구현해야 합니다:

```java
@Entity
@Table(name = "CompatibilityTestRequest")
public class CompatibilityTestRequest implements Serializable {
    // ...
}
```

## @Cacheable 적용 방법

Service 클래스의 `findById` 메서드에 `@Cacheable` 어노테이션을 적용합니다:

```java
@Cacheable(value = "compatibilityTestRequest", unless = "#result == null")
public Optional<CompatibilityTestRequest> findById(Long id) {
    return repository.findById(id);
}
```

- `unless = "#result == null"`: null 결과는 캐시하지 않음
- 기본 TTL: 10분 (설정에 없는 캐시의 폴백)

## 트러블슈팅

### Jackson 직렬화 오류

Jackson ObjectMapper를 사용할 경우 Hibernate 지연 로딩 프록시에서 직렬화 오류가 발생할 수 있습니다. `JdkSerializationRedisSerializer`를 사용하면 이 문제를 피할 수 있습니다.

### JPA 리포지토리 충돌

`DataRedisRepositoriesAutoConfiguration`을 제외하지 않으면 Spring Data Redis가 JPA 리포지토리를 Redis 리포지토리로 인식하려 할 수 있습니다.

### 캐시 무효화

현재는 TTL 기반 자동 만료만 사용합니다. 데이터 수정 시 캐시를 즉시 무효화하려면 `@CacheEvict`를 추가하면 됩니다.

:::caution
엔티티 구조가 변경되면 기존 캐시 데이터와 호환되지 않을 수 있습니다. 배포 후 Redis 캐시를 수동으로 비워야 할 수 있습니다.
:::
