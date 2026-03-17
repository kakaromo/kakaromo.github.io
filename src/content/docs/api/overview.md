---
title: API 개요
description: Samsung Portal REST API의 공통 사항, 인증, CSRF, 에러 응답 구조 및 API 도메인 목록
---

Samsung Portal의 모든 API는 REST 아키텍처를 따르며, JSON 형식으로 요청/응답합니다.

## Base URL

```
http://localhost:8080/api
```

프로덕션 환경에서는 실제 서버 주소로 대체됩니다.

## 인증

OAuth2/OIDC 기반 인증을 사용합니다. 현재 개발 모드에서는 `AUTH_DISABLED = true`로 모든 요청이 허용됩니다.

활성화 시:
- `/api/**` (auth 제외): 인증 필요 (401 반환)
- 정적 파일, SPA 라우트: 공개
- Admin 전용 API (`/api/admin/**`): ADMIN 역할 필요

## CSRF 보호

POST, PUT, DELETE 요청에는 CSRF 토큰이 필요합니다.

1. 서버가 `XSRF-TOKEN` 쿠키를 설정합니다
2. 클라이언트는 해당 값을 `X-XSRF-TOKEN` 헤더에 포함하여 요청합니다

```javascript
// 프론트엔드 client.ts에서 자동 처리
const xsrfToken = getCookie('XSRF-TOKEN');
headers['X-XSRF-TOKEN'] = xsrfToken;
```

## 응답 형식

모든 응답은 `Content-Type: application/json`으로 반환됩니다. 성공 시 HTTP 200과 함께 데이터가 직접 반환됩니다.

### 에러 응답 구조

```json
{
  "error": "에러 메시지",
  "status": 400
}
```

| HTTP 상태 코드 | 의미 |
|----------------|------|
| 400 | 잘못된 요청 (파라미터 오류) |
| 401 | 인증 필요 |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 422 | 처리 불가 (바이너리 파일 등) |
| 500 | 서버 내부 오류 |

## API 도메인 목록

| 도메인 | Prefix | 설명 |
|--------|--------|------|
| **TestDB** | `/api/...` | 호환성/성능 테스트 데이터 CRUD, Dashboard 통계 |
| **Head** | `/api/head/...` | 실시간 슬롯 모니터링 및 명령 전송 |
| **Log Browser** | `/api/log-browser/...` | 원격 로그 파일 탐색/조회/검색 |
| **MinIO** | `/api/minio/...` | S3 호환 오브젝트 스토리지 관리 |
| **BinMapper** | `/api/binmapper/...` | 바이너리 구조체 매핑 도구 |
| **Guacamole** | `/api/guacamole/...` | 원격 접속 (SSH/RDP) |
| **UFSInfo** | `/api/ufsinfo/...` | UFS 참조 데이터 CRUD |
| **MakeSet Group** | `/api/makeset-groups/...` | MakeSet 보드 그룹 관리 |
| **Auth / Admin** | `/api/auth/...`, `/api/admin/...` | 인증 상태 및 관리자 대시보드 |

## Redis 캐싱

모든 `findById` 호출에 Redis 캐시가 적용됩니다.

| 대상 | TTL | 조건 |
|------|-----|------|
| TestDB 엔티티 | 10분 | `null` 결과 제외 |
| UFSInfo 엔티티 | 1시간 | `null` 결과 제외 |

## Multi-DataSource 구조

| DataSource | DB | 패키지 | 설명 |
|------------|-----|--------|------|
| `testdb` (Primary) | testdb | `testdb.*` | 테스트 데이터 |
| `ufsinfo` | UFSInfo | `ufsinfo.*` | UFS 참조 코드 |
| `portal` | binmapper | `binmapper.*`, `tcgroup.*`, `makesetgroup.*`, `auth.*` | 도구/그룹/사용자 데이터 |
