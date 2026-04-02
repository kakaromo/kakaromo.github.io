---
title: TestDB API
description: 호환성/성능 테스트의 TR, TC, History, Parser, Set, Slot CRUD 및 Dashboard 통계 API
---

TestDB API는 테스트 데이터의 CRUD 및 조회를 담당합니다. 모든 엔드포인트는 표준 REST 패턴을 따릅니다.

## Dashboard

### GET `/api/dashboard/stats`

메인 대시보드용 집계 통계를 반환합니다. DB에서 `GROUP BY` 쿼리로 집계된 결과만 전송하여 성능을 최적화합니다.

**응답:**

```json
{
  "compatibility": {
    "trCount": 15,
    "tcCount": 42,
    "totalCount": 300,
    "passCount": 280,
    "failCount": 20,
    "byFw": [
      { "name": "FW_v1.2", "pass": 50, "fail": 3, "total": 53, "rate": 94.3 }
    ],
    "byTc": [
      { "name": "AgingTest", "pass": 30, "fail": 2, "total": 32, "rate": 93.8 }
    ],
    "recent": [
      { "id": 100, "result": "PASS", "trFw": "FW_v1.2", "tcName": "AgingTest" }
    ]
  },
  "performance": { ... }
}
```

| 필드 | 설명 |
|------|------|
| `byFw[]` | TR(FW)별 pass/fail/total/rate (상위 10개) |
| `byTc[]` | TC별 pass/fail/total/rate (상위 10개) |
| `recent[]` | 최근 10건 History |

---

## Compatibility Test Requests

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/compatibility-test-requests` | 전체 목록 |
| GET | `/api/compatibility-test-requests/{id}` | ID로 조회 |
| POST | `/api/compatibility-test-requests` | 생성 |
| PUT | `/api/compatibility-test-requests/{id}` | 수정 |
| DELETE | `/api/compatibility-test-requests/{id}` | 삭제 |

**요청 Body (POST/PUT):**

```json
{
  "fw": "FW_v1.2.3",
  "testType": "Aging",
  "description": "설명"
}
```

:::caution[수정/삭제 제한]
연결된 History가 존재하는 TR은 수정(PUT) 및 삭제(DELETE) 시 `409 Conflict` 응답을 반환합니다.
```json
{ "error": "연결된 History가 존재하여 수정할 수 없습니다. (TR ID: 21)" }
```
Performance TR에도 동일한 제한이 적용됩니다.
:::

## Compatibility Test Cases

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/compatibility-test-cases` | 전체 목록 |
| GET | `/api/compatibility-test-cases/{id}` | ID로 조회 |
| POST | `/api/compatibility-test-cases` | 생성 |
| PUT | `/api/compatibility-test-cases/{id}` | 수정 |
| DELETE | `/api/compatibility-test-cases/{id}` | 삭제 |

## Compatibility Histories

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/compatibility-histories` | 전체 목록 |
| GET | `/api/compatibility-histories/{id}` | ID로 조회 |
| POST | `/api/compatibility-histories` | 생성 |
| PUT | `/api/compatibility-histories/{id}` | 수정 |
| DELETE | `/api/compatibility-histories/{id}` | 삭제 |

---

## Performance Test Requests

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/performance-test-requests` | 전체 목록 |
| GET | `/api/performance-test-requests/{id}` | ID로 조회 |
| POST | `/api/performance-test-requests` | 생성 |
| PUT | `/api/performance-test-requests/{id}` | 수정 |
| DELETE | `/api/performance-test-requests/{id}` | 삭제 |

## Performance Test Cases

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/performance-test-cases` | 전체 목록 |
| GET | `/api/performance-test-cases/{id}` | ID로 조회 |
| POST | `/api/performance-test-cases` | 생성 |
| PUT | `/api/performance-test-cases/{id}` | 수정 |
| DELETE | `/api/performance-test-cases/{id}` | 삭제 |

## Performance Parsers

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/performance-parsers` | 전체 파서 목록 |
| GET | `/api/performance-parsers/{id}` | ID로 조회 |
| POST | `/api/performance-parsers` | 생성 |
| PUT | `/api/performance-parsers/{id}` | 수정 |
| DELETE | `/api/performance-parsers/{id}` | 삭제 |

