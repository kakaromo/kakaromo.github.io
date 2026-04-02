---
title: 원격 접속 아키텍처
description: xterm.js SSH 터미널과 Guacamole RDP 원격 접속 아키텍처, 백엔드/프론트엔드 구현, 보안 고려사항을 설명합니다.
---

## 아키텍처

원격 접속은 두 가지 방식을 지원합니다:

### SSH 터미널 (xterm.js) -- 기본

```
Browser (XtermClient.svelte / xterm.js)
    |
    WebSocket (/api/terminal/ssh)
    |
Spring Boot (SshTerminalEndpoint)
    |
    JSch SSH session
    |
Tentacle Server (T1, T2, T3, T4, HEAD)
```

- 브라우저 네이티브 텍스트 선택 + Cmd+C/V 복사/붙여넣기
- DOM 기반 렌더링으로 자연스러운 UX
- `terminal` 패키지: `SshTerminalEndpoint`, `SshConnectionService`, `SshConnectionInfo`

### RDP (Guacamole) -- GUI 데스크톱

```
Browser (GuacamoleClient.svelte)
    |
    WebSocket (/api/guacamole/tunnel)
    |
Spring Boot (GuacamoleTunnelEndpoint)
    |
    Guacamole Protocol (TCP)
    |
guacd (4822)
    |
    RDP
    |
Tentacle Server
```

### 구성 요소

| 구성 요소 | 설명 |
|-----------|------|
| xterm.js | 브라우저 터미널 에뮬레이터. SSH 접속용 |
| JSch | Java SSH 라이브러리. WebSocket ↔ SSH 브릿지 |
| guacd (Guacamole Daemon) | RDP 프로토콜 처리 데몬 (192.168.1.248:4822) |
| Guacamole JavaScript Client | RDP 화면 렌더링, 키보드/마우스 입력 처리 |

## 백엔드

### SSH 터미널 (terminal 패키지)

```
terminal/
├── SshTerminalEndpoint.java    # WebSocket 엔드포인트
├── SshConnectionService.java   # JSch SSH 연결 서비스
└── SshConnectionInfo.java      # SSH 연결 정보 레코드
```

`SshTerminalEndpoint`는 WebSocket 메시지를 SSH 채널로 중계합니다. SSH 연결 로직은 `SshConnectionService`에 위임합니다.

**resize 프로토콜**: 클라이언트에서 `{"type":"resize","cols":120,"rows":40}` JSON을 전송하면 PTY 크기를 변경합니다.

### Guacamole RDP (guacamole 패키지)

```
guacamole/
├── config/       GuacamoleProperties
├── controller/   GuacamoleController
├── dto/          VmInfo
├── endpoint/     GuacamoleTunnelEndpoint, GuacamoleProxyEndpoint
├── tunnel/       SessionLockManager
└── service/      GuacamoleService, GuacamoleApiService
```

`GuacamoleTunnelEndpoint`는 `@ServerEndpoint`로 WebSocket 엔드포인트를 구현합니다.

```java
@ServerEndpoint(value = "/api/guacamole/tunnel",
                subprotocols = "guacamole",
                configurator = SpringWebSocketConfigurator.class)
public class GuacamoleTunnelEndpoint {
    // ...
}
```

**동작 과정:**
1. 쿼리 파라미터에서 VM 정보 추출 (`vm`, `protocol`)
2. VM 설정 DB(`portal_servers`)에서 조회
3. Guacamole 설정 생성 (hostname, username, password 등)
4. guacd에 연결 (`InetGuacamoleSocket`)
5. 터널 생성 및 리더 스레드 시작
6. 양방향 메시지 중계: 브라우저 ↔ guacd

### GuacamoleProperties

글로벌 guacd 데몬 연결 설정을 보유합니다. VM 정보는 DB `portal_servers` 테이블에서 관리합니다.

```java
@ConfigurationProperties(prefix = "guacamole")
public class GuacamoleProperties {
    private String guacdHost;
    private int guacdPort;
}
```

### VM별 guacd 설정

`portal_servers` 테이블의 `guacd_host`, `guacd_port` 컬럼으로 VM별 개별 guacd를 지정할 수 있습니다. 설정하지 않으면 `GuacamoleProperties`의 글로벌 값을 사용합니다.

```java
// VM별 guacd 설정이 있으면 사용, 없으면 글로벌 fallback
String guacdHost = vmConfig.getGuacdHost() != null ? vmConfig.getGuacdHost() : properties.getGuacdHost();
int guacdPort = vmConfig.getGuacdPort() != null ? vmConfig.getGuacdPort() : properties.getGuacdPort();
```

