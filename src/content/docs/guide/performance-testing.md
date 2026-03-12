---
title: 성능 테스트
description: 성능 테스트의 전체 워크플로우 — TR 생성부터 결과 시각화까지
---

성능 테스트는 UFS 장치의 읽기/쓰기 성능, 마모도 등을 측정하고 결과를 차트로 시각화하는 기능입니다.

## 워크플로우

```
TR 생성 → TC 등록 → 슬롯에 할당 → 테스트 실행 → 결과 확인
```

1. **Test Request (TR) 생성** — 테스트 대상 FW 정보와 메타데이터 등록
2. **Test Case (TC) 등록** — TR에 속한 개별 테스트 항목 등록, 파서 선택
3. **슬롯 할당** — Slots 페이지에서 TR/TC를 슬롯에 배정
4. **테스트 실행** — 슬롯에서 테스트 시작
5. **결과 확인** — History에서 결과 조회 및 차트 시각화

## Test Request (TR) 관리

TR은 테스트의 최상위 단위입니다. 페이지 경로: `/testdb/performance`

| 기능 | 설명 |
|------|------|
| 생성 | FW 정보, 프로젝트, 설명 등 입력 |
| 조회 | 목록에서 필터링/검색 |
| 수정 | 기존 TR 정보 변경 |
| 삭제 | TR 및 하위 TC/History 삭제 |

:::caution
TR을 삭제하면 해당 TR에 속한 모든 TC와 History도 함께 삭제됩니다.
:::

## Test Case (TC) 관리

TC는 TR 하위의 개별 테스트 항목입니다. 페이지 경로: `/testdb/performance/[trId]`

- **파서 선택**: 결과 데이터를 해석할 파서(parserId)를 지정합니다. 파서에 따라 시각화 차트 유형이 결정됩니다.
- **카테고리**: TC를 분류하기 위한 카테고리 지정

## Performance History

테스트 실행 결과가 History로 기록됩니다. 페이지 경로: `/testdb/performance/history/[hisId]`

### 상태별 색상 뱃지

History 목록에서 각 항목의 상태가 색상 뱃지로 표시됩니다. 총 16가지 상태가 있으며, 주요 상태는 다음과 같습니다:

| 상태 | 의미 |
|------|------|
| **PASS** | 테스트 통과 |
| **FAIL** | 테스트 실패 |
| **RUNNING** | 테스트 실행 중 |
| **READY** | 실행 대기 |
| **ERROR** | 오류 발생 |

## 결과 시각화 — 15종 파서별 차트

성능 결과는 parserId에 따라 전용 차트 컴포넌트로 시각화됩니다. 총 15종의 파서가 지원됩니다.

주요 차트 유형:

- **GenPerf** — 범용 성능 차트 (읽기/쓰기 처리량)
- **FragmentWrite** — Fragment Write 패턴 분석
- **WearLeveling** — 마모 분산 시각화

각 차트는 다음 공통 기능을 제공합니다:

- **Cycle/Type 탭**: 여러 사이클이나 타입 간 전환
- **마우스 휠 줌**: `dataZoom` 기능으로 차트 확대/축소
- **DataTable**: 차트 데이터를 테이블로 조회
- **Excel 내보내기**: 데이터를 Excel 파일로 다운로드

:::tip
History 상세 페이지에서 GenPerf 차트를 전체 화면으로 볼 수 있습니다.
:::

## 페이지 경로 요약

| 페이지 | 경로 |
|--------|------|
| TR 목록 | `/testdb/performance` |
| TC 목록 (TR 상세) | `/testdb/performance/[trId]` |
| History 상세 | `/testdb/performance/history/[hisId]` |
