---
title: Guacamole API
description: Apache Guacamole 기반 원격 접속(SSH/RDP) VM 목록 조회 및 WebSocket 터널 API
---

Guacamole API는 Apache Guacamole 프로토콜을 통해 브라우저에서 SSH/RDP 원격 접속을 지원합니다. VPN 없이 Tentacle/HEAD 서버에 직접 접속할 수 있습니다.

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
guacd 데몬이 포트 4822에서 실행 중이어야 합니다. 서버(VM) 정보는 Admin 페이지에서 관리합니다.
:::

### 다중 탭

프론트엔드에서 여러 서버에 동시 접속할 수 있습니다. 각 탭은 독립적인 WebSocket 연결을 유지합니다.
