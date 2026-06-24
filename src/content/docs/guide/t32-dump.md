---
title: T32 Dump (JTAG 디버깅)
description: Lauterbach TRACE32 디버거를 이용한 UFS 코어 덤프 기능 가이드 — JTAG(SSH) + T32 PowerView(t32remote gRPC) 5-Step
---

T32 Dump는 슬롯의 UFS 디바이스에 JTAG으로 연결하여 코어별 메모리를 덤프하는 기능입니다. **JTAG 서버는 SSH로**, **T32 PowerView는 t32remote(gRPC)로** 제어하며, 결과 ZIP은 MinIO에 보관합니다. Slots 페이지에서 슬롯 우클릭 → Debug → T32 Dump로 실행합니다.

:::note[gRPC 경로가 기본, SSH+bat은 legacy]
`T32Config`에 **T32Remote Host/Port**를 채우면 Step 2·3을 t32remote gRPC로 수행합니다(권장). 비워두면 옛 SSH + `.bat` 경로로 동작합니다. 이 가이드는 gRPC 경로 기준입니다.
:::

---

## 1. 사전 준비

### 인프라 구성

각 Lab에는 다음 3종 장비가 필요합니다:

| 장비 | 역할 | OS | 예시 |
|------|------|-----|------|
| **JTAG 서버** | JTAG 물리 연결 제어 (SSH) | Linux (라즈베리파이) | JTAG-01 |
| **T32 PC** | Lauterbach TRACE32 PowerView + t32remote.exe | Windows | WIN-T32-01 |
| **텐타클** | 테스트 대상 서버 | — | T8, T9, T10 |

모든 장비는 **Admin > Server Management**에 SSH 접속 가능한 상태로 등록되어 있어야 합니다.

### T32 PC 측 t32remote 준비 (gRPC 경로)

T32 PC에서 **t32remote.exe**가 떠 있어야 하고, TRACE32 PowerView의 `config.t32`에 RCL 채널이 켜져 있어야 합니다:

```text
RCL=NETASSIST
PORT=20000
PACKLEN=1024
```

- t32remote는 PowerView와 **같은 Windows 머신**에서 실행 (기본 포트 `:50551`)
- PowerView는 콘솔/RDP 세션에 GUI로 떠 있어야 USB 디버그 모듈 인식
- 자세한 내부 구조는 [t32remote 모듈](/learn/l2-t32remote/) 참고

### Admin > T32 탭 설정

1. **Admin** 페이지 → **T32** 탭 → **Add** 버튼 클릭
2. 아래 필드를 입력합니다:

#### 기본 설정

| 필드 | 설명 | 예시 |
|------|------|------|
| **Server Group** | Lab 선택 | Lab A |
| **JTAG Server** | JTAG 서버 선택 | JTAG-01 (192.168.1.50) |
| **JTAG Username/Password** | JTAG 서버 전용 계정 | 비워두면 서버 기본 계정 사용 |
| **T32 PC** | Windows PC 선택 | WIN-T32-01 (192.168.1.60) |
| **T32 PC Username/Password** | T32 PC 전용 계정 | 비워두면 서버 기본 계정 사용 |
| **Assigned Servers** | 이 설정이 적용될 텐타클 체크 | T8, T9, T10 |

#### 명령어 / gRPC 설정

| 필드 | 설명 | 예시 |
|------|------|------|
| **JTAG Command** | JTAG 연결 명령어 (Step 1, SSH) | `cd T32linker && echo yes \| python ./tmux_lib.py -n 1 -t {tentacle_num} -s {slot}` |
| **Success Pattern** | JTAG 성공 판별 정규식 (선택) | `Measured Voltage.*Register Value` |
| **T32 Remote Host** | t32remote.exe 호스트 (채우면 gRPC 경로) | `192.168.1.60` |
| **T32 Remote Port** | t32remote.exe 포트 | `50551` |
| **T32 Port Check Command** | gRPC 경로에선 **RCL 포트 번호**로 사용 | `20000` |
| **T32 Start Command** | JTAG 변경 후 PowerView 재시작 명령 | `C:\T32\bin\windows64\t32mp64.exe -c C:\T32\config.t32` |
| **Core Scripts** | controller 그룹별 cmm 스크립트 (JSON) | 아래 참고 |
| **Dump Command** | legacy(SSH+bat) 전용 | `cmd /c C:\T32\dump.bat {branch_path} {result_path}` |

