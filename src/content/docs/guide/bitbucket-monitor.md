---
title: Bitbucket 브랜치 모니터
description: Bitbucket Server의 새 브랜치를 감지하여 ZIP 자동 다운로드 및 압축 해제하는 기능 사용 가이드
---

Bitbucket 브랜치 모니터는 자체 Bitbucket Server에서 새 브랜치 생성을 자동으로 감지하여 HEAD 서버의 지정 경로에 ZIP 다운로드 + 압축 해제를 수행하는 기능입니다.

---

## 1. 개요

### 배경

팀에서 펌웨어 코드를 Bitbucket Server에서 관리하고 있으며, 새 브랜치가 생성되면 HEAD 서버의 `/home/octo/FW_Code` 경로에 자동으로 코드를 배포해야 합니다. Bitbucket Server는 Webhook을 지원하지 않으므로 **Polling 방식**으로 구현되어 있습니다.

### 동작 방식

1. **5분 간격**으로 등록된 Bitbucket 저장소의 브랜치 목록을 조회
2. 이전에 다운로드하지 않은 **새 브랜치**를 감지
3. 해당 브랜치의 소스를 **ZIP으로 다운로드** → NFS 마운트 경로에 저장
4. ZIP을 **압축 해제**하여 폴더로도 유지 (ZIP + 폴더 둘 다 보존)

---

## 2. 사전 준비

### PAT (Personal Access Token) 발급

Bitbucket Server에서 PAT를 발급해야 합니다.

1. Bitbucket Server 로그인
2. 프로필 → **Personal Access Tokens** → **Create token**
3. **Repository Read** 권한 부여
4. 생성된 토큰을 복사 (한 번만 표시됨)

### NFS 마운트 확인

HEAD 서버의 `/home/octo/FW_Code`가 Portal 서버에서 접근 가능해야 합니다.

```bash
# Portal 서버에서 마운트 확인
ls /mnt/head/FW_Code
```

기본 마운트 경로는 `/mnt/head/FW_Code`이며, `application.yaml`의 `bitbucket.monitor.default-target-path`로 변경 가능합니다.

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
| **Target Path** | ZIP 저장 경로 (마운트) | `/mnt/head/FW_Code` |
| **Enabled** | 자동 폴링 활성화 여부 | 체크 |

4. **Create** 클릭

### 연결 테스트

저장소 등록 후 Actions 열의 **플러그 아이콘**을 클릭하면 Bitbucket API 연결을 테스트합니다. 성공 시 발견된 브랜치 수가 toast로 표시됩니다.

---

## 4. 자동 폴링

Enabled 상태의 저장소는 **5분 간격**으로 자동 폴링됩니다.

### 폴링 결과

- **새 브랜치 감지**: ZIP 다운로드 → 압축 해제 → DB에 이력 기록
- **기존 브랜치**: 스킵 (중복 다운로드 방지)
- **Last Polled**: 마지막 폴링 시각이 테이블에 표시됨

### 파일 저장 구조

브랜치명 `feature/new-function`의 경우:

```
/mnt/head/FW_Code/
├── feature_new-function.zip      ← ZIP 파일
└── feature_new-function/         ← 압축 해제된 폴더
    ├── src/
    ├── Makefile
    └── ...
```

:::note
브랜치명의 `/`는 `_`로 치환됩니다. 예: `feature/abc` → `feature_abc.zip`
:::

---

## 5. 수동 작업

### 수동 폴링

테이블 Actions 열의 **재생 아이콘**(▶)을 클릭하면 해당 저장소를 즉시 폴링합니다. 다운로드된 브랜치 수가 toast로 표시됩니다.

### 수동 다운로드

특정 브랜치를 직접 다운로드할 수 있습니다.

1. 저장소 행을 **더블 클릭**하여 브랜치 이력 패널 표시
2. 하단의 브랜치명 입력 필드에 원하는 브랜치명 입력 (예: `main`, `develop`)
3. **Download** 버튼 클릭

:::tip
이미 다운로드된 브랜치라도 수동 다운로드는 항상 실행됩니다. 기존 파일을 덮어씁니다.
:::

---

## 6. 다운로드 이력

저장소를 더블 클릭하면 하단에 해당 저장소의 브랜치 다운로드 이력이 표시됩니다.

| 컬럼 | 설명 |
|------|------|
| **Branch** | 브랜치 이름 |
| **Commit** | 다운로드 시점의 최신 커밋 해시 (앞 8자리) |
| **Status** | `OK` (성공), `FAIL` (실패), `...` (진행 중) |
| **Path** | ZIP 파일 저장 경로 |
| **Size** | ZIP 파일 크기 |
| **Downloaded** | 다운로드 완료 시각 |

Status가 `FAIL`인 경우 마우스를 올리면 에러 메시지를 확인할 수 있습니다.

---

## 7. 설정

### application.yaml

```yaml
bitbucket:
  monitor:
    enabled: true                        # 전체 모니터링 활성/비활성
    default-target-path: /mnt/head/FW_Code  # 기본 저장 경로
```

### 비활성화

전체 Bitbucket 모니터링을 끄려면:
- `bitbucket.monitor.enabled: false` 설정, 또는
- 개별 저장소의 **Enabled** 체크 해제

---

## 8. 문제 해결

| 증상 | 원인 | 해결 |
|------|------|------|
| 연결 테스트 실패 | PAT 만료 또는 권한 부족 | PAT 재발급, Repository Read 권한 확인 |
| ZIP 다운로드 실패 | 브랜치명 오류 또는 네트워크 | 브랜치명 정확히 입력, 방화벽 확인 |
| 파일이 생성되지 않음 | 마운트 경로 접근 불가 | `ls /mnt/head/FW_Code` 확인, NFS 마운트 상태 점검 |
| 자동 폴링 미작동 | enabled 비활성화 | application.yaml과 저장소별 Enabled 확인 |
| 브랜치 중복 다운로드 | 수동 다운로드는 중복 허용 | 정상 동작 (자동 폴링은 중복 방지) |
