---
title: 데이터베이스
description: Samsung Portal의 3개 MySQL 데이터베이스(testdb, UFSInfo, binmapper) 스키마, 테이블 구조 및 관계를 설명합니다.
---

## 개요

Samsung Portal은 세 개의 MySQL 데이터베이스를 사용합니다. 각각 독립적인 DataSource, EntityManagerFactory, TransactionManager로 설정됩니다.

| DB | 용도 | DataSource Config | 패키지 |
|----|------|-------------------|--------|
| **testdb** | UFS 테스트 관리 데이터 | `config.datasource.TestdbDataSourceConfig` (Primary) | `testdb.*` |
| **UFSInfo** | UFS 참조 데이터 (코드 테이블) | `config.datasource.UfsInfoDataSourceConfig` | `ufsinfo.*` |
| **binmapper** | Portal 전용 데이터 | `config.datasource.PortalDataSourceConfig` | `admin.*`, `auth`, `binmapper.*`, `head.entity/repository`, `minio`, `tcgroup.*`, `makesetgroup.*` |

```yaml
# application.yaml
spring:
  datasource:
    testdb:
      url: jdbc:mysql://127.0.0.1:3306/testdb
    ufsinfo:
      url: jdbc:mysql://127.0.0.1:3306/UFSInfo
    binmapper:
      url: jdbc:mysql://127.0.0.1:3307/binmapper
```

:::note
**왜 3개 DB를 분리하는가?**
- **testdb**: 레거시 시스템과 공유하는 DB. 스키마 변경 불가
- **UFSInfo**: 참조 코드 테이블. 다른 시스템에서도 공유 사용
- **binmapper**: Portal 전용 도구 데이터. 독립적으로 스키마 관리 가능
:::

---

## testdb 데이터베이스

### CompatibilityTestRequest

호환성 테스트 요청 정보.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| ID | bigint (PK, AUTO) | 고유 ID |
| NAME | varchar | 요청 이름 |
| CONTROLLER | varchar | 컨트롤러 (UFSInfo.Controller 참조) |
| SPEC_VERSION | varchar | UFS 스펙 버전 |
| CELL_TYPE | varchar | 셀 타입 |
| NAND_TYPE | varchar | NAND 타입 |
| NAND_SIZE | varchar | NAND 크기 |
| DENSITY | varchar | 밀도 |
| FW_VERSION | varchar | 펌웨어 버전 |
| IS_RELEASED_FW | varchar | 출시 FW 여부 |
| DATE | datetime | 등록일 |
| DESCRIPTION | varchar | 설명 |
| ENGINEER | varchar | 담당 엔지니어 |
| TEST_TYPE | varchar | 테스트 유형 (Aging, function, POR-TC, NPO-TC, SPOR-OCTO, BootingRepeat) |
| FW | varchar | 펌웨어 |

### CompatibilityTestCase

호환성 테스트 케이스.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| ID | bigint (PK, AUTO) | 고유 ID |
| NAME | varchar | TC 이름 |
| FILE_NAME | varchar | 파일명 |
| TC_OPTION | varchar | TC 옵션 |
| VERSION | varchar | 버전 |
| TYPE | varchar | TC 타입 (APK, Binary, Dummy) |
| AUTHOR | varchar | 작성자 |
| DATE | datetime | 등록일 |
| HIDDEN | varchar | 숨김 여부 |
| BELONG_TO | varchar | 소속 (OFK, STF, ETC) |
| DENSITY | varchar | 밀도 |
| SPECIFIC | varchar | 특정 조건 |

### CompatibilityHistory

