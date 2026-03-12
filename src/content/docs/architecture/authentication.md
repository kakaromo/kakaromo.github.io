---
title: 인증 (OAuth2/OIDC)
description: Samsung Galaxy SSO(ADFS)를 통한 OAuth2/OIDC 인증 아키텍처, 플로우, CSRF 보호, 프론트엔드 구현을 설명합니다.
---

## 아키텍처

```
브라우저 (SvelteKit SPA)
    |   /api/auth/*, /oauth2/*, JSESSIONID 쿠키
Spring Security (OAuth2 Login + CSRF)
    |   OAuth2 Authorization Code Flow
Samsung ADFS (sts.secsso.net/adfs)
```

세션 기반 인증으로 DB 저장 없이 동작하며, SPA 프론트엔드와 호환되는 CSRF 보호를 포함합니다.

:::note
현재 상태: `AUTH_DISABLED = true` (비활성화). client-id/secret 설정 후 활성화가 필요합니다.
:::

## 구성 요소

| 파일 | 역할 |
|------|------|
| `application.yaml` | OAuth2 client/provider 설정 |
| `SecurityConfig.java` | Spring Security 필터 체인 (CSRF, 인가, OAuth2 로그인, 로그아웃) |
| `AuthController.java` | 인증 상태 API (`/api/auth/me`, `/api/auth/login-url`) |
| `auth.svelte.ts` | 프론트엔드 인증 상태 스토어 (Svelte 5 runes) |
| `client.ts` | API 클라이언트 (XSRF 토큰, 401 처리) |
| `minio.ts` | XHR 업로드 XSRF 토큰 |
| `+layout.svelte` | 인증 UI (사용자 이름, 로그아웃 버튼) |

## 인증 플로우

```
사용자                    Portal (Spring)              Galaxy SSO (ADFS)
  |                           |                              |
  |-- 페이지 접속 (/) ------->|                              |
  |<-- SPA 로드 ------------- |                              |
  |                           |                              |
  |-- GET /api/auth/me -----> |                              |
  |<-- {authenticated:false} -|                              |
  |                           |                              |
  |-- "Login" 클릭 ---------> |                              |
  |   /oauth2/authorization/galaxy                           |
  |<-- 302 Redirect --------- |                              |
  |                           |                              |
  |-- ADFS 로그인 페이지 ------------------------------------>|
  |<-- 인증 후 콜백 ------------------------------------------|
  |   /login/oauth2/code/galaxy?code=xxx                     |
  |                           |                              |
  |                           |-- 토큰 교환 (code -> token) ->|
  |                           |<-- access_token + id_token ---|
  |                           |                              |
  |<-- 302 -> / + JSESSIONID -|                              |
  |                           |                              |
  |-- GET /api/auth/me -----> |                              |
  |<-- {authenticated:true,   |                              |
  |     name:"홍길동",        |                              |
  |     email:"hong@..."}    |                              |
  |                           |                              |
  |-- API 요청 + XSRF 토큰 ->|  (JSESSIONID로 인증 확인)    |
  |<-- 응답 ----------------- |                              |
  |                           |                              |
  |-- POST /api/auth/logout ->|                              |
  |<-- {loggedOut:true} ----- |  (세션 무효화)               |
```

## application.yaml 설정

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          galaxy:
            client-id: YOUR_CLIENT_ID
            client-secret: YOUR_CLIENT_SECRET
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid, profile, email
            client-name: Samsung Galaxy SSO
        provider:
          galaxy:
            issuer-uri: https://sts.secsso.net/adfs
            authorization-uri: https://sts.secsso.net/adfs/oauth2/authorize
            token-uri: https://sts.secsso.net/adfs/oauth2/token
            jwk-set-uri: https://sts.secsso.net/adfs/discovery/keys
            user-info-uri: https://sts.secsso.net/adfs/userinfo
            user-name-attribute: upn
