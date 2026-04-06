---
title: Bitbucket API
description: Bitbucket 브랜치 모니터 REST API 레퍼런스
---

Base path: `/api/bitbucket`

모든 요청/응답은 JSON (`Content-Type: application/json`).

---

## Repository CRUD

| Method | Path | 설명 |
|--------|------|------|
| GET | `/repos` | 감시 대상 저장소 목록 조회 |
| POST | `/repos` | 저장소 추가 |
| PUT | `/repos/{id}` | 저장소 수정 |
| DELETE | `/repos/{id}` | 저장소 삭제 |

### 요청 본문 (POST/PUT `/repos`)

```json
{
  "name": "FW Main Repo",
  "serverUrl": "https://bitbucket.mycompany.com",
  "projectKey": "FW",
  "repoSlug": "firmware-code",
  "pat": "NzM2...",
  "controller": "Savona",
  "targetPath": "/appdata/samsung/OCTO_HEAD/FW_Code",
  "autoDownload": true,
  "enabled": true
}
```

### 응답 (저장소 객체)

```json
{
  "id": 1,
  "name": "FW Main Repo",
  "serverUrl": "https://bitbucket.mycompany.com",
  "projectKey": "FW",
  "repoSlug": "firmware-code",
  "pat": "NzM2...",
  "controller": "Savona",
  "targetPath": "/appdata/samsung/OCTO_HEAD/FW_Code",
  "autoDownload": true,
  "enabled": true,
  "createdAt": "2026-04-04T10:00:00",
  "updatedAt": "2026-04-04T10:00:00",
  "lastPolledAt": "2026-04-04T10:05:00"
}
```

---

## Branch History

| Method | Path | 설명 |
|--------|------|------|
| GET | `/repos/{id}/branches` | 다운로드된 브랜치 이력 (최신순) |

### 응답 (브랜치 배열)

```json
[
  {
    "id": 1,
    "repoId": 1,
    "branchName": "feature/new-function",
    "latestCommitId": "a1b2c3d4e5f6",
    "status": "DOWNLOADED",
    "filePath": "/mnt/head/FW_Code/SERRA/feature_new-function",
    "fileSizeBytes": 2048576,
    "commitDate": "2026-04-01T14:30:00",
    "downloadedAt": "2026-04-04T10:05:30",
    "errorMessage": null
  }
]
```

### status 값

| 값 | 설명 |
|----|------|
| `DETECTED` | 감지됨 (autoDownload=false 또는 파일 삭제 후) |
| `DOWNLOADING` | 다운로드 진행 중 |
| `DOWNLOADED` | 다운로드 + 압축 해제 완료 |
| `FAILED` | 실패 (`errorMessage`에 원인 기록) |

---

## Actions

### 수동 폴링

```
POST /repos/{id}/poll
```

해당 저장소의 브랜치 목록을 즉시 조회하고, 신규 브랜치가 있으면 다운로드합니다.

**요청 본문**: `{}` (빈 객체)

**응답**:
```json
{
  "message": "폴링 완료",
  "downloaded": 2
}
```

### 수동 다운로드

```
POST /repos/{id}/download?branch={branchName}
```

특정 브랜치를 즉시 다운로드합니다. 이미 다운로드된 브랜치도 재다운로드 가능합니다.

**파라미터**:
- `branch` (query, required): 브랜치명 (예: `feature/my-branch`)

**요청 본문**: `{}` (빈 객체)

**응답**: BitbucketBranch 객체 (위 브랜치 이력 항목과 동일)

### 연결 테스트 (저장소 기반)

```
POST /repos/{id}/test
```

저장소의 Bitbucket API 연결을 테스트합니다. PAT 유효성과 저장소 접근 권한을 확인합니다.

**요청 본문**: `{}` (빈 객체)

**응답**:
```json
{
  "result": "OK: 15개 브랜치 발견"
}
```

실패 시:
```json
{
  "result": "FAIL: Bitbucket API 응답 오류: 401 - Unauthorized"
}
```

### 연결 테스트 (저장 전 검증)

```
POST /test-connection
```

저장소를 DB에 저장하기 전에 연결을 테스트합니다. 성공 시 브랜치 목록과 커밋 정보를 반환합니다.

**요청 본문**:
```json
{
  "serverUrl": "https://bitbucket.mycompany.com",
  "projectKey": "FW",
  "repoSlug": "firmware-code",
  "pat": "NzM2..."
}
```

**응답**:
```json
{
  "success": true,
  "message": "15개 브랜치 발견",
  "branches": [
    { "name": "feature/new-func", "commitId": "a1b2c3d", "timestamp": "2026-04-01T14:30:00" }
  ]
}
```

---

## Branch Actions

### DETECTED 브랜치 다운로드 (SSE)

```
POST /branches/{branchId}/download
→ text/event-stream
```

DETECTED 또는 FAILED 상태의 브랜치를 SSE 스트리밍으로 다운로드합니다. 진행률을 실시간으로 표시합니다.

**SSE 이벤트**:

| 이벤트 | 데이터 | 설명 |
|--------|--------|------|
| `download-progress` | `{bytes, mb}` | 1MB마다 진행률 |
| `download-done` | `{bytes}` | 다운로드 완료 |
| `extract-start` | `{}` | 압축 해제 시작 |
| `extract-done` | `{}` | 압축 해제 완료 |
| `done` | `{branchId, status}` | 전체 완료 |
| `error` | `{message}` | 오류 |

### 브랜치 파일 삭제

```
POST /branches/{branchId}/delete-files
```

다운로드된 브랜치의 파일 (ZIP + 폴더)을 삭제합니다. DB 기록은 유지되며 상태가 `DETECTED`로 변경됩니다.

**응답**:
```json
{
  "success": true,
  "message": "파일 삭제 완료"
}
```

상태 변경: `DOWNLOADED` → `DETECTED` (`downloadedAt=null`, `filePath=null`)

### 브랜치 DB 기록 삭제

```
DELETE /branches/{branchId}
```

브랜치의 DB 기록을 완전히 삭제합니다.

**응답**:
```json
{
  "success": true,
  "message": "브랜치 기록 삭제 완료"
}
```

---

## 에러 응답

| 상태 코드 | 설명 |
|-----------|------|
| 404 | 해당 ID의 저장소가 존재하지 않음 |
| 500 | 서버 내부 오류 (Bitbucket 연결 실패 등) |

---

## Bitbucket Server REST API 참고

Portal이 내부적으로 호출하는 Bitbucket Server API:

| 용도 | Bitbucket API |
|------|--------------|
| 브랜치 목록 | `GET /rest/api/latest/projects/{proj}/repos/{repo}/branches?start={n}&limit=100` |
| ZIP 다운로드 | `GET /rest/api/latest/projects/{proj}/repos/{repo}/archive?at=refs/heads/{branch}&format=zip` |

인증은 `Authorization: Bearer {PAT}` 헤더를 사용합니다. 브랜치 목록은 페이지네이션(`isLastPage`, `nextPageStart`)을 처리합니다.