:::note
명령어 필드는 textarea로 되어 있어 긴 명령어도 전체 내용을 확인할 수 있습니다.
:::

#### Core Scripts (gRPC 경로) — controller 그룹

controller(SoC)마다 cmm 위치·Canary 경로가 달라, Core Scripts는 **controller 그룹** 단위로 등록합니다. Admin 코어 에디터에서 controller 그룹을 추가하고(이름은 UFSinfo controller 목록 자동완성), 그 안에 core를 넣습니다.

```json
[
  {
    "controller": "S5E9945",
    "canaryRelPath": "00_BUILD\\00_SIMULATOR\\CANARY",
    "cores": [
      { "core": "H-Core",  "cmmRelPath": "scripts/h_core.cmm",  "optionalCommands": "" },
      { "core": "CM-Core", "cmmRelPath": "scripts/cm_core.cmm", "optionalCommands": "" },
      { "core": "Canary",  "cmmRelPath": "scripts/canary.cmm",  "optionalCommands": "" }
    ]
  }
]
```

- **controller**: slot/HEAD의 controller와 매칭하는 키(대소문자 무시). 비우면 controller 없는 slot의 fallback 그룹
- **canaryRelPath**: Canary 폴더의 branch base 기준 상대경로. 비우면 기본값 `00_BUILD\00_SIMULATOR\CANARY`
- **cores[].cmmRelPath**: 선택한 브랜치 폴더 기준 상대 경로 (Windows 경로로 변환되어 `CD.DO`에 전달)
- **cores[].optionalCommands**: cmm 실행 후 보낼 추가 PRACTICE 명령 (줄마다 개별 전송)
- **Canary** core는 report 생성 대기 + robocopy 복사 등 별도 후처리가 동작

:::note[구 포맷 호환]
controller 없는 flat 목록 `[{core, cmmRelPath, optionalCommands}]`도 그대로 동작합니다(controller 없는 단일 그룹으로 처리). 기존 config는 수정 불필요.
:::

**controller 선택**: dump 시 slot의 controller로 그룹을 자동 선택합니다. 그룹이 하나뿐이면 자동, slot에 controller가 없거나 안 맞으면 다이얼로그가 **controller 드롭다운**으로 선택을 요청합니다(아래 5단계 참고).

#### 경로 매핑

| 필드 | 설명 | 예시 |
|------|------|------|
| **FW Code Linux Base** | Linux 측 FW 코드 기본 경로 | `/appdata/samsung/OCTO_HEAD/FW_Code` |
| **FW Code Windows Base** | Windows 측 FW 코드 기본 경로 | `F:\FW_Code` |
| **Result Base Path** | Linux 측 결과 저장 fallback 경로 | `/appdata/samsung/OCTO_HEAD/T32_dump` |
| **Result Windows Base Path** | Windows 측 결과 저장 fallback 경로 | `F:\T32_dump` |

#### 설명/활성화

| 필드 | 설명 |
|------|------|
| **Description** | 자유 메모 |
| **Enabled** | 체크 해제 시 이 설정이 비활성화됨 |

---

## 2. 명령어 플레이스홀더

명령어 템플릿에 다음 변수를 사용할 수 있습니다. 실행 시 실제 값으로 치환됩니다.

| 변수 | 예시 값 | 설명 |
|------|---------|------|
| `{tentacle}` | `T10` | 텐타클 이름 그대로 |
| `{tentacle_num}` | `10` | 텐타클 이름에서 숫자만 추출 |
| `{slot}` | `1` | 슬롯 번호 |
| `{result_path}` | `"F:\T32_dump\..."` | 결과 저장 경로 (legacy dumpCommand 전용) |
| `{branch_path}` | `"F:\FW_Code\Savona\Savona_V8_P00RC28"` | FW 코드 경로 (legacy dumpCommand 전용) |

`{tentacle}` 계열은 영숫자·언더스코어·하이픈만 허용(명령어 인젝션 방지). `{result_path}`/`{branch_path}`는 공백이 있을 수 있어 자동으로 `""`로 감싸서 치환됩니다.

