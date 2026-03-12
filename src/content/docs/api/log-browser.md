---
title: Log Browser API
description: 원격 Tentacle 서버의 로그 파일 탐색, 조회, 다운로드, 검색 API
---

Log Browser API는 원격 Tentacle 서버(T1~T4)의 로그 파일을 탐색하고 조회하는 기능을 제공합니다. `tentacle.access-mode` 설정에 따라 SSH 또는 로컬 파일시스템 모드로 동작합니다.

## 디렉토리 목록

### GET `/api/log-browser/files`

지정된 경로의 파일/디렉토리 목록을 반환합니다.

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `tentacleName` | string | 필수 | Tentacle 서버명 (T1, T2, T3, T4) |
| `path` | string | 필수 | 디렉토리 경로 |

**응답:**

```json
[
  {
    "name": "test.log",
    "directory": false,
    "size": 1024,
    "lastModified": "2026-02-25T12:00:00"
  },
  {
    "name": "subdir",
    "directory": true,
    "size": 0,
    "lastModified": "2026-02-25T11:00:00"
  }
]
```

---

## 파일 다운로드

### GET `/api/log-browser/download`

파일을 바이너리 스트림으로 다운로드합니다.

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `tentacleName` | string | 필수 | Tentacle 서버명 |
| `path` | string | 필수 | 파일 경로 |

**응답**: `application/octet-stream` 바이너리 스트림

---

## 라인 범위 읽기

### GET `/api/log-browser/view/lines`

파일의 특정 라인 범위를 읽어 반환합니다.

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `tentacleName` | string | - | Tentacle 서버명 |
| `path` | string | - | 파일 경로 |
| `startLine` | number | 1 | 시작 라인 번호 |
| `limit` | number | 1000 | 읽을 라인 수 (최대 5000) |
| `force` | boolean | false | 바이너리 파일 강제 열기 (NUL 바이트 제거) |

**응답:**

```json
{
  "content": "line1\nline2\nline3\n...",
  "startLine": 1,
  "totalLines": 50000
}
```

**바이너리 파일 에러 (HTTP 422):**

```json
{
  "error": "Binary file cannot be viewed",
  "binary": true
}
```

:::note
`force=true`로 요청하면 바이너리 파일도 NUL 바이트를 제거한 후 텍스트로 반환합니다. non-UTF-8 인코딩은 자동 감지 후 UTF-8로 변환됩니다.
:::

---

## 파일 끝 읽기

### GET `/api/log-browser/view/last`

파일의 마지막 N줄을 반환합니다.

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `tentacleName` | string | - | Tentacle 서버명 |
| `path` | string | - | 파일 경로 |
| `lines` | number | 2000 | 읽을 마지막 줄 수 (최대 10000) |

**응답**: `/view/lines`와 동일한 구조

---

## 파일 검색

### GET `/api/log-browser/view/search`

`rg` (ripgrep) 패턴으로 파일 내용을 검색합니다.

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `tentacleName` | string | - | Tentacle 서버명 |
| `path` | string | - | 파일 경로 |
| `pattern` | string | - | 검색 패턴 (정규식) |
| `maxResults` | number | 500 | 최대 결과 수 |

**응답:**

```json
[
  { "lineNumber": 42, "text": "ERROR: connection timeout" },
  { "lineNumber": 108, "text": "ERROR: device not found" }
]
```
