---
title: MinIO API
description: S3 호환 오브젝트 스토리지의 버킷 관리, 파일 업로드/다운로드, 폴더 생성, 가시성 설정 API
---

MinIO API는 S3 호환 오브젝트 스토리지를 관리합니다. 버킷 CRUD, 파일 업로드/다운로드, 폴더 생성, 버킷 가시성 제어 기능을 제공합니다.

## 버킷 관리

### GET `/api/minio/buckets`

버킷 목록을 반환합니다.

- **Admin**: 모든 버킷 + `visible` 정보
- **일반 사용자**: `visible=true`인 버킷만

**응답:**

```json
[
  { "name": "test-results", "creationDate": "2026-01-15T10:00:00", "visible": true },
  { "name": "internal-logs", "creationDate": "2026-01-10T08:00:00", "visible": false }
]
```

### POST `/api/minio/buckets`

새 버킷을 생성합니다. (Admin 전용)

**요청 Body:**

```json
{ "name": "new-bucket" }
```

### DELETE `/api/minio/buckets/{name}`

버킷을 삭제합니다. (Admin 전용) 가시성 레코드도 함께 삭제됩니다.

| 파라미터 | 설명 |
|----------|------|
| `name` | 버킷 이름 |

### PUT `/api/minio/buckets/{name}/visibility`

버킷의 가시성을 설정합니다. (Admin 전용)

**요청 Body:**

```json
{ "visible": true }
```

---

## 오브젝트 관리

### GET `/api/minio/buckets/{bucket}/objects`

버킷 내 오브젝트 목록을 반환합니다.

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `bucket` | string (path) | 버킷 이름 |
| `prefix` | string (query) | 폴더 경로 필터 (예: `folder/subfolder/`) |

**응답:**

```json
[
  { "name": "file.txt", "size": 1024, "lastModified": "2026-03-01T09:00:00", "isDir": false },
  { "name": "subfolder/", "size": 0, "lastModified": null, "isDir": true }
]
```

### GET `/api/minio/buckets/{bucket}/download`

파일을 다운로드합니다.

| 파라미터 | 설명 |
|----------|------|
| `bucket` | 버킷 이름 |
| `objectName` | 오브젝트 경로 (query) |

### GET `/api/minio/buckets/{bucket}/objects/recursive`

폴더 내 모든 파일을 재귀적으로 나열합니다. 폴더 다운로드 시 사용됩니다.

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `bucket` | string (path) | 버킷 이름 |
| `prefix` | string (query) | 폴더 경로 (예: `folder/subfolder/`) |

**응답:**

```json
[
  { "name": "folder/file1.txt", "size": 1024, "lastModified": "2026-03-01T09:00:00" },
  { "name": "folder/sub/file2.txt", "size": 2048, "lastModified": "2026-03-01T10:00:00" }
]
```

:::note
디렉토리 마커(빈 object)는 결과에서 제외되며, 실제 파일만 반환됩니다.
:::

### DELETE `/api/minio/buckets/{bucket}/objects`

오브젝트를 삭제합니다.

| 파라미터 | 설명 |
|----------|------|
| `bucket` | 버킷 이름 |
| `objectName` | 삭제할 오브젝트 경로 (query) |

---

## 파일 업로드

### POST `/api/minio/buckets/{bucket}/upload`

파일을 업로드합니다. `multipart/form-data` 형식입니다.

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `bucket` | string (path) | 버킷 이름 |
| `prefix` | string (query) | 업로드 대상 폴더 경로 |
| `file` | FormData | 업로드할 파일 |

프론트엔드에서는 XHR 기반 업로드로 프로그레스 바와 취소 기능을 지원하며, 다중 파일 배치 업로드가 가능합니다.

:::caution
**2GB 초과 파일은 압축 형식만 허용됩니다.** 파일 크기가 2GB를 초과하는 경우, `.zip`, `.gz`, `.tar`, `.7z`, `.rar` 등 압축 포맷으로 압축한 파일만 업로드할 수 있습니다. 비압축 파일이 2GB를 초과하면 서버에서 요청을 거부합니다. 업로드 한도는 최대 1TB입니다.
:::

---

## 폴더 생성

### POST `/api/minio/buckets/{bucket}/folder`

빈 폴더를 생성합니다.

**요청 Body:**

```json
{ "folderPath": "new-folder/" }
```