### Result Path 조합 규칙

결과 폴더명은 다음 형식으로 자동 생성됩니다(시·분·초 포함 → 같은 브랜치 반복 dump도 구분):

```
{datetime}_{setLocation}_{testToolName}_{testTrName}
```

예시: `20260623_14h05m22s_T10-1_randwrite_Savona_V8_TLC_512Gb_512GB_P00RC28`

결과 폴더는 **선택한 브랜치 폴더 안**에 생성됩니다(없으면 `resultBasePath`로 fallback).

---

## 3. 경로 매핑 (Linux ↔ Windows)

Portal 서버는 Linux이고, T32 PC는 Windows입니다. 같은 NAS를 각각 다른 경로로 마운트하므로 경로 변환이 필요합니다.

### FW Code 경로 변환

사용자가 선택한 브랜치 폴더(Linux 경로)를 Windows 경로로 변환합니다:

```
① 사용자 선택 (Linux):
   /appdata/samsung/OCTO_HEAD/FW_Code/Savona/Savona_V8_P00RC28

② fwCodeLinuxBase 제거:
   /Savona/Savona_V8_P00RC28

③ fwCodeWindowsBase 결합 + 경로 구분자 변환 (/ → \):
   F:\FW_Code\Savona\Savona_V8_P00RC28

④ gRPC 경로: CD.DO "F:\FW_Code\Savona\...\scripts\h_core.cmm"
   legacy 경로: dump.bat의 {branch_path}에 대입
```

### Result 경로 변환

결과 폴더는 Linux/Windows 양쪽 경로가 모두 생성됩니다:

| 용도 | 경로 |
|------|------|
| **Linux** (Portal에서 파일 조회 / ZIP 읽어 업로드) | `/appdata/.../FW_Code/Savona/.../20260623_14h...` |
| **Windows** (PowerView가 저장) | `F:\FW_Code\Savona\...\20260623_14h...` |

---

## 4. Dump 실행

### 진입 경로

Slots 페이지에서:
1. 슬롯 **우클릭** → **Debug** → **T32 Dump**
2. 활성화 조건:
   - 단일 슬롯만 선택
   - 연결 상태 (connection = 1)
   - 해당 텐타클에 T32 Config가 등록되어 있음

### Dump Dialog — Idle 화면

Dialog가 열리면 다음 정보가 표시됩니다:

- **연결 정보**: JTAG 서버명, T32 PC명
- **점유 배너**: 다른 사용자가 dump 중이면 "{lockedBy} 님이 진행 중" amber 카드 → 시작 차단
- **Fail 아닌 슬롯**: 노란 경고 — "Pre-Command로 Hang을 먼저 걸어주세요"
- **FW 소스 폴더 선택**: Bitbucket 브랜치 리스트

### FW 소스 폴더 선택 (브랜치 자동 최신화)

다이얼로그를 열면 캐시된 브랜치를 **즉시 표시**하고, 백그라운드로 Bitbucket을 폴링해 **최신 브랜치를 자동으로 받아와 목록을 갱신**합니다(scheduled 5분 폴링을 기다리지 않음). 헤더의 **"새로고침"** 버튼으로 수동 재폴링도 가능합니다.

폴링/재조회가 실패하면(인증 만료·네트워크·PAT 오류 등) 조용히 무시하지 않고 **toast로 원인을 노출**합니다. 등록된 Bitbucket 저장소가 없으면 그 사실을 안내합니다("새 브랜치가 안 떠도 단서가 남지 않던" 증상 개선).

브랜치를 선택하는 3가지 방법:

#### 방법 1: Bitbucket 브랜치 리스트에서 선택 (권장)

| 상태 | 아이콘 | 동작 |
|------|--------|------|
| **DOWNLOADED** | 초록 폴더 | 클릭하면 즉시 선택됨 |
| **DETECTED** | 구름 다운로드 | "다운로드" 버튼 클릭 → SSE 진행률 표시 → 완료 후 자동 선택 |
| **FAILED** | 빨간 X | "다운로드" 버튼으로 재시도 가능 |