:::tip
여러 네트워크 세그먼트에 guacd가 분산 배치된 환경에서, 각 VM이 가까운 guacd를 사용하도록 설정할 수 있습니다. Admin 페이지의 Server Management에서 guacd Host/Port를 설정합니다.
:::

### WebSocket 안정성 설정

```java
@OnOpen
public void onOpen(Session session, EndpointConfig config) {
    session.setMaxIdleTimeout(0);              // idle timeout 비활성화
    session.setMaxTextMessageBufferSize(1024 * 1024); // 1MB 버퍼
}
```

:::caution
`maxIdleTimeout`을 설정하지 않으면 기본값(30초)으로 인해 유휴 상태에서 WebSocket이 close code 0으로 끊기는 문제가 발생합니다.
:::

## SessionLockManager

VM 단위의 배타적 접근 제어를 담당하는 컴포넌트입니다. 한 VM에 대해 한 명의 사용자만 RDP/VNC 세션을 가질 수 있도록 보장합니다.

### 설계 목적

- **1VM = 1사용자**: 동일 VM에 대해 동시에 하나의 RDP/VNC 연결만 허용
- **접속 시도 알림**: 다른 사용자가 접속을 시도하면 현재 사용자에게 알림 전달
- **비정상 종료 대응**: Heartbeat 기반 타임아웃으로 좀비 Lock 자동 해제

### 데이터 구조

```java
// VM별 Lock 정보
ConcurrentHashMap<String, LockInfo> locks;      // vmName → LockInfo

// VM별 접속 시도 알림 큐
ConcurrentHashMap<String, List<AttemptInfo>> attempts;  // vmName → 시도 목록
```

### 레코드 정의

| 레코드 | 필드 | 설명 |
|--------|------|------|
| `LockInfo` | `user` | Lock을 보유한 사용자 |
| | `protocol` | 접속 프로토콜 (rdp, vnc) |
| | `lockedAt` | Lock 획득 시각 |
| | `lastHeartbeat` | 마지막 Heartbeat 시각 |
| `AttemptInfo` | `user` | 접속을 시도한 사용자 |
| | `attemptedAt` | 시도 시각 |

### 주요 메서드

| 메서드 | 반환 | 동작 |
|--------|------|------|
| `tryAcquire(vmName, user, protocol)` | `null` (성공) / `LockInfo` (거부) | Lock 획득 시도. 만료된 Lock은 자동 해제 후 재시도. 거부 시 attempts 큐에 기록 |
| `release(vmName, user)` | void | Lock 해제 (본인 Lock만 해제 가능) |
| `forceRelease(vmName)` | void | 강제 Lock 해제 (관리자/시스템용, 소유자 체크 없음) |
| `heartbeat(vmName, user)` | void | Heartbeat 갱신 -- `lastHeartbeat`를 현재 시각으로 업데이트 |
| `pollAttempts(vmName)` | `List<AttemptInfo>` | 접속 시도 알림 가져오기 (가져오면 큐에서 제거) |
| `hasAttempts(vmName)` | boolean | 접속 시도가 있는지 확인 (제거하지 않음) |
| `getLock(vmName)` | `LockInfo` / null | 현재 Lock 상태 조회 |
| `getAllLocks()` | `Map<String, LockInfo>` | 전체 Lock 상태 조회 (불변 맵) |

### Lock 획득 흐름

```
tryAcquire(vm, userB, rdp)
    │
    ├─ Lock 없음 → LockInfo 생성, 반환 null (성공)
    │
    ├─ Lock 있음, 같은 사용자 → LockInfo 갱신, 반환 null (성공)
    │
    └─ Lock 있음, 다른 사용자
         │
         ├─ Lock 만료 (lastHeartbeat > 5분) → 자동 해제 후 새 Lock 획득
         │
         └─ Lock 유효 → attempts 큐에 기록, 기존 LockInfo 반환 (거부)
```

### 자동 정리 (Auto-Cleanup)

```java
@Scheduled(fixedRate = 60_000)  // 1분마다 실행
public void cleanupExpiredLocks() {
    // lastHeartbeat로부터 5분(LOCK_TIMEOUT) 경과한 Lock 제거
}
```

- `@Scheduled`로 60초마다 전체 Lock을 순회
- `lastHeartbeat`로부터 5분이 지난 Lock을 자동 해제
- 해당 VM의 attempts 큐도 함께 정리

