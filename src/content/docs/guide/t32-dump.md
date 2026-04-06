---
title: T32 Dump (JTAG 디버깅)
description: Lauterbach TRACE32 디버거를 이용한 UFS 코어 덤프 기능 가이드
---

T32 Dump는 슬롯의 UFS 디바이스에 JTAG으로 연결하여 코어별 소스, 스택, 레지스터를 캡처하고 메모리를 덤프하는 기능입니다. Slots 페이지에서 슬롯 우클릭 → Debug → T32 Dump로 실행합니다.

---

## 1. 사전 준비

### 인프라 구성

각 Lab에는 다음 3종 장비가 필요합니다:

| 장비 | 역할 | OS | 예시 |
|------|------|-----|------|
| **TMUX 서버** | JTAG 물리 연결 제어 | Linux (라즈베리파이) | TMUX-01 |
| **T32 PC** | Lauterbach TRACE32 디버거 실행 | Windows | WIN-T32-01 |
| **텐타클** | 테스트 대상 서버 | — | T8, T9, T10 |

모든 장비는 **Admin > Server Management**에 SSH 접속 가능한 상태로 등록되어 있어야 합니다.

### Admin > T32 탭 설정

1. **Admin** 페이지 → **T32** 탭 → **Add** 버튼 클릭
2. 아래 필드를 입력합니다:

#### 기본 설정

| 필드 | 설명 | 예시 |
|------|------|------|
| **Server Group** | Lab 선택 | Lab A |
| **JTAG Server** | TMUX 서버 선택 | TMUX-01 (192.168.1.50) |
| **JTAG Username/Password** | TMUX 전용 계정 | 비워두면 서버 기본 계정 사용 |
| **T32 PC** | Windows PC 선택 | WIN-T32-01 (192.168.1.60) |
| **T32 PC Username/Password** | T32 PC 전용 계정 | 비워두면 서버 기본 계정 사용 |
| **Assigned Servers** | 이 설정이 적용될 텐타클 체크 | T8, T9, T10 |

#### 명령어 템플릿

| 필드 | 설명 | 예시 |
|------|------|------|
| **JTAG Command** | JTAG 연결 명령어 | `cd T32linker && echo yes \| python ./tmux_lib.py -n 1 -t {tentacle_num} -s {slot}` |
| **Success Pattern** | JTAG 성공 판별 정규식 | `Measured Voltage.*Register Value` |
| **T32 Port Check Command** | T32 Attach 확인 명령어 | `t32rem.exe localhost port=20000 system.mode.attach` |
| **Dump Command** | dump bat 실행 명령어 | `cmd /c C:\T32\dump.bat {branch_path} {result_path}` |

:::note
명령어 필드는 textarea로 되어 있어 긴 명령어도 전체 내용을 확인할 수 있습니다.
:::

#### 경로 매핑

| 필드 | 설명 | 예시 |
|------|------|------|
| **FW Code Linux Base** | Linux 측 FW 코드 기본 경로 | `/appdata/samsung/OCTO_HEAD/FW_Code` |
| **FW Code Windows Base** | Windows 측 FW 코드 기본 경로 | `F:\FW_Code` |
| **Result Base Path** | Linux 측 결과 저장 기본 경로 | `/appdata/samsung/OCTO_HEAD/T32_dump` |
| **Result Windows Base Path** | Windows 측 결과 저장 기본 경로 | `F:\T32_dump` |

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
| `{result_path}` | `"F:\T32_dump\20260407_T10-1_randwrite_Savona_V8_P00RC28"` | 결과 저장 경로 (Windows, 자동 생성) |
| `{branch_path}` | `"F:\FW_Code\Savona\Savona_V8_P00RC28"` | FW 코드 경로 (Windows, 사용자 선택) |

:::note
`{result_path}`와 `{branch_path}`는 경로에 공백이 있을 수 있어 자동으로 `""`로 감싸서 치환됩니다.
:::

### Result Path 조합 규칙

결과 폴더명은 다음 형식으로 자동 생성됩니다:

```
{date}_{setLocation}_{testToolName}_{testTrName}
```

예시: `20260407_T10-1_randwrite_Savona_V8_TLC_512Gb_512GB_P00RC28`

