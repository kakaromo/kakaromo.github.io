---
title: Bitbucket 브랜치 모니터
description: Bitbucket Server의 새 브랜치를 감지하여 ZIP 자동 다운로드 및 압축 해제하는 기능 사용 가이드
---

Bitbucket 브랜치 모니터는 자체 Bitbucket Server에서 새 브랜치 생성을 자동으로 감지하여 Portal 서버의 지정 경로에 ZIP 다운로드 + 압축 해제를 수행하는 기능입니다.

---

## 1. 개요

### 배경

팀에서 펌웨어 코드를 Bitbucket Server에서 관리하고 있으며, 새 브랜치가 생성되면 자동으로 코드를 배포해야 합니다. Bitbucket Server는 Webhook을 지원하지 않으므로 **Polling 방식**으로 구현되어 있습니다.

### 동작 방식

1. **5분 간격**으로 등록된 Bitbucket 저장소의 브랜치 목록을 조회
2. 이전에 다운로드하지 않은 **새 브랜치**를 감지
3. 브랜치명에서 **UFS Controller를 자동 감지** (DB 매칭)
4. 해당 브랜치의 소스를 **ZIP으로 다운로드** → 저장 경로에 저장
5. ZIP을 **압축 해제**하여 폴더로도 유지 (ZIP + 폴더 둘 다 보존)

### 저장 경로 구조

```
/appdata/samsung/OCTO_HEAD/FW_Code/           ← Portal 서버 기본 경로
├── Savona/                                    ← Controller 자동 감지
│   ├── Savona_V8_512Gb_512GB_P00RC28.zip
│   └── Savona_V8_512Gb_512GB_P00RC28/
├── SERRA/
│   ├── 2023_SERRA_V9_256Gb.zip
│   └── 2023_SERRA_V9_256Gb/
└── (controller 미감지 시 루트에 저장)
```

Windows PC에서는 `F:\FW_Code`로 동일 경로가 Samba/NFS로 마운트되어 있습니다.

---

## 2. 사전 준비

### PAT (Personal Access Token) 발급

Bitbucket Server에서 PAT를 발급해야 합니다.

1. Bitbucket Server 로그인
2. 프로필 → **Personal Access Tokens** → **Create token**
3. **Repository Read** 권한 부여
4. 생성된 토큰을 복사 (한 번만 표시됨)

### 방화벽

Portal 서버 → Bitbucket 서버 방향으로 **단방향 통신**만 필요합니다 (기본 포트: 7990 또는 443).

---

## 3. 저장소 등록

Admin 페이지의 **Bitbucket** 탭에서 감시 대상 저장소를 등록합니다.

### 등록 절차

1. **Admin** 페이지 → **Bitbucket** 탭 클릭
2. **Add** 버튼 클릭
3. 다음 정보 입력:

| 필드 | 설명 | 예시 |
|------|------|------|
| **Name** | 표시 이름 | `FW Main Repo` |
| **Server URL** | Bitbucket 서버 주소 | `https://bitbucket.mycompany.com` |
| **Project Key** | Bitbucket 프로젝트 키 | `FW` |
| **Repo Slug** | 저장소 이름 (slug) | `firmware-code` |
| **PAT** | Personal Access Token | `NzM2...` (비밀번호 필드) |
| **Controller** | UFS Controller 선택 (선택사항) | `Savona` |
| **Target Path** | ZIP 저장 경로 | `/appdata/samsung/OCTO_HEAD/FW_Code` |
| **Enabled** | 자동 폴링 활성화 여부 | 체크 |

### Controller 설정

- **수동 선택**: UFS Info의 Controller 목록에서 선택
- **미선택 시**: 브랜치명에서 자동 감지 (아래 참고)

### Controller 자동 감지

Controller가 미설정된 경우, 다운로드 시 브랜치명에서 UFS Info Controller DB와 자동 매칭합니다.

```
브랜치명: "2023/SERRA_V8_512Gb_512GB_P00RC28"
→ "/" → "_" 치환: "2023_SERRA_V8_512Gb_512GB_P00RC28"
→ 토큰 분리: ["2023", "SERRA", "V8", "512Gb", ...]
→ DB Controller 목록과 대소문자 무시 비교
→ "SERRA" 매칭 → /appdata/.../FW_Code/SERRA/ 에 저장
```

---

## 4. 자동 폴링

Enabled 상태의 저장소는 **5분 간격**으로 자동 폴링됩니다.

### 폴링 결과

- **새 브랜치 감지**: ZIP 다운로드 → 압축 해제 → DB에 이력 기록
- **기존 브랜치**: 스킵 (중복 다운로드 방지)
- **Last Polled**: 마지막 폴링 시각이 테이블에 표시됨

:::note
브랜치명의 `/`는 `_`로 치환됩니다. 예: `feature/abc` → `feature_abc.zip`
:::

---

## 5. 수동 작업

### 수동 폴링

테이블 Actions 열의 **재생 아이콘**(▶)을 클릭하면 해당 저장소를 즉시 폴링합니다.

### 수동 다운로드

특정 브랜치를 직접 다운로드할 수 있습니다.

1. 저장소 행을 **더블 클릭**하여 브랜치 이력 패널 표시
2. 하단의 브랜치명 입력 필드에 원하는 브랜치명 입력
3. **Download** 버튼 클릭

---

## 6. Bitbucket Server REST API

Portal이 내부적으로 호출하는 API:

| 용도 | API |
|------|-----|
| 브랜치 목록 | `GET /rest/api/latest/projects/{proj}/repos/{repo}/branches` |
| ZIP 다운로드 | `GET /rest/api/latest/projects/{proj}/repos/{repo}/archive?format=zip&at={branch}` |

- HTTPS + SSL 인증서 무시 (자체 서명 인증서)
- 리다이렉트 자동 따라가기
- `Authorization: Bearer {PAT}` 헤더
- 페이지네이션 처리 (`isLastPage`, `nextPageStart`)

---

## 7. 설정

### application.yaml

```yaml
bitbucket:
  monitor:
    enabled: true
    default-target-path: /appdata/samsung/OCTO_HEAD/FW_Code
```

---

## 8. 문제 해결

| 증상 | 원인 | 해결 |
|------|------|------|
| 연결 테스트 실패 | PAT 만료 또는 권한 부족 | PAT 재발급, Repository Read 권한 확인 |
| SSL 인증서 오류 | 자체 서명 인증서 | Portal이 자동으로 SSL 무시 처리 (변경 불필요) |
| ZIP 다운로드 실패 | 브랜치명 오류 또는 네트워크 | 브랜치명 정확히 입력, 방화벽 확인 |
| Controller 자동 감지 안 됨 | UFS Info에 Controller 미등록 | Admin > UFS Info에서 Controller 추가 |
| 자동 폴링 미작동 | enabled 비활성화 | application.yaml과 저장소별 Enabled 확인 |