호환성 테스트 실행 이력.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| ID | bigint (PK) | 고유 ID (외부 할당) |
| RESULT | varchar | 테스트 결과 |
| FAIL_CAUSE | varchar | 실패 원인 |
| START_TIME | datetime | 시작 시간 |
| END_TIME | datetime | 종료 시간 |
| RUNNING_TIME | varchar | 실행 시간 |
| SET_SERIAL | varchar | Set 시리얼 (스냅샷) |
| SET_PRODUCT_NAME | varchar | Set 제품명 (스냅샷) |
| SET_MODEL_NAME | varchar | Set 모델명 (스냅샷) |
| SET_DEVICE_NAME | varchar | Set 디바이스명 (스냅샷) |
| TENTACLE_IP | varchar | Tentacle IP |
| USB_ID | varchar | USB ID |
| LOG_PATH | varchar | 로그 경로 |
| TC_ID | bigint (FK) | → CompatibilityTestCase.ID |
| TR_ID | bigint (FK) | → CompatibilityTestRequest.ID |
| OPTIONAL | varchar | 선택 정보 |
| SLOT_LOCATION | varchar | 슬롯 위치 |
| TEST_TYPE | varchar | 테스트 타입 |
| POR_COUNT | bigint | POR 카운트 |
| TEST_EXPECT_FINISH_TIME | bigint | 예상 완료 시간 |
| DSPLM_UPLOAD | varchar | DSPLM 업로드 |
| POWER_ON_MIN | bigint | 전원 ON 최소 |
| POWER_ON_MAX | bigint | 전원 ON 최대 |
| POWER_OFF_MIN | bigint | 전원 OFF 최소 |
| POWER_OFF_MAX | bigint | 전원 OFF 최대 |

### PerformanceTestRequest

성능 테스트 요청 정보.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| ID | bigint (PK, AUTO) | 고유 ID |
| CONTROLLER | varchar | 컨트롤러 |
| SPEC_VERSION | varchar | UFS 스펙 버전 |
| CELL_TYPE | varchar | 셀 타입 |
| NAND_TYPE | varchar | NAND 타입 |
| NAND_SIZE | varchar | NAND 크기 |
| DENSITY | varchar | 밀도 |
| FW_VERSION | varchar | 펌웨어 버전 |
| BASE_FW_VERSION | varchar | 기준 FW 버전 |
| OEM | varchar | OEM 제조사 |
| DATE | datetime | 등록일 |
| FW | varchar | 펌웨어 |

### PerformanceTestCase

성능 테스트 케이스.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| ID | bigint (PK, AUTO) | 고유 ID |
| NAME | varchar | TC 이름 |
| FILE_NAME | varchar | 파일명 |
| PARSER_ID | varchar | 파서 ID |
| AUTHOR | varchar | 작성자 |
| DATE | datetime | 등록일 |
| HIDDEN | varchar | 숨김 여부 |
| CATEGORY | varchar | 카테고리 |
| IO_TYPE | varchar | IO 타입 |
| TC_OPTION | varchar | TC 옵션 |

### PerformanceHistory

성능 테스트 실행 이력.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| ID | bigint (PK, AUTO) | 고유 ID |
| START_TIME | varchar | 시작 시간 |
| END_TIME | varchar | 종료 시간 |
| RUNNING_TIME | varchar | 실행 시간 |
| SET_ID | varchar | Set ID (→ SetInfomation 참조) |
| LOG_PATH | varchar | 로그 경로 |
| TC_ID | varchar (FK) | → PerformanceTestCase.ID |
| TR_ID | varchar (FK) | → PerformanceTestRequest.ID |
| SLOT_LOCATION | varchar | 슬롯 위치 |
| UPLOADED | varchar | 업로드 여부 |
| RESULT | varchar | 테스트 결과 |

:::caution
`TC_ID`, `TR_ID`는 varchar이지만 실제 값은 숫자 ID입니다. 레거시 스키마 제약으로 타입 변경이 불가합니다.
:::

### PerformanceParser

성능 파서 정보. TC 결과 데이터의 시각화 방식을 결정합니다.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| ID | bigint (PK) | 파서 ID |
| NAME | varchar | 파서 이름 |

### SetInfomation

디바이스 Set 정보.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| ID | bigint (PK, AUTO) | 고유 ID |
| NUMBER | varchar | Set 번호 |
| SERIAL | varchar | 시리얼 번호 |
| PRODUCT_NAME | varchar | 제품명 |
| MODEL_NAME | varchar | 모델명 |
| DEVICE_NAME | varchar | 디바이스명 |
| PUSH_PATH | varchar | 푸시 경로 |
| OS_VERSION | varchar | OS 버전 |
| KERNEL_VERSION | varchar | 커널 버전 |
| VENDOR_COMMAND | varchar | 벤더 명령어 |
| POWER_LEVEL_MIN | varchar | 최소 전원 레벨 |
| POWER_BUTTON_CLICK_TIME | varchar | 전원 버튼 클릭 시간 |
| BATTERY_OFF_STAY_TIME | varchar | 배터리 OFF 대기 시간 |
| BOOTING_TIME | varchar | 부팅 시간 |

