---
title: Guacamole 원격 접속
description: Apache Guacamole 통합을 통한 웹 기반 SSH/RDP 원격 접속 아키텍처, 백엔드/프론트엔드 구현, 보안 고려사항을 설명합니다.
---

## 아키텍처

Apache Guacamole은 클라이언트리스 원격 데스크톱 게이트웨이입니다. 웹 브라우저만으로 SSH, RDP 등의 원격 연결을 지원합니다.

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
    SSH / RDP
    |
Tentacle Server (T1, T2, T3, T4, HEAD)
```

### 구성 요소

| 구성 요소 | 설명 |
|-----------|------|
| guacd (Guacamole Daemon) | 실제 원격 프로토콜(SSH, RDP)을 처리하는 데몬 (192.168.1.248:4822) |
| Spring Boot WebSocket Endpoint | 브라우저와 guacd 간의 WebSocket 터널 역할 |
| Guacamole JavaScript Client | 브라우저에서 실행. 화면 렌더링, 키보드/마우스 입력 처리 |

## 백엔드

### GuacamoleTunnelEndpoint

`@ServerEndpoint`로 WebSocket 엔드포인트를 구현합니다.

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

guacd 데몬 연결 설정만 보유합니다. VM 정보는 DB `portal_servers` 테이블에서 관리합니다.

```java
@ConfigurationProperties(prefix = "guacamole")
public class GuacamoleProperties {
    private String guacdHost;
    private int guacdPort;
}
```

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

## 프론트엔드

### GuacamoleClient.svelte

`guacamole-common-js` 라이브러리를 사용하여 WebSocket 터널과 Guacamole 클라이언트를 생성합니다.

**주요 기능:**
- WebSocket URL 생성 (`ws://` 또는 `wss://`)
- `Guacamole.WebSocketTunnel` + `Guacamole.Client` 초기화
- 키보드 입력 설정 (각 터미널 독립)
- 텍스트 전송 함수 (Broadcast용 `sendText()`, `sendEnter()`)
- 연결 상태 추적

### TerminalDialog.svelte

다중 터미널 탭 UI를 제공합니다.
- VM 선택 (T1~T4, HEAD)
- 프로토콜 선택 (SSH/RDP)
- 탭별 독립 터미널 인스턴스
- Broadcast 명령어: 모든 열린 터미널에 동시 입력

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
