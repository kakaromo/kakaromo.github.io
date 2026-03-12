---
title: MinIO S3 스토리지
description: MinIO S3 호환 스토리지의 아키텍처, 설정, REST API, 프론트엔드 업로드 구현 및 에러 처리를 설명합니다.
---

## 아키텍처

```
브라우저 (SvelteKit SPA)
    |   REST API (/api/minio/*)
Spring Boot (MinioController, MinioUploadController)
    |   MinIO Java SDK
MinIO Server (S3 호환, port 9000)
```

## 설정

### MinioProperties

```java
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private String endpoint = "http://127.0.0.1";
    private int port = 9000;
    private boolean useSsl = false;
    private String accessKey = "admin";
    private String secretKey = "changeme";
}
```

### MinioConfig

```java
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {
    @Bean
    public MinioClient minioClient(MinioProperties props) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint(), props.getPort(), props.isUseSsl())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }
}
```

### application.yaml

```yaml
minio:
  endpoint: http://192.168.1.248
  port: 9000
  use-ssl: false
  access-key: admin
  secret-key: tka123tjd!

spring:
  servlet:
    multipart:
      max-file-size: 1GB
      max-request-size: 1GB
```

## MinioStorageService 비즈니스 로직

### 주요 메서드

```java
// 버킷
List<String> listBuckets()
void createBucket(String bucketName)    // 중복 시 IllegalArgumentException
void deleteBucket(String bucketName)    // 비어있어야 삭제 가능

// 오브젝트
List<Map<String, Object>> listObjects(String bucket, String prefix)
InputStream downloadObject(String bucket, String objectName)
void uploadObject(String bucket, String objectName, InputStream stream, long size, String contentType)
void deleteObject(String bucket, String objectName)

// 폴더
void createFolder(String bucket, String folderPath)  // 0바이트 오브젝트 + trailing "/"
```

:::note
S3는 실제 폴더 개념이 없습니다. 폴더는 trailing `/`가 있는 0바이트 오브젝트로 표현됩니다.
:::

## REST API 엔드포인트

### 버킷 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/minio/buckets` | 버킷 목록 조회 (User: 이름만, Admin: 가시성 포함) |
| POST | `/api/minio/buckets` | 버킷 생성 (Admin) |
| DELETE | `/api/minio/buckets/{name}` | 버킷 삭제 (Admin) |
| PUT | `/api/minio/buckets/{name}/visibility` | 버킷 가시성 설정 (Admin) |

### 오브젝트 관리

| Method | Endpoint | 설명 | 파라미터 |
|--------|----------|------|----------|
| GET | `/api/minio/buckets/{bucket}/objects` | 오브젝트 목록 | `?prefix=folder/` |
| DELETE | `/api/minio/buckets/{bucket}/objects` | 오브젝트 삭제 | `?objectName=file.txt` |
| POST | `/api/minio/buckets/{bucket}/folder` | 폴더 생성 | body: `{folderPath}` |
| GET | `/api/minio/buckets/{bucket}/download` | 파일 다운로드 | `?objectName=file.txt` |
| POST | `/api/minio/buckets/{bucket}/upload` | 파일 업로드 | `?prefix=folder/`, FormData: `file` |

### 오브젝트 목록 응답 형식

```json
[
  {
    "name": "docs/readme.txt",
    "isDir": false,
    "size": 1024,
    "lastModified": "2026-02-28T10:30:00+09:00"
  },
  {
    "name": "images/",
    "isDir": true,
    "size": 0,
    "lastModified": null
  }
]
```

구분자 `/`를 사용하여 계층적 디렉토리 구조를 표현합니다.

## XHR 업로드 구현 (프론트엔드)

`fetch` API는 업로드 진행률을 지원하지 않으므로 `XMLHttpRequest`를 사용합니다:

```typescript
export function uploadWithProgress(
    bucket: string,
    prefix: string,
    file: File,
    onProgress: (progress: UploadProgress) => void,
    onComplete: () => void,
    onError: (error: string) => void
): () => void {   // 반환값: 업로드 취소 함수
    const xhr = new XMLHttpRequest();
    const url = `/api/minio/buckets/${encodeURIComponent(bucket)}/upload?prefix=...`;

    xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
            onProgress({
                bytesRead: e.loaded,
                totalSize: e.total,
                percent: Math.round((e.loaded / e.total) * 100)
            });
        }
    });

    xhr.open('POST', url);
    xhr.setRequestHeader('X-XSRF-TOKEN', getCsrfToken());
    const formData = new FormData();
    formData.append('file', file);
    xhr.send(formData);

    return () => xhr.abort();  // 취소 함수
}
```

### UI 기능

- **좌측 패널**: 버킷 목록 (생성/삭제/가시성 토글)
  - Admin: 모든 버킷 표시 + 눈 아이콘으로 visible 토글
  - User: visible=true인 버킷만 표시
- **우측 패널**: 파일/폴더 브라우저 (Breadcrumb, 확장자별 아이콘)
- **업로드**: 파일 선택기 + 드래그앤드롭, 다중 파일 배치 업로드, 실시간 프로그레스 바, 업로드 취소 가능
- **체크박스 선택**: 다중 선택 → 일괄 다운로드/삭제

## 에러 처리

| 상황 | 백엔드 응답 | 프론트엔드 처리 |
|------|-------------|----------------|
| 버킷 중복 생성 | 400 + `{error}` | Toast 알림 |
| 존재하지 않는 오브젝트 | 500 + 에러 메시지 | Toast 알림 |
| 업로드 실패 | HTTP 에러 코드 | onError 콜백 → Toast |
| 네트워크 오류 | - | XHR error 이벤트 → Toast |

:::tip
버킷 가시성 정보는 DB `portal_bucket_visibility` 테이블에서 관리되며, 기본값은 visible=true입니다.
:::