- **검색**: 리스트 상단 검색창에 브랜치명 일부를 입력하면 실시간 필터링
- 선택 완료 시 **Windows 경로**로 변환되어 표시됨

#### 방법 2: 파일 시스템에서 직접 선택

우상단 **"직접 찾기"** 링크를 클릭하면 LogBrowser가 열립니다. `fwCodeLinuxBase` 경로를 루트로 폴더를 탐색하여 선택합니다.

#### 방법 3: 브랜치가 없는 경우

Bitbucket에 브랜치가 없으면 안내 메시지와 함께 "파일 시스템에서 직접 선택" 링크가 표시됩니다.

### Stepper 실행 — 5단계

**"Dump 시작"** 버튼을 클릭하면 단계가 순차적으로 실행됩니다. 시작 시 **단독 점유 lock**을 잡아 같은 T32 PC의 동시 dump를 막습니다.

| Step | 이름 | 대상 | 프로토콜 | 성공 판정 |
|------|------|------|----------|-----------|
| 1 | **JTAG 연결** | JTAG 서버 | SSH | `jtagSuccessPattern` → "Measured Voltage" > 0 → exitCode=0 |
| — | *(PowerView 재시작)* | T32 PC | SSH+PsExec | best-effort (`t32StartCommand` 있을 때) |
| 2 | **T32 Attach** | t32remote | gRPC | `t32_version` ≠ 빈값 + `STATE.TARGET()` 정상 |
| 3 | **Dump 실행** | t32remote | gRPC | 선택된 controller 그룹의 core별 `ExecuteCommand` 전부 성공 |
| 4 | **결과 업로드** | MinIO | S3 | (soft) ZIP 업로드 — 실패해도 dump는 성공 |

각 Step의 동작:
- **실시간 로그**: 출력이 라인 단위로 SSE 스트리밍되어 브라우저에 표시
- **성공**: 다음 Step으로 자동 진행
- **실패**: 로그 자동 펼침 + "다시 시도" 버튼 표시
- **중단**: "중단" 버튼 → 워커 interrupt → lock 해제

#### controller 선택 (gRPC 경로)

Step 3 직전, slot의 controller로 Core Scripts의 controller 그룹을 자동 선택합니다. 그룹이 하나뿐이면 자동, slot에 controller가 없거나 등록된 그룹과 안 맞으면 다이얼로그에 **controller 드롭다운**이 나타납니다. 후보에서 controller를 골라 다시 "Dump 시작"을 누르면 그 그룹으로 진행합니다.

#### PowerView 재시작이 필요한 이유

JTAG 연결을 바꾸면 이미 떠 있던 PowerView가 fatal `#FF` 상태가 됩니다. 그래서 Step 2 전에 `taskkill` → **PsExec**로 활성 세션에 GUI로 재시작합니다(`t32StartCommand`가 비면 건너뜀, 수동 운영).

#### Step 3 상세 — core별 진행

선택된 controller 그룹의 core마다 `CD.DO`로 cmm을 실행하고, dump 직후 PowerView 창 스크린샷(PNG)을 저장합니다(Canary core는 그룹의 `canaryRelPath` 폴더에서 report 대기 + robocopy 후처리). 모든 core가 끝나면 result 폴더를 PowerShell `Compress-Archive`로 ZIP 압축합니다.

### 결과 확인

Dump 성공 시:
- **결과 경로**: 마지막 2 segment 표시
- **파일 목록**: 결과 폴더의 파일이 자동으로 로드됨 (이미지/Canary report/ZIP)
- **이미지 미리보기**: 이미지 파일 클릭 시 인라인 미리보기
- **두 가지 다운로드**:
  - **"전체 다운로드"**: log-browser로 result 폴더를 즉석 ZIP
  - **"S3에서 다운로드"**: `historyId`+`testType`이 있으면 노출. MinIO에 보관된 결과 ZIP을 presigned로 다운로드 (업로드 완료까지 3회 재시도)

---

## 5. 결과 보관 정책

- dump 결과 ZIP은 MinIO(`t32-results` 버킷)에 업로드되고 `(testType, source, historyId)`로 추적됩니다.
- **로컬 result 폴더는 보존** — 완료 직후 다이얼로그가 그 폴더를 조회하기 때문. S3는 백업/장기보관용.
- **60일 자동 삭제**: 매일 04:00 KST에 `uploadedAt` 60일 초과 아티팩트의 S3 객체 + DB row를 함께 삭제합니다.

