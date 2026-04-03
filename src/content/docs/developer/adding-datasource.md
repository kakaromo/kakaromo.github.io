---
title: 새 데이터소스 추가하기
description: 새로운 MySQL 데이터베이스를 Portal에 연결하는 DataSource 설정 가이드
---

Portal은 현재 3개의 MySQL 데이터소스(testdb, portal, ufsinfo)를 사용합니다. 새로운 DB를 추가해야 할 때의 설정 방법입니다.

## 언제 새 데이터소스가 필요한가?

| 상황 | 판단 |
|------|------|
| Portal 전용 새 테이블 | **portal DB 사용** (새 DataSource 불필요) |
| 다른 팀과 공유하는 DB | **새 DataSource 필요** |
| 외부 시스템의 기존 DB 읽기 | **새 DataSource 필요** |

대부분의 경우 기존 portal DB에 테이블을 추가하면 됩니다. 새 DataSource는 **다른 팀과 공유하는 외부 DB에 접근할 때만** 필요합니다.

---

## Step 1: application.yaml에 DataSource 설정

```yaml
# src/main/resources/application.yaml
spring:
  datasource:
    # 기존 DataSource들...
    testdb:
      url: jdbc:mysql://localhost:3306/testdb
      # ...
    portal:
      url: jdbc:mysql://localhost:3307/portal
      # ...

    # 새 DataSource
    newdb:
      url: jdbc:mysql://localhost:3306/new_database
      username: portal
      password: ${DB_PASSWORD}
      driver-class-name: com.mysql.cj.jdbc.Driver
```

---

## Step 2: DataSourceConfig 클래스 생성

기존 `PortalDataSourceConfig.java`를 참고하여 새 설정 클래스를 만듭니다.

```java
package com.samsung.portal.config.datasource;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.samsung.portal.newdomain",  // ← 새 패키지
    entityManagerFactoryRef = "newdbEntityManagerFactory",
    transactionManagerRef = "newdbTransactionManager"
)
public class NewdbDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.newdb")
    public DataSourceProperties newdbDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource newdbDataSource() {
        return newdbDataSourceProperties()
            .initializeDataSourceBuilder()
            .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean newdbEntityManagerFactory() {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        adapter.setShowSql(false);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(newdbDataSource());
        factory.setJpaVendorAdapter(adapter);
        factory.setPackagesToScan("com.samsung.portal.newdomain");  // ← 엔티티 패키지
        factory.setPersistenceUnitName("newdb");

        var props = new java.util.HashMap<String, Object>();
        props.put("hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        factory.setJpaPropertyMap(props);

        return factory;
    }

    @Bean
    public PlatformTransactionManager newdbTransactionManager(
            EntityManagerFactory newdbEntityManagerFactory) {
        return new JpaTransactionManager(newdbEntityManagerFactory);
    }
}
```

### 핵심 포인트

| 설정 | 역할 |
|------|------|
| `basePackages` | JPA Repository를 스캔할 패키지 (이 패키지 내 Repository만 이 DataSource 사용) |
| `setPackagesToScan` | Entity 클래스를 스캔할 패키지 |
| `entityManagerFactoryRef` | 이 DataSource 전용 EntityManagerFactory 빈 이름 |
| `transactionManagerRef` | 이 DataSource 전용 TransactionManager 빈 이름 |

:::caution[패키지 격리]
**Entity와 Repository는 반드시 `basePackages`에 지정된 패키지 내에 위치해야 합니다.** 다른 DataSource의 패키지에 Entity를 생성하면 잘못된 DB에 연결됩니다.
:::

---

## Step 3: 새 패키지에 Entity/Repository 생성

```
com.samsung.portal.newdomain/
├── entity/
│   └── NewEntity.java
├── repository/
│   └── NewEntityRepository.java
├── service/
│   └── NewEntityService.java
└── controller/
    └── NewEntityController.java
```

Entity, Repository, Service, Controller는 [새 REST API 추가하기](/developer/adding-api-endpoint)의 패턴을 따릅니다.

---

## Step 4: Redis 캐시 연동 (선택)

새 DataSource의 엔티티에도 기존과 동일한 Redis 캐시 패턴을 적용할 수 있습니다:

1. Entity에 `implements Serializable` 추가
2. Service에 `@Cacheable` 적용
3. `RedisCacheConfig`에 캐시 이름과 TTL 등록

```java
// config/RedisCacheConfig.java
Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
    "newEntity", defaultConfig.entryTtl(Duration.ofMinutes(30))
);
```

---

## 기존 DataSource 참고

| 파일 | DataSource | 특징 |
|------|-----------|------|
| `TestdbDataSourceConfig.java` | testdb | `@Primary` (기본 DataSource) |
| `PortalDataSourceConfig.java` | portal | 가장 많은 패키지 스캔 (10개+) |
| `UfsInfoDataSourceConfig.java` | ufsinfo | 읽기 위주, 1시간 TTL 캐시 |

모든 파일은 `config/datasource/` 디렉토리에 위치합니다.
