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

Jackson 기반 직렬화 대신 **JDK 직렬화**를 선택한 이유:

- Hibernate 프록시 객체의 직렬화 문제 회피
- `activateDefaultTyping` 설정 시 null 처리 이슈 방지
- 모든 엔티티가 `implements Serializable` 구현

:::note
Jackson ObjectMapper를 사용할 경우 Hibernate 지연 로딩 프록시에서 직렬화 오류가 발생할 수 있습니다. `JdkSerializationRedisSerializer`를 사용하면 이 문제를 피할 수 있습니다.
:::

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