- `date`: 실행 날짜 (yyyyMMdd)
- `setLocation`: 슬롯의 setLocation (HeadSlotData에서 가져옴)
- `testToolName`: 실행 중인 테스트 도구명
- `testTrName`: 실행 중인 TR 이름

이 값이 `resultWindowsBasePath`와 결합되어 최종 경로가 됩니다:
```
F:\T32_dump\20260407_T10-1_randwrite_Savona_V8_TLC_512Gb_512GB_P00RC28
```

---

## 3. 경로 매핑 (Linux ↔ Windows)

Portal 서버는 Linux이고, T32 PC는 Windows입니다. 같은 NAS를 각각 다른 경로로 마운트하므로, 경로 변환이 필요합니다.

### FW Code 경로 변환

사용자가 선택한 브랜치 폴더(Linux 경로)를 dump 명령어용 Windows 경로로 변환합니다:

```
① 사용자 선택 (Linux):
   /appdata/samsung/OCTO_HEAD/FW_Code/Savona/Savona_V8_P00RC28

② fwCodeLinuxBase 제거:
   /Savona/Savona_V8_P00RC28

③ fwCodeWindowsBase 결합 + 경로 구분자 변환 (/ → \):
   F:\FW_Code\Savona\Savona_V8_P00RC28

④ dumpCommand의 {branch_path}에 대입:
   cmd /c C:\T32\dump.bat "F:\FW_Code\Savona\Savona_V8_P00RC28" "F:\T32_dump\..."
```

### Result 경로 변환

결과 폴더는 Linux/Windows 양쪽 경로가 모두 생성됩니다:

| 용도 | 경로 |
|------|------|
| **Linux** (Portal에서 파일 조회) | `/appdata/.../T32_dump/20260407_T10-1_...` |
| **Windows** (dump.bat에서 저장) | `F:\T32_dump\20260407_T10-1_...` |

Linux 경로로 결과 폴더를 미리 `mkdir`하고, Windows 경로를 dump 명령어에 전달합니다.

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
- **Fail 아닌 슬롯**: 노란 경고 — "Pre-Command로 Hang을 먼저 걸어주세요"
- **FW 소스 폴더 선택**: Bitbucket 브랜치 리스트

### FW 소스 폴더 선택

브랜치를 선택하는 3가지 방법이 제공됩니다:

#### 방법 1: Bitbucket 브랜치 리스트에서 선택 (권장)

Dialog 하단에 Bitbucket에서 다운로드된 브랜치 목록이 자동으로 표시됩니다.

| 상태 | 아이콘 | 동작 |
|------|--------|------|
| **DOWNLOADED** | 초록 폴더 | 클릭하면 즉시 선택됨 (hover 시 초록 배경) |
| **DETECTED** | 구름 다운로드 | "다운로드" 버튼 클릭 → SSE 진행률 표시 → 완료 후 자동 선택 |
| **FAILED** | 빨간 X | "다운로드" 버튼으로 재시도 가능 |

- **검색**: 리스트 상단 검색창에 브랜치명 일부를 입력하면 실시간 필터링
- **긴 브랜치명**: 자동 줄바꿈으로 전체 이름 표시 (잘리지 않음)
- 선택 완료 시 **Windows 경로**로 변환되어 표시됨

#### 방법 2: 파일 시스템에서 직접 선택

우상단 **"직접 찾기"** 링크를 클릭하면 LogBrowser가 열립니다. `fwCodeLinuxBase` 경로를 루트로 폴더를 탐색하여 선택합니다.

#### 방법 3: 브랜치가 없는 경우

Bitbucket에 브랜치가 없으면 안내 메시지와 함께 "파일 시스템에서 직접 선택" 링크가 표시됩니다.

### Stepper 실행 — 4단계

**"Dump 시작"** 버튼을 클릭하면 4단계가 순차적으로 실행됩니다:

| Step | 이름 | 대상 서버 | 동작 | 성공 판정 |
|------|------|-----------|------|-----------|
| 1 | **JTAG 연결** | TMUX (SSH) | `jtagCommand` 실행 | `jtagSuccessPattern` 정규식 매칭 |
| 2 | **T32 Attach** | T32 PC (SSH) | `t32PortCheckCommand` 실행 | exitCode=0 + stdout에 "Down" 없음 |
| 3 | **Dump 실행** | T32 PC (SSH) | `dumpCommand` 실행 (경로 치환) | exitCode=0 + stdout에 "fail" 없음 |
| 4 | **완료** | — | 결과 경로 반환 | — |

