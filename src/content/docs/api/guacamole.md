---
title: 원격 접속 API
description: xterm.js SSH 터미널 및 Guacamole RDP 원격 접속 API
---

원격 접속 API는 SSH 터미널(xterm.js)과 RDP(Guacamole)를 지원합니다. VPN 없이 Tentacle/HEAD 서버에 직접 접속할 수 있습니다.

## SSH 터미널 (xterm.js)

### WebSocket `/api/terminal/ssh`

xterm.js ↔ JSch SSH 브릿지 WebSocket 엔드포인트입니다.

**연결 URL:**

```
ws://host:8080/api/terminal/ssh?vm=T1&cols=120&rows=40
```

| 파라미터 | 설명 |
|----------|------|
| `vm` | VM 이름 (DB `portal_servers` 조회) |
| `cols` | 터미널 컬럼 수 (기본: 120) |
| `rows` | 터미널 행 수 (기본: 40) |

**메시지 프로토콜:**
- **클라이언트 → 서버**: 텍스트 입력 (UTF-8)
- **서버 → 클라이언트**: SSH 출력 (UTF-8)
- **리사이즈**: `{"type":"resize","cols":120,"rows":40}` JSON 전송

:::note
SSH 접속 정보(IP, 포트, 계정)는 `portal_servers` 테이블에서 관리합니다. Admin 페이지에서 설정합니다.
:::

---

## VM 목록

### GET `/api/guacamole/vms`

사용 가능한 VM(서버) 목록을 반환합니다. DB의 `portal_servers` 테이블에서 `visible=true`인 서버만 반환됩니다.

**응답:**

```json
[
  {
    "name": "T1",
    "ip": "192.168.1.101",
    "sshPort": 22,
    "rdpPort": 3389,
    "connectionType": 3
  },
  {
    "name": "HEAD",
    "ip": "192.168.1.248",
    "sshPort": 22,
    "rdpPort": null,
    "connectionType": 1
  }
]
```

| `connectionType` 값 | 의미 |
|----------------------|------|
| 0 | 접속 불가 |
| 1 | SSH만 |
| 2 | RDP만 |
| 3 | SSH + RDP |

### GET `/api/guacamole/tunnel-url`

WebSocket 터널 URL을 생성합니다.

| 파라미터 | 설명 |
|----------|------|
| `vm` | VM 이름 (T1, T2, HEAD 등) |
| `protocol` | `ssh` 또는 `rdp` |

---

## WebSocket 터널

### WebSocket `/api/guacamole/tunnel`

Guacamole 프로토콜 WebSocket 터널입니다.

**연결 URL:**

```
ws://host:8080/api/guacamole/tunnel?vm=T1&protocol=ssh&width=1024&height=768&sessionId=abc123
```

| 파라미터 | 설명 |
|----------|------|
| `vm` | VM 이름 |
| `protocol` | `ssh` 또는 `rdp` |
| `width` | 화면 너비 (픽셀) |
| `height` | 화면 높이 (픽셀) |
| `sessionId` | 세션 식별자 |

### 프로토콜 핸드셰이크

Guacamole WebSocket 터널은 다음 순서로 핸드셰이크를 수행합니다:

1. **클라이언트**: WebSocket 연결 수립
2. **서버**: `guacd` 데몬(포트 4822)에 TCP 연결
3. **서버**: Guacamole 프로토콜로 `select` instruction 전송 (ssh 또는 rdp)
4. **서버**: `connect` instruction 전송 (호스트, 포트, 인증 정보 포함)
5. **guacd**: 대상 서버에 SSH/RDP 연결 수립
6. **양방향**: WebSocket ↔ guacd 간 Guacamole instruction 릴레이

:::note
guacd 데몬이 포트 4822에서 실행 중이어야 합니다. 서버(VM) 정보는 Admin 페이지에서 관리합니다. VM별로 개별 guacd host/port를 지정할 수 있으며, 미설정 시 글로벌 guacd 설정이 사용됩니다.
:::

### 다중 탭

프론트엔드에서 여러 서버에 동시 접속할 수 있습니다. 각 탭은 독립적인 WebSocket 연결을 유지합니다.