## Performance Histories

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/performance-histories` | 전체 목록 |
| GET | `/api/performance-histories/{id}` | ID로 조회 |
| POST | `/api/performance-histories` | 생성 |
| PUT | `/api/performance-histories/{id}` | 수정 |
| DELETE | `/api/performance-histories/{id}` | 삭제 |
| GET | `/api/performance-histories/page` | 서버사이드 페이지네이션 |
| GET | `/api/performance-histories/by-tr/{trId}/tc-groups` | TR별 TC 그룹 카운트 |
| GET | `/api/performance-histories/by-tr/{trId}/tc/{tcId}` | TR+TC 필터 페이지네이션 |
| GET | `/api/performance-histories/group-by/{groupBy}` | 그룹별 카운트 |
| GET | `/api/performance-histories/group-by/{groupBy}/{groupValue}` | 그룹 내 페이지네이션 |

### 페이지네이션 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `page` | number | 0 | 페이지 번호 (0-based) |
| `size` | number | 20 | 페이지 크기 |
| `sort` | string | `id,desc` | 정렬 필드 및 방향 |

### 그룹핑 파라미터

| `groupBy` 값 | 설명 |
|---------------|------|
| `tr` | Test Request별 그룹핑 |
| `tc` | Test Case별 그룹핑 |
| `result` | 결과(PASS/FAIL)별 그룹핑 |

---

## Performance Results

### GET `/api/performance-results/{historyId}/data`

성능 테스트 결과 JSON 데이터를 조회합니다.

**응답:**

```json
{
  "parserId": 2,
  "parserName": "readwrite",
  "tcName": "SeqRead_128K",
  "data": {
    "read": [
      { "cycle": 1, "data": [1200, 1250, 1180], "min": 1180, "max": 1250, "avg": 1210 }
    ],
    "write": [...]
  }
}
```

### GET `/api/performance-results/{historyId}/excel`

성능 결과를 네이티브 Excel 차트 포함 `.xlsx` 파일로 다운로드합니다.

**동작 과정:**
1. `PerformanceResultDataService`가 History + TC + Parser + 로그 데이터를 조회
2. `ExcelGrpcClient`가 Go Excel Service(포트 50052)에 gRPC 호출
3. Go 서비스가 네이티브 Excel 차트 포함 `.xlsx` 생성
4. `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 타입으로 다운로드

---

## Set Infomations

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/set-infomations` | 전체 목록 |
| GET | `/api/set-infomations/{id}` | ID로 조회 |
| POST | `/api/set-infomations` | 생성 |
| PUT | `/api/set-infomations/{id}` | 수정 |
| DELETE | `/api/set-infomations/{id}` | 삭제 |

## Slot Infomations

복합키(`tentacleName` + `slotNumber`)를 사용합니다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/slot-infomations` | 전체 목록 |
| GET | `/api/slot-infomations/{tentacleName}/{slotNumber}` | 복합키로 조회 |
| POST | `/api/slot-infomations` | 생성 |
| PUT | `/api/slot-infomations/{tentacleName}/{slotNumber}` | 수정 |
| DELETE | `/api/slot-infomations/{tentacleName}/{slotNumber}` | 삭제 |

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `tentacleName` | string | Tentacle 서버명 (T1, T2, T3, T4) |
| `slotNumber` | number | 슬롯 번호 |

---

## TC Groups

TC 조합을 그룹으로 저장/관리합니다. Slots 페이지의 SetTC에서 사용됩니다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/tc-groups` | 전체 목록 (`?type=performance` 필터 가능) |
| GET | `/api/tc-groups/{id}` | 단건 조회 |
| POST | `/api/tc-groups` | 생성 |
| PUT | `/api/tc-groups/{id}` | 수정 |
| DELETE | `/api/tc-groups/{id}` | 삭제 |

**요청 (POST/PUT):**

```json
{
  "name": "SeqAll",
  "tcType": "performance",
  "description": "모든 Sequential TC",
  "tcIds": [1, 2, 3, 5, 8]
}
```

**응답 (GET):**

```json
{
  "id": 1,
  "name": "SeqAll",
  "tcType": "performance",
  "description": "모든 Sequential TC",
  "items": [
    { "id": 1, "tcId": 1, "sortOrder": 0 },
    { "id": 2, "tcId": 2, "sortOrder": 1 }
  ],
  "createdAt": "2026-03-06T08:30:00",
  "updatedAt": "2026-03-06T08:30:00"
}
```

---

## Reparse (재파싱)

성능 테스트 결과를 원본 로그에서 다시 파싱합니다. SSH로 원격 서버에 접속하여 `parsingcontroller`를 실행하며, 백그라운드로 동작합니다.

### POST `/api/reparse/{historyId}`

재파싱을 시작합니다.

**응답 (200):**

```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "historyId": 123,
  "tcId": 45,
  "tentacleName": "T10",
  "state": "preparing",
  "totalFiles": 0,
  "currentIndex": 0,
  "currentFileName": "",
  "error": "",
  "startedAt": 1712150400000,
  "updatedAt": 1712150400000
}
```

**에러 응답:**

| 코드 | 상황 |
|------|------|
| 400 | logPath 없음, RUNNING 상태, TC/Parser 없음 |
| 409 | 이미 재파싱 진행 중 |

### GET `/api/reparse/jobs`

모든 활성 재파싱 작업 목록을 반환합니다. 완료된 작업은 10분 후 자동 정리됩니다.

### GET `/api/reparse/jobs/{jobId}`

특정 재파싱 작업의 상태를 조회합니다.

### GET `/api/reparse/stream` (SSE)

실시간 재파싱 진행상황을 Server-Sent Events로 스트리밍합니다.

**이벤트:**

| 이벤트 | 데이터 | 설명 |
|--------|--------|------|
| `init` | `{ jobs: [...] }` | 연결 시 전체 작업 목록 |
| `update` | `{ jobs: [...] }` | 1초 주기 변경사항 push |

**Job 상태:**

| state | 설명 |
|-------|------|
| `preparing` | SSH 연결 및 파일 탐색 중 |
| `running` | parsingcontroller 실행 중 |
| `completed` | 재파싱 완료 |
| `failed` | 오류 발생 |