:::note
`@EnableScheduling`이 `PortalApplication`에 설정되어 있어야 `@Scheduled` 메서드가 동작합니다.
:::

### GuacamoleTunnelEndpoint 연동

`GuacamoleTunnelEndpoint`의 WebSocket 생명주기에서 `SessionLockManager`를 호출합니다:

| WebSocket 이벤트 | SessionLockManager 호출 |
|-------------------|------------------------|
| `@OnOpen` | `tryAcquire()` -- 실패 시 WebSocket 즉시 close |
| 메시지 수신 중 (주기적) | `heartbeat()` -- 연결이 살아있음을 알림 |
| `@OnClose` | `release()` -- Lock 해제 |
| 에러/비정상 종료 | 자동 정리가 5분 후 Lock 해제 |

:::caution
`forceRelease()`는 소유자 체크 없이 Lock을 해제합니다. Admin API나 시스템 내부에서만 사용해야 하며, 일반 사용자 API에 노출하지 않아야 합니다.
:::

## 프론트엔드

### XtermClient.svelte (SSH)

xterm.js 기반 터미널 컴포넌트입니다. `@xterm/xterm`, `@xterm/addon-fit`, `@xterm/addon-web-links`를 사용합니다.

**주요 기능:**
- WebSocket `/api/terminal/ssh`에 연결
- 브라우저 네이티브 텍스트 선택 + Cmd+C 복사
- Cmd+V 붙여넣기 (클립보드 → WebSocket)
- `FitAddon` + `ResizeObserver`로 자동 리사이즈
- 텍스트 전송 함수: `sendText()`, `sendEnter()`, `close()`

### GuacamoleClient.svelte (RDP)

`guacamole-common-js` 라이브러리를 사용하여 WebSocket 터널과 Guacamole 클라이언트를 생성합니다. RDP 접속에 사용됩니다.

### TerminalDialog.svelte

다중 터미널 탭 UI를 제공합니다. 기본적으로 `XtermClient`를 사용합니다.
- 탭별 독립 터미널 인스턴스
- Broadcast 명령어: 모든 열린 터미널에 동시 입력
- 전체화면 토글
- 슬롯 우클릭 시 자동 ADB shell 접속 (usbId가 있는 경우)

## SSH 설정

```java
guacConfig.setProtocol("ssh");
guacConfig.setParameter("hostname", "192.168.1.10");
guacConfig.setParameter("port", "22");
guacConfig.setParameter("username", "samsung");
guacConfig.setParameter("password", "password");
guacConfig.setParameter("font-size", "12");
guacConfig.setParameter("color-scheme", "gray-black");
guacConfig.setParameter("width", "1920");
guacConfig.setParameter("height", "1080");
```

## RDP 설정

```java
guacConfig.setProtocol("rdp");
guacConfig.setParameter("hostname", "192.168.1.10");
guacConfig.setParameter("port", "3389");
guacConfig.setParameter("username", "user");
guacConfig.setParameter("password", "password");
guacConfig.setParameter("width", "1920");
guacConfig.setParameter("height", "1080");
guacConfig.setParameter("ignore-cert", "true");
guacConfig.setParameter("security", "any");
guacConfig.setParameter("resize-method", "display-update");
// 성능 최적화
guacConfig.setParameter("enable-wallpaper", "false");
guacConfig.setParameter("enable-theming", "false");
guacConfig.setParameter("disable-audio", "true");
guacConfig.setParameter("color-depth", "16");
```

## 보안 고려사항

### 인증

VM 접속 정보는 DB(`portal_servers` 테이블)에 저장되며, Admin 페이지에서 관리합니다.

:::note
프로덕션 환경에서는 비밀번호 암호화 저장, 사용자별 권한 검증 추가, 세션 기반 인증 연동을 고려하세요.
:::

### 네트워크

- guacd와 Spring Boot 서버 간 내부 네트워크 사용
- HTTPS/WSS 사용 권장
- 방화벽으로 guacd 포트 외부 접근 차단

### 감사 로그

- 연결/해제 시점 로깅
- Reader thread에서 발생하는 예외를 유형별로 분리하여 디버깅 용이

### 의존성

**Maven:**
```xml
<dependency>
    <groupId>org.apache.guacamole</groupId>
    <artifactId>guacamole-common</artifactId>
    <version>1.5.4</version>
</dependency>
```

**NPM:**
```json
{
    "dependencies": {
        "guacamole-common-js": "^1.5.0"
    }
}
```