---

## 6. Hang 명령어 (Fail 아닌 슬롯)

Fail 상태가 아닌 슬롯에서 T32 JTAG 연결을 하려면 디바이스를 먼저 정지시켜야 합니다.

```bash
# Pre-Command에 등록할 hang 명령어 예시
nohup adb -s {usbId} shell hang_ufs > /dev/null 2>&1 &
```

- `nohup ... &`로 백그라운드 실행 → SSH 세션 즉시 반환
- USB가 빠지면 adb 프로세스 자동 종료 (잔여 프로세스 없음)
- Pre-Command는 Slots 페이지의 슬롯 설정에서 등록합니다

---

## 7. 보안 — 전용 계정

T32Config에 JTAG/T32 PC별 전용 계정을 설정할 수 있습니다:

- **비워두면**: PortalServer에 등록된 기본 계정으로 SSH 접속
- **입력하면**: 해당 계정으로 SSH 접속 (NAS 마운트 권한 등 별도 계정 필요 시)
- 비밀번호는 수정 시 입력하지 않으면 기존 값 유지

JTAG 서버, T32 PC 모두 동일한 동작입니다.

---

## 8. 문제 해결

| 증상 | 원인 | 해결 |
|------|------|------|
| T32 Dump 메뉴 비활성화 | T32 Config 미등록 | Admin > T32에서 설정 등록 + Assigned Servers 체크 |
| T32 Dump 메뉴 비활성화 | 다중 슬롯 선택 | 단일 슬롯만 선택 |
| T32 Dump 메뉴 비활성화 | 연결 끊김 | connection = 1인 슬롯에서만 가능 |
| "{사용자} 님이 진행 중" | 같은 T32 PC를 다른 사람이 dump 중 | 완료/중단 대기 (단독 점유 lock) |
| Step 1 실패 | JTAG 물리 연결 불량 | JTAG 서버 SSH 접속 확인, JTAG 케이블 점검 |
| Step 1 실패 | Success Pattern 불일치 | Admin > T32에서 정규식/전압 출력 확인 |
| Step 2 실패 (t32_version 없음) | PowerView 미실행 / RCL 미설정 | T32 PC에서 PowerView 실행 + `config.t32`의 `RCL=NETASSIST PORT=20000` 확인 |
| Step 2 실패 (target error) | 타겟 디버그 연결 실패 | JTAG 케이블/전원/Hang 명령 실행 여부 확인 |
| Step 2 gRPC 연결 실패 | t32remote.exe 미실행 | T32 PC에서 t32remote.exe(:50551) 실행 확인, Host/Port 설정 확인 |
| "controller를 선택하세요" 드롭다운 | slot.controller가 없거나 Core Scripts 그룹과 안 맞음 | 드롭다운에서 controller 선택 후 재실행 / Admin > T32에서 controller 그룹 이름 확인 |
| Step 3 실패 (cores 비어있음) | 선택된 controller 그룹에 core 없음 | Admin > T32 Core Scripts에서 해당 controller 그룹에 core 추가 |
| Step 3 실패 | core cmm 경로 오류 / 명령 실패 | Core Scripts의 `cmmRelPath` 확인, 출력 로그 점검 |
| Canary 후처리 실패 | `canaryRelPath` 경로 불일치 | controller 그룹의 `canaryRelPath`(branch base 기준 상대경로) 확인 |
| Step 4 (S3 업로드 실패) | MinIO 연결/버킷 문제 | dump는 성공 처리됨. "전체 다운로드"로 로컬 결과 받기, MinIO 상태 확인 |
| 브랜치 목록 안 보임 | Bitbucket 저장소 미등록 | Admin > Bitbucket에서 저장소 등록 (다이얼로그 "새로고침"으로 재폴링) |
| 경로 변환 오류 | FW Code 경로 매핑 미설정 | Admin > T32에서 Linux/Windows 경로 확인 |
| 전용 계정 접속 실패 | 계정 정보 오류 | Admin > T32에서 Username/Password 확인 |
