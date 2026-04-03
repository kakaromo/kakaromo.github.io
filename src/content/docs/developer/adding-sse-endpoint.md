---
title: 새 SSE/WebSocket 엔드포인트 추가하기
description: SseEmitter와 @ServerEndpoint를 사용한 실시간 통신 엔드포인트 추가 가이드
---

Portal에서 실시간 데이터를 전달하기 위한 SSE와 WebSocket 엔드포인트를 추가하는 방법입니다.

## SSE vs WebSocket 선택 기준

| | SSE | WebSocket |
|--|-----|-----------|
| **방향** | 서버 → 클라이언트 (단방향) | 양방향 |
| **자동 재연결** | `EventSource` API 기본 지원 | 수동 구현 필요 |
| **데이터 형식** | 텍스트만 | 텍스트 + 바이너리 |
| **프록시 호환** | HTTP 기반, 호환 우수 | 업그레이드 필요, 일부 프록시 문제 |
| **Portal 사용처** | 슬롯 상태, 벤치마크 진행률, reparse | 원격 터미널 (Guacamole) |

**판단**: 서버에서 클라이언트로만 데이터를 보내면 **SSE**, 클라이언트도 데이터를 보내야 하면 **WebSocket**.

---

## SSE 엔드포인트 추가

### 백엔드 패턴

Portal의 SSE 패턴은 `CopyOnWriteArrayList`로 구독자를 관리하고, `@Scheduled`로 주기적으로 push합니다.

```java
@RestController
@RequestMapping("/api/new-feature")
public class NewFeatureSseController {

    // 1. 구독자 목록 (스레드 안전)
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // 2. SSE 연결 엔드포인트
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);  // 0L = 타임아웃 없음

        emitters.add(emitter);

        // 초기 데이터 전송
        try {
            emitter.send(SseEmitter.event()
                .name("init")
                .data(getCurrentState()));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        // 정리 콜백 등록
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    // 3. 주기적 push (또는 이벤트 기반)
    @Scheduled(fixedDelay = 1000)  // 1초마다
    public void pushUpdates() {
        Object data = getLatestData();
        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("update")
                    .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }
}
```

### 핵심 포인트

| 설정 | 값 | 이유 |
|------|-----|------|
| `SseEmitter(0L)` | 타임아웃 없음 | 장시간 연결 유지 (모니터링 등) |
| `CopyOnWriteArrayList` | 스레드 안전한 리스트 | @Scheduled 스레드에서 반복 중 다른 스레드가 추가/제거 가능 |
| `onCompletion/onTimeout/onError` | 정리 콜백 | 연결 종료 시 emitter 목록에서 제거 |
| `.name("이벤트명")` | 명시적 이벤트 이름 | 프론트엔드에서 `addEventListener`로 선택적 수신 |

### 변형: gRPC 스트리밍 → SSE 브릿지

Go Agent의 gRPC 서버 스트리밍을 SSE로 변환하는 패턴:

```java
@GetMapping(value = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamProgress(@RequestParam String jobId) {
    SseEmitter emitter = new SseEmitter(0L);

    // gRPC blocking iterator를 별도 스레드에서 읽기
    CompletableFuture.runAsync(() -> {
        try {
            Iterator<ProgressResponse> stream = grpcClient.subscribeProgress(jobId);
            while (stream.hasNext()) {
                ProgressResponse msg = stream.next();
                emitter.send(SseEmitter.event()
                    .name(msg.getCompleted() ? "complete" : "progress")
                    .data(msg));
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    return emitter;
}
```

### 프론트엔드 SSE 수신

```typescript
// EventSource 기본 패턴
const source = new EventSource('/api/new-feature/stream');

source.addEventListener('init', (e: MessageEvent) => {
    const data = JSON.parse(e.data);
    state = data;  // 초기 상태 설정
});

source.addEventListener('update', (e: MessageEvent) => {
    const data = JSON.parse(e.data);
    state = data;  // 상태 업데이트
});

source.onerror = () => {
    // EventSource는 기본적으로 자동 재연결
    // 필요시 상태 표시 업데이트
};

// 페이지 이탈 시 정리
onDestroy(() => source.close());
```