각 Step의 동작:
- **실시간 로그**: SSH stdout이 라인 단위로 SSE 스트리밍되어 브라우저에 표시
- **성공**: 다음 Step으로 자동 진행
- **실패**: 로그 자동 펼침 + "다시 시도" 버튼 표시
- **펼치기/접기**: 각 Step 클릭 시 출력 로그 토글

#### Step 3 상세 — Core별 진행 파싱

dump.bat의 stdout에서 Core 이름 패턴을 자동 감지하여 진행 상태를 표시합니다:

- 감지 패턴: `H-Core`, `CM-Core`, `F-Core`, `N-Core`, `Canary` (대소문자 무시)
- Phase 감지: `source`, `stack`, `register`, `dump`, `capture`
- Status 감지: `done`/`complete`/`success` → 완료, `fail`/`error` → 실패

#### Step 3 실패 판정

Step 3은 두 가지 조건으로 실패를 판정합니다:
1. **exit code ≠ 0**: dump.bat이 비정상 종료
2. **stdout에 "fail" 포함** (대소문자 무시): exit code가 0이어도 실패 처리

### 결과 확인

Dump 성공 시:
- **결과 경로**: 마지막 2 segment 표시 (예: `T32_dump/20260407_T10-1_...`)
- **파일 목록**: 결과 폴더의 파일이 자동으로 로드됨
- **이미지 미리보기**: `.png`, `.jpg` 등 이미지 파일 클릭 시 인라인 미리보기
- **다운로드**: 각 파일 옆 다운로드 버튼

---

## 5. Hang 명령어 (Fail 아닌 슬롯)

Fail 상태가 아닌 슬롯에서 T32 JTAG 연결을 하려면 디바이스를 먼저 정지시켜야 합니다.

```bash
# Pre-Command에 등록할 hang 명령어 예시
nohup adb -s {usbId} shell hang_ufs > /dev/null 2>&1 &
```

- `nohup ... &`로 백그라운드 실행 → SSH 세션 즉시 반환
- USB가 빠지면 adb 프로세스 자동 종료 (잔여 프로세스 없음)
- Pre-Command는 Slots 페이지의 슬롯 설정에서 등록합니다

---

## 6. 보안 — 전용 계정

T32Config에 JTAG/T32 PC별 전용 계정을 설정할 수 있습니다:

- **비워두면**: PortalServer에 등록된 기본 계정으로 SSH 접속
- **입력하면**: 해당 계정으로 SSH 접속 (NAS 마운트 권한 등 별도 계정 필요 시)
- 비밀번호는 수정 시 입력하지 않으면 기존 값 유지

JTAG 서버, T32 PC 모두 동일한 동작입니다.

---

## 7. 문제 해결

| 증상 | 원인 | 해결 |
|------|------|------|
| T32 Dump 메뉴 비활성화 | T32 Config 미등록 | Admin > T32에서 설정 등록 + Assigned Servers 체크 |
| T32 Dump 메뉴 비활성화 | 다중 슬롯 선택 | 단일 슬롯만 선택 |
| T32 Dump 메뉴 비활성화 | 연결 끊김 | connection = 1인 슬롯에서만 가능 |
| Step 1 실패 | JTAG 물리 연결 불량 | TMUX 서버 SSH 접속 확인, JTAG 케이블 점검 |
| Step 1 실패 | Success Pattern 불일치 | Admin > T32에서 정규식 패턴 확인 |
| Step 2 Debug Port Fail | T32 연결 불가 | JTAG 연결 상태 확인, Hang 명령어 실행 여부 확인 |
| Step 3 실패 (fail 감지) | dump.bat 내부 오류 | 출력 로그에서 "fail" 키워드 전후 확인 |
| Step 3 타임아웃 | 실행 시간 초과 (5분) | bat 파일 로직 확인 |
| 브랜치 목록 안 보임 | Bitbucket 저장소 미등록 | Admin > Bitbucket에서 저장소 등록 |
| 경로 변환 오류 | FW Code 경로 매핑 미설정 | Admin > T32에서 Linux/Windows 경로 확인 |
| 전용 계정 접속 실패 | 계정 정보 오류 | Admin > T32에서 Username/Password 확인 |
