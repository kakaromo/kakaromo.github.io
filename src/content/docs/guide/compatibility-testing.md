---
title: 호환성 테스트
description: 호환성 테스트의 TR/TC/History 관리와 다양한 테스트 타입 안내
---

호환성 테스트는 UFS 장치의 다양한 호스트 환경과의 호환성을 검증하는 기능입니다. 페이지 경로: `/testdb/compatibility`

## 워크플로우

성능 테스트와 동일한 3계층 구조를 따릅니다:

```
Test Request (TR) → Test Case (TC) → History
```

1. TR을 생성하여 테스트 대상과 조건을 정의합니다.
2. TR 하위에 TC를 등록하여 개별 테스트 항목을 구성합니다.
3. 테스트 실행 후 History에서 결과를 확인합니다.

## 3개 탭 전환

호환성 테스트 페이지는 **TR**, **TC**, **History** 세 개의 탭으로 구성되어 있습니다. 탭을 클릭하여 각 계층의 데이터를 전환할 수 있습니다.

## TR (Test Request)

| 필드 | 설명 |
|------|------|
| FW Version | 펌웨어 버전 |
| Project | 프로젝트명 |
| Description | 테스트 설명 |
| UFS 정보 | UFS 참조 데이터 (Controller, Density 등) |

## TC (Test Case)

| 필드 | 설명 |
|------|------|
| TC Name | 테스트 케이스 이름 |
| Test Type | 테스트 유형 (아래 참조) |
| Category | 분류 카테고리 |
| Expected Result | 기대 결과 |

## History

| 필드 | 설명 |
|------|------|
| Status | 테스트 상태 (PASS/FAIL 등) |
| Slot | 실행된 슬롯 |
| Start/End Time | 실행 시작/종료 시각 |
| Log Path | 로그 파일 경로 |

## 테스트 타입

호환성 테스트는 다음 6가지 테스트 타입을 지원합니다:

| 타입 | 설명 |
|------|------|
| **Aging** | 장시간 반복 테스트 |
| **function** | 기능 검증 테스트 |
| **POR-TC** | Power-On Reset 테스트 |
| **NPO-TC** | Non-Power-Off 테스트 |
| **SPOR-OCTO** | Sudden Power-Off Recovery (OCTO) |
| **BootingRepeat** | 부팅 반복 테스트 |

## 결과 확인 및 필터링

History 탭에서 결과를 확인할 수 있습니다. 상단의 필터를 사용하여 특정 조건의 결과만 조회할 수 있습니다:

- 상태별 필터 (PASS, FAIL 등)
- FW 버전별 필터
- 날짜 범위 필터
- TC 이름 검색

:::tip
호환성 테스트와 성능 테스트는 동일한 슬롯 인프라를 공유합니다. 슬롯 할당은 [실시간 슬롯 모니터링](/guide/slot-monitoring/) 페이지에서 수행합니다.
:::