### SlotInfomation

슬롯 정보. **복합 Primary Key** 사용.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| TENTACLE_NAME | varchar (PK) | Tentacle 이름 (T1-0 등) |
| SLOT_NUMBER | int (PK) | 슬롯 번호 |
| TESTREQUEST_ID | int | TestRequest ID 참조 |
| TESTCASE_IDS | varchar | TC ID 목록 (`/` 구분) |
| TESTHISTORY_IDS | varchar | History ID 목록 (`/` 구분) |

:::note
DB 테이블에 PK가 없고 ID 컬럼은 항상 null입니다. JPA에서 `@IdClass(SlotInfomationId.class)`로 복합키를 구현합니다.
:::

---

## UFSInfo 데이터베이스

모든 테이블은 동일한 구조: `ID` (PK, AUTO) + `NAME` (varchar, NOT NULL)

| 테이블 | 설명 | 예시 값 |
|--------|------|---------|
| CellType | 셀 타입 | SLC, MLC, TLC, QLC |
| Controller | 컨트롤러 이름 | - |
| Density | 밀도 값 | - |
| NandSize | NAND 크기 | - |
| NandType | NAND 타입 | - |
| OEM | OEM 제조사 | - |
| UfsVersion | UFS 스펙 버전 | - |

---

## binmapper 데이터베이스

### portal_users

사용자 계정 및 권한 관리.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| username | varchar(100) | 로그인 ID (UNIQUE, NOT NULL) |
| password | varchar(255) | BCrypt 해시 (NOT NULL) |
| display_name | varchar(100) | 표시 이름 |
| role | varchar(20) | 역할: `ADMIN` 또는 `USER` (NOT NULL, DEFAULT 'USER') |
| enabled | boolean | 활성화 여부 (NOT NULL, DEFAULT TRUE) |
| created_at | datetime | 생성 시간 |
| updated_at | datetime | 수정 시간 |

### tc_groups

TC 그룹 정의. SetTC 시 자주 사용하는 TC 조합을 저장.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| name | varchar(255) | 그룹 이름 |
| tc_type | varchar(20) | 타입 (`compatibility` 또는 `performance`) |
| description | varchar(500) | 설명 (선택) |
| created_at | datetime | 생성 시간 |
| updated_at | datetime | 수정 시간 |

- **UK**: `(name, tc_type)`

### tc_group_items

TC 그룹에 속한 TC 목록.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| group_id | bigint (FK) | → tc_groups.id (CASCADE DELETE) |
| tc_id | bigint | TC ID |
| sort_order | int | 정렬 순서 |

- **FK**: `group_id` → `tc_groups(id)` ON DELETE CASCADE
- **UK**: `(group_id, tc_id)`

### makeset_groups

MakeSet 그룹 정의. 다중 보드 배치 MakeSet 설정을 저장.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| name | varchar(255) | 그룹 이름 |
| description | varchar(500) | 설명 (선택) |
| created_at | datetime | 생성 시간 |
| updated_at | datetime | 수정 시간 |

### makeset_group_items

MakeSet 그룹에 속한 보드별 설정.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| group_id | bigint (FK) | → makeset_groups.id (CASCADE DELETE) |
| board | varchar(255) | 보드 이름 (슬롯 매칭 키) |
| provision_path | varchar(1000) | Provision XML 경로 |
| image_path | varchar(1000) | Image 폴더 경로 |
| dd_value | varchar(100) | auto_dd 값 (기본: "none") |
| sort_order | int | 정렬 순서 |

### predefined_structs

BinMapper 사전 정의 구조체.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| name | varchar(255) | 구조체 이름 |
| category | varchar(255) | 카테고리 |
| structText | text | 구조체 텍스트 |
| description | varchar(255) | 설명 |
| createdAt | datetime | 생성 시간 |
| updatedAt | datetime | 수정 시간 |

### portal_servers