:::caution[이벤트 이름과 addEventListener]
`emitter.send(event.name("update"))` 로 이름이 지정된 이벤트는 `addEventListener('update', ...)` 로만 수신 가능합니다. `onmessage` 핸들러는 이름이 **없는** 이벤트만 수신합니다.
:::

---

## WebSocket 엔드포인트 추가

### 백엔드 패턴

Portal은 Jakarta WebSocket API(`@ServerEndpoint`)를 사용합니다.

```java
@ServerEndpoint(
    value = "/api/new-feature/ws",
    configurator = SpringWebSocketConfigurator.class  // Spring DI 지원
)
@Component
public class NewFeatureWebSocketEndpoint {

    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        session.setMaxIdleTimeout(0);  // 무제한 유휴
        session.setMaxTextMessageBufferSize(64 * 1024);  // 64KB
        sessions.add(session);

        // 쿼리 파라미터 읽기
        String param = session.getRequestParameterMap().get("param").get(0);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        // 클라이언트 메시지 처리
        // ...

        // 응답 전송
        session.getBasicRemote().sendText("response");
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        sessions.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        sessions.remove(session);
    }

    // 모든 세션에 브로드캐스트
    public static void broadcast(String message) {
        for (Session s : sessions) {
            if (s.isOpen()) {
                s.getAsyncRemote().sendText(message);
            }
        }
    }
}
```

### 프론트엔드 WebSocket 연결

```typescript
const ws = new WebSocket(`ws://${location.host}/api/new-feature/ws?param=value`);

ws.onopen = () => {
    console.log('Connected');
    ws.send(JSON.stringify({ type: 'subscribe', channel: 'updates' }));
};

ws.onmessage = (e: MessageEvent) => {
    const data = JSON.parse(e.data);
    // 처리
};

ws.onclose = (e: CloseEvent) => {
    console.log('Disconnected:', e.code, e.reason);
    // 수동 재연결 로직 (필요시)
    if (!intentionalClose) {
        setTimeout(() => reconnect(), 3000);
    }
};

// 정리
onDestroy(() => ws.close());
```

---

## Portal의 기존 구현 참조

| 엔드포인트 | 유형 | 파일 | 특징 |
|-----------|------|------|------|
| `/api/head/slots/stream` | SSE | `HeadSseController.java` | version 기반 변경 감지, @Scheduled push |
| `/api/reparse/stream` | SSE | `ReparseController.java` | init/update 이벤트, localStorage 영속화 |
| `/api/agent/benchmark/progress` | SSE | `AgentController.java` | gRPC → SSE 브릿지 |
| `/api/guacamole/tunnel` | WebSocket | `GuacamoleTunnelEndpoint.java` | 양방향 바이너리 중계 |

---

## 체크리스트

### SSE
- [ ] `SseEmitter(0L)` (무제한 타임아웃)
- [ ] `CopyOnWriteArrayList` 구독자 관리
- [ ] `onCompletion/onTimeout/onError` 정리 콜백
- [ ] `.name("이벤트명")` 지정
- [ ] 프론트엔드 `addEventListener` (이벤트명 일치)
- [ ] 페이지 이탈 시 `source.close()`

### WebSocket
- [ ] `@ServerEndpoint` + `SpringWebSocketConfigurator`
- [ ] `session.setMaxIdleTimeout(0)`
- [ ] `ConcurrentHashMap.newKeySet()` 세션 관리
- [ ] `@OnOpen/@OnMessage/@OnClose/@OnError` 핸들러
- [ ] 프론트엔드 수동 재연결 로직
- [ ] 페이지 이탈 시 `ws.close()`