```

| 항목 | 값 | 설명 |
|------|-----|------|
| `registration-id` | `galaxy` | Spring 내부 식별자 |
| `grant-type` | `authorization_code` | 서버사이드 웹앱 표준 방식 |
| `redirect-uri` | `{baseUrl}/login/oauth2/code/{registrationId}` | Spring이 자동 처리하는 콜백 URL |
| `scope` | `openid, profile, email` | OIDC 표준 스코프 |
| `user-name-attribute` | `upn` | ADFS에서 사용자 식별에 사용하는 User Principal Name |

## SecurityConfig.java 설정

### 엔드포인트 보호 정책

| 패턴 | 인증 | 비고 |
|------|------|------|
| `/oauth2/**`, `/login/oauth2/**` | 불필요 | OAuth2 인프라 (Spring 자동 처리) |
| `/api/auth/me`, `/api/auth/login-url` | 불필요 | 인증 상태 확인용 (공개) |
| `/api/**` (기타) | **필요** | 401 Unauthorized 반환 (리다이렉트 아님) |
| `/`, `/_app/**`, `/*.js`, `/*.css` | 불필요 | 정적 파일 |
| `/**` | 불필요 | SPA 라우트 (프론트엔드에서 인증 처리) |

`AUTH_DISABLED = true`일 때는 CSRF 비활성, 모든 엔드포인트 공개. `false`로 변경하면 OAuth2 + CSRF가 활성화됩니다.

## CSRF 보호

SPA에서는 쿠키 기반 CSRF 보호 방식을 사용합니다:

1. Spring이 `XSRF-TOKEN` 쿠키를 설정 (`httpOnly: false` → JS에서 읽기 가능)
2. SPA가 쿠키에서 토큰 값을 읽음
3. 상태 변경 요청 (POST/PUT/DELETE) 시 `X-XSRF-TOKEN` 헤더에 토큰 포함
4. Spring이 쿠키와 헤더 값을 비교하여 검증

## 프론트엔드 구현

### auth.svelte.ts

Svelte 5의 `$state` runes를 사용한 반응형 인증 상태 관리. `getCsrfToken()` 함수가 `document.cookie`에서 `XSRF-TOKEN` 값을 추출합니다.

### client.ts

모든 POST/PUT/DELETE/PATCH 요청에 `X-XSRF-TOKEN` 헤더를 자동 추가합니다. 401 응답 시 `/oauth2/authorization/galaxy`로 자동 리다이렉트합니다.

### minio.ts

`fetch` API 대신 `XMLHttpRequest`를 사용하는 업로드에도 `X-XSRF-TOKEN` 헤더를 추가합니다.

## 활성화 방법

### 1단계: application.yaml 주석 해제

`client-id`와 `client-secret`을 실제 값으로 변경합니다.

### 2단계: SecurityConfig.java 토글

```java
private static final boolean AUTH_DISABLED = false;
```

### 3단계: +layout.svelte 인증 게이트 (선택사항)

미인증 사용자에게 로그인 프롬프트를 표시하려면 메인 콘텐츠를 조건부 렌더링으로 변경합니다.

### ADFS 등록 요구사항

| 항목 | 값 |
|------|-----|
| Client Type | Confidential (서버 앱) |
| Redirect URI | `https://{portal-domain}/login/oauth2/code/galaxy` |
| Grant Type | Authorization Code |
| Scopes | `openid`, `profile`, `email` |
| Token Format | JWT |

:::caution
**보안 고려사항**
- OAuth2 토큰은 서버에서만 관리. 브라우저에는 `JSESSIONID` 쿠키만 전달
- 모든 상태 변경 요청에 XSRF 토큰 필수 (쿠키 + 헤더 이중 검증)
- API에서 리다이렉트 대신 401 반환하여 SPA가 로그인 흐름을 제어
- `jwk-set-uri`를 통해 토큰 서명을 ADFS 공개키로 검증
:::
