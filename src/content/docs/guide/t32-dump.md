---
title: T32 Dump (JTAG 디버깅)
description: Lauterbach TRACE32 디버거를 이용한 UFS 코어 덤프 기능 가이드
---

T32 Dump는 슬롯의 UFS 디바이스에 JTAG으로 연결하여 코어별 소스, 스택, 레지스터를 캡처하고 메모리를 덤프하는 기능입니다.

---

## 1. 사전 준비

### 인프라 구성

각 Lab에는 다음 장비가 필요합니다:

| 장비 | 역할 | 예시 |
|------|------|------|
| **TMUX 서버** | JTAG 물리 연결 제어 (라즈베리파이) | TMUX-01 (SSH 접속) |
| **T32 PC** | Lauterbach TRACE32 디버거 실행 (Windows) | WIN-T32-01 (SSH 접속) |
| **텐타클** | 테스트 대상 서버 (T1~T10 등) | T10 |

모든 장비는 Portal의 **Admin > Server Management**에 등록되어 있어야 합니다.

### Admin > T32 탭 설정

1. **Admin** 페이지 → **T32** 탭
2. **Add** 버튼으로 T32 Config 생성:

| 필드 | 설명 | 예시 |
|------|------|------|
| **Server Group** | Lab 선택 | Lab A |
| **JTAG Server** | TMUX 서버 선택 | TMUX-01 |
| **JTAG Username/Password** | TMUX 전용 계정 (비워두면 서버 기본 계정) | |
| **T32 PC** | Windows PC 선택 | WIN-T32-01 |
| **T32 PC Username/Password** | T32 PC 전용 계정 (NAS 접근 등) | |
| **Assigned Servers** | 이 설정이 적용될 텐타클 체크 | T8, T9, T10 |
| **JTAG Command** | JTAG 연결 명령어 | `cd T32linker && echo yes \| python ./tmux_lib.py -n 1 -t {tentacle_num} -s {slot}` |
| **Success Pattern** | 성공 판별 정규식 | `Measured Voltage.*Register Value` |
| **T32 Port Check Command** | T32 Attach 명령어 | `t32rem.exe localhost port=20000 system.mode.attach` |
| **Dump Command** | dump bat 실행 명령어 | `cmd /c C:\T32\dump.bat {tentacle} {slot}` |

### 명령어 플레이스홀더

| 변수 | 예시 | 설명 |
|------|------|------|
| `{tentacle}` | `T10` | 텐타클 이름 그대로 |
| `{tentacle_num}` | `10` | 숫자만 추출 |
| `{slot}` | `1` | 슬롯 번호 |

---

## 2. Dump 실행

### 컨텍스트 메뉴 진입

슬롯 모니터링 페이지에서:
1. 슬롯 **우클릭** → **Debug** → **T32 Dump**
2. 활성화 조건:
   - 단일 슬롯 선택
   - 연결 상태 (connection = 1)
   - 해당 텐타클에 T32 Config가 등록되어 있음

### Dump Dialog

**Idle 화면** — Dump 시작 전:
- 연결 정보 표시 (JTAG 서버, T32 PC)
- Fail 아닌 슬롯: 노란 경고 ("Pre-Command로 Hang을 먼저 걸어주세요")
- **Dump 시작** 버튼

**Stepper 실행** — 4단계 순차 진행:

| Step | 이름 | 동작 |
|------|------|------|
| 1 | **JTAG 연결** | TMUX SSH → `jtagCommand` 실행, `jtagSuccessPattern`으로 성공 판정 |
| 2 | **T32 Attach** | T32 PC SSH → `t32PortCheckCommand` 실행, `Down` 응답이면 Debug Port Fail |
| 3 | **Dump 실행** | T32 PC SSH → `dumpCommand` 실행, stdout 실시간 스트리밍 |
| 4 | **완료** | 결과 경로 표시 + Download 버튼 |

각 Step은:
- 성공해야 다음 Step으로 진행
- 실패 시 로그 자동 펼침 + "다시 시도" 버튼
- 실시간 출력 로그 확인 가능 (펼치기/접기)

### Hang 명령어 (Fail 아닌 슬롯)

Fail 상태가 아닌 슬롯에서 T32 JTAG 연결을 하려면 디바이스를 먼저 정지시켜야 합니다.

```bash
# Pre-Command에 등록할 hang 명령어 예시
nohup adb -s {usbId} shell hang_ufs > /dev/null 2>&1 &
```

- `nohup ... &`로 백그라운드 실행 → SSH 세션 즉시 반환
- USB가 빠지면 adb 프로세스 자동 종료 (잔여 프로세스 없음)

---

## 3. 보안 — 전용 계정

T32Config에 JTAG/T32 PC별 전용 계정을 설정할 수 있습니다:

- **비워두면**: PortalServer에 등록된 기본 계정 사용
- **설정하면**: 해당 계정으로 SSH 접속 (NAS 접근 권한 등)
- 비밀번호는 수정 시 입력하지 않으면 기존 값 유지

---

## 4. 문제 해결

| 증상 | 원인 | 해결 |
|------|------|------|
| T32 Dump 메뉴 비활성화 | T32 Config 미등록 | Admin > T32에서 설정 등록 + Assigned Servers 체크 |
| Step 1 실패 | JTAG 물리 연결 불량 | TMUX 서버 SSH 접속 확인, JTAG 케이블 점검 |
| Step 2 Debug Port Fail | T32 연결 불가 | JTAG 연결 상태 확인, Hang 명령어 실행 여부 |
| Step 3 타임아웃 | dump bat 실행 시간 초과 (5분) | bat 파일 로직 확인, 타임아웃 조정 필요 시 코드 수정 |
| 전용 계정 접속 실패 | 계정 정보 오류 | Admin > T32에서 Username/Password 확인 |
