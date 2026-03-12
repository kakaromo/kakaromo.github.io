---
title: UFSInfo API
description: UFS 참조 데이터(셀 타입, 컨트롤러, 밀도, NAND 크기/타입, OEM, UFS 버전) CRUD API
---

UFSInfo API는 UFS(Universal Flash Storage) 관련 참조 데이터를 관리합니다. 7개 리소스 모두 동일한 CRUD 패턴을 따르며, `id` + `name` 구조입니다.

## 공통 CRUD 패턴

각 리소스에 대해 동일한 5개 엔드포인트가 제공됩니다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/ufsinfo/{resource}` | 전체 목록 |
| GET | `/api/ufsinfo/{resource}/{id}` | ID로 조회 |
| POST | `/api/ufsinfo/{resource}` | 생성 |
| PUT | `/api/ufsinfo/{resource}/{id}` | 수정 |
| DELETE | `/api/ufsinfo/{resource}/{id}` | 삭제 |

## 리소스 목록

| `{resource}` | 설명 | 예시 값 |
|--------------|------|---------|
| `cell-types` | 셀 타입 | SLC, MLC, TLC, QLC |
| `controllers` | 컨트롤러 이름 | - |
| `densities` | 밀도 값 | - |
| `nand-sizes` | NAND 크기 | - |
| `nand-types` | NAND 타입 | - |
| `oems` | OEM 제조사 | Samsung, SK Hynix, Micron |
| `ufs-versions` | UFS 스펙 버전 | UFS 3.1, UFS 4.0 |

## 요청/응답 예시

**요청 (POST/PUT):**

```json
{
  "name": "TLC"
}
```

**응답 (GET 목록):**

```json
[
  { "id": 1, "name": "SLC" },
  { "id": 2, "name": "MLC" },
  { "id": 3, "name": "TLC" },
  { "id": 4, "name": "QLC" }
]
```

**응답 (GET 단건):**

```json
{
  "id": 3,
  "name": "TLC"
}
```

## 캐싱

UFSInfo 엔티티는 Redis에 1시간 TTL로 캐싱됩니다. `null` 결과는 캐시하지 않습니다.