서버(VM) 관리. Guacamole 원격 접속 대상 정보.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| name | varchar(100) | 서버 이름 (UNIQUE, NOT NULL) — e.g. T1, T2, HEAD |
| ip | varchar(100) | IP 주소 (NOT NULL) |
| username | varchar(100) | SSH/RDP 접속 계정 |
| password | varchar(255) | SSH/RDP 접속 비밀번호 |
| ssh_port | int | SSH 포트 (DEFAULT 22) |
| rdp_port | int | RDP 포트 (DEFAULT 3389) |
| connection_type | tinyint | 연결 타입 (NOT NULL, DEFAULT 0) |
| is_visible | boolean | 목록 노출 여부 (NOT NULL, DEFAULT TRUE) |
| created_at | datetime | 생성 시간 |
| updated_at | datetime | 수정 시간 |

연결 타입: `0` = 원격 접속 불가, `1` = SSH만, `2` = RDP만, `3` = SSH + RDP 모두

### portal_head_connections

Head 서버 TCP 연결 관리.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| name | varchar(100) | 연결 이름 (UNIQUE, NOT NULL) — e.g. compatibility, performance |
| host | varchar(100) | Head 서버 IP (NOT NULL) |
| port_suffix | varchar(10) | 명령 포트 suffix (NOT NULL) — 실제 포트 = 10000 + suffix |
| listen_port_suffix | varchar(10) | 수신 포트 suffix (NOT NULL) — 실제 포트 = 10000 + suffix |
| enabled | boolean | 활성화 여부 (NOT NULL, DEFAULT TRUE) |
| test_mode | boolean | 테스트 모드 (NOT NULL, DEFAULT FALSE) |
| created_at | datetime | 생성 시간 |
| updated_at | datetime | 수정 시간 |

### portal_bucket_visibility

MinIO 버킷 가시성 관리.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | bigint (PK, AUTO) | 고유 ID |
| bucket_name | varchar(255) | 버킷 이름 (UNIQUE, NOT NULL) |
| is_visible | boolean | 가시성 여부 (NOT NULL, DEFAULT TRUE) |

---

## 관계 다이어그램

```
testdb:

CompatibilityTestRequest (1) <--@ManyToOne-- (N) CompatibilityHistory (N) --@ManyToOne--> (1) CompatibilityTestCase
                                                    |
                                                    +-- RESULT (테스트 결과)
                                                    +-- SET_* (스냅샷, FK 아님)

PerformanceTestRequest (1) <--@ManyToOne-- (N) PerformanceHistory (N) --@ManyToOne--> (1) PerformanceTestCase
                                                    |                                      |
                                                    +-- RESULT (테스트 결과)                 +-- PARSER_ID -> PerformanceParser
                                                    +-- SET_ID -> SetInfomation (논리적 참조)

SlotInfomation --> TestRequest (TESTREQUEST_ID, 사용자 판단)
               --> TestCase (TESTCASE_IDs, / 구분)
               --> History (TESTHISTORY_IDs, / 구분)


binmapper:

portal_users (독립)
portal_servers (독립)
portal_head_connections (독립)
portal_bucket_visibility (독립)

tc_groups (1) ---- (N) tc_group_items
  |                    |
  +-- name, tc_type    +-- tc_id (-> testdb TC ID, 논리적 참조)
  +-- description      +-- sort_order

makeset_groups (1) ---- (N) makeset_group_items
  |                         |
  +-- name                  +-- board (슬롯 매칭 키)
  +-- description           +-- provision_path, image_path
                            +-- dd_value, sort_order

predefined_structs (독립)
```

## 크로스 DB 관계

testdb와 UFSInfo 간에는 JPA 연관관계가 아닌 **논리적 이름 매칭**으로 참조합니다.

| TestRequest 컬럼 | UFSInfo 테이블 | 매칭 기준 |
|---|---|---|
| CONTROLLER | Controller | NAME 값 일치 |
| CELL_TYPE | CellType | NAME 값 일치 |
| NAND_TYPE | NandType | NAME 값 일치 |
| NAND_SIZE | NandSize | NAME 값 일치 |
| DENSITY | Density | NAME 값 일치 |
| SPEC_VERSION | UFSVersion | NAME 값 일치 |
| OEM (성능 전용) | OEM | NAME 값 일치 |

:::caution
서로 다른 DB이므로 FK 제약이나 JPA 연관관계를 설정할 수 없습니다. 프론트엔드에서 UFSInfo 데이터를 조회하여 드롭다운으로 제공하고, TestRequest 생성 시 선택된 NAME 값을 저장합니다.
:::
