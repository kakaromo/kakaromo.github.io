---
title: 새 gRPC RPC 추가하기
description: Proto 정의부터 프론트엔드 연동까지 — Agent gRPC 서비스에 새 RPC를 추가하는 전체 워크플로우
---

Go Agent 서버에 새로운 gRPC RPC를 추가하고 Portal에서 호출하는 전체 과정입니다.

## 전체 흐름

```
1. Proto 파일 수정 (Go + Java 양쪽)
2. Go 서버 구현
3. Go proto 컴파일
4. Java proto 컴파일 (Maven)
5. AgentGrpcClient에 메서드 추가
6. Controller 엔드포인트 추가
7. Frontend API 함수 추가
```

---

## Step 1: Proto 파일 수정

**두 개의 proto 파일을 동시에 수정해야 합니다.** 메시지/서비스 정의는 동일하고, `java_package` 옵션만 Java 쪽에 추가됩니다.

### Go Agent proto

```protobuf
// ~/project/agent/proto/agent.proto

service DeviceAgent {
    // ... 기존 RPC들
    rpc NewMethod(NewRequest) returns (NewResponse);
}

message NewRequest {
    string device_id = 1;
    string param = 2;
}

message NewResponse {
    bool success = 1;
    string result = 2;
}
```

### Java Portal proto

```protobuf
// src/main/proto/device_agent.proto

option java_package = "com.samsung.move.agent.grpc";  // ← Java 전용 옵션
option java_outer_classname = "DeviceAgentProto";

service DeviceAgent {
    // ... 동일한 RPC 정의
    rpc NewMethod(NewRequest) returns (NewResponse);
}

message NewRequest {
    string device_id = 1;
    string param = 2;
}

message NewResponse {
    bool success = 1;
    string result = 2;
}
```

:::caution[Proto 동기화 규칙]
- `java_package`, `java_outer_classname` 옵션만 차이가 있어야 함
- 메시지 이름, 필드 번호, 서비스 정의는 **완전히 동일**해야 함
- 한쪽만 수정하면 `UNIMPLEMENTED` 또는 필드 누락 에러 발생
:::

---

## Step 2: Go 서버 구현

Go Agent 서버에서 RPC 핸들러를 구현합니다.

```go
// ~/project/agent/server.go (또는 해당 패키지 파일)

func (s *server) NewMethod(ctx context.Context, req *pb.NewRequest) (*pb.NewResponse, error) {
    deviceID := req.GetDeviceId()
    param := req.GetParam()

    // ADB 명령 실행 등 구현
    result, err := s.executeCommand(deviceID, param)
    if err != nil {
        return nil, status.Errorf(codes.Internal, "command failed: %v", err)
    }

    return &pb.NewResponse{
        Success: true,
        Result:  result,
    }, nil
}
```

---

## Step 3: Go proto 컴파일

```bash
cd ~/project/agent
PATH="$PATH:$HOME/go/bin" protoc \
    --go_out=paths=source_relative:. \
    --go-grpc_out=paths=source_relative:. \
    proto/agent.proto \
    && cp proto/*.go pb/
```

**필요한 도구**:
- `protoc` (Homebrew: `brew install protobuf`)
- `protoc-gen-go`: `go install google.golang.org/protobuf/cmd/protoc-gen-go@latest`
- `protoc-gen-go-grpc`: `go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest`

컴파일 후 `proto/*.go` 파일이 생성되며, `pb/` 디렉토리로 복사합니다.

---

## Step 4: Java proto 컴파일

Maven 빌드 시 `protobuf-maven-plugin`이 자동으로 컴파일합니다.

```bash
cd ~/project/portal
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./mvnw clean install
```

`target/generated-sources/protobuf/` 디렉토리에 Java 클래스가 생성됩니다:
- `DeviceAgentProto.java` — 메시지 클래스 (NewRequest, NewResponse 등)
- `DeviceAgentGrpc.java` — gRPC stub 클래스

:::note[macOS Sequoia 주의]
`protocArtifact` 대신 `protocExecutable=/opt/homebrew/bin/protoc`을 사용해야 합니다. 자세한 내용은 [문제 해결](/developer/troubleshooting) 참조.
:::

---

## Step 5: AgentGrpcClient에 메서드 추가

### Unary RPC (일반 요청-응답)

```java
// agent/grpc/AgentGrpcClient.java

public NewResponse newMethod(String deviceId, String param) {
    NewRequest request = NewRequest.newBuilder()
        .setDeviceId(deviceId)
        .setParam(param)
        .build();

    return blockingStub.newMethod(request);
}
```

### Server Streaming RPC (진행률 등 실시간 데이터)

서버가 여러 메시지를 순차적으로 보내는 스트리밍 RPC:

```java
// 방법 1: blocking iterator (SSE 브릿지용)
public Iterator<ProgressResponse> subscribeProgress(String jobId) {
    ProgressRequest request = ProgressRequest.newBuilder()
        .setJobId(jobId)
        .build();

    return blockingStub.subscribeProgress(request);
}

// 방법 2: async observer (모니터링 등 콜백용)
public void monitorAsync(MonitorRequest request, StreamObserver<MonitorResponse> observer) {
    asyncStub.monitor(request, observer);
}
```

**blockingStub vs asyncStub**:

| 종류 | 용도 | 스레드 모델 |
|------|------|------------|
| `blockingStub` | Unary RPC, SSE 브릿지 (별도 스레드에서 반복) | 호출 스레드 블로킹 |
| `asyncStub` | 모니터링 스트리밍 (콜백 기반) | 논블로킹, gRPC 이벤트 루프에서 콜백 |

---

## Step 6: Controller 엔드포인트 추가

### Unary RPC 래핑

```java
// agent/controller/AgentController.java

@PostMapping("/new-method")
public ResponseEntity<?> newMethod(@RequestBody Map<String, Object> body) {
    Long serverId = ((Number) body.get("serverId")).longValue();
    String deviceId = (String) body.get("deviceId");
    String param = (String) body.get("param");

    AgentServer server = serverService.findById(serverId);
    AgentGrpcClient client = connectionManager.getOrCreate(
        serverId, server.getHost(), server.getPort()
    );

    NewResponse response = client.newMethod(deviceId, param);
    return ResponseEntity.ok(Map.of(
        "success", response.getSuccess(),
        "result", response.getResult()
    ));
}
```

### Server Streaming → SSE 브릿지

gRPC 서버 스트리밍 RPC를 SSE로 변환하는 패턴:

```java
@GetMapping(value = "/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamProgress(@RequestParam String jobId, @RequestParam Long serverId) {
    SseEmitter emitter = new SseEmitter(0L);  // 무제한 타임아웃

    AgentServer server = serverService.findById(serverId);
    AgentGrpcClient client = connectionManager.getOrCreate(
        serverId, server.getHost(), server.getPort()
    );

    Iterator<ProgressResponse> stream = client.subscribeProgress(jobId);

    // 별도 스레드에서 gRPC 스트림 → SSE 변환
    CompletableFuture.runAsync(() -> {
        try {
            while (stream.hasNext()) {
                ProgressResponse progress = stream.next();
                String eventName = progress.getCompleted() ? "complete" : "progress";
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(progress, MediaType.APPLICATION_JSON));
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    return emitter;
}
```

:::tip[SSE 브릿지 패턴 요약]
1. `SseEmitter(0L)` 생성 (무제한 타임아웃)
2. gRPC blocking iterator 생성
3. `CompletableFuture.runAsync()`로 별도 스레드에서 반복
4. 각 gRPC 메시지를 `emitter.send()`로 SSE 이벤트로 변환
5. 스트림 완료 시 `emitter.complete()`
:::

---

## Step 7: 프론트엔드 API 함수

### Unary RPC 호출

```typescript
// frontend/src/lib/api/agent.ts
export function callNewMethod(data: {
    serverId: number;
    deviceId: string;
    param: string;
}): Promise<{ success: boolean; result: string }> {
    return post('/agent/new-method', data);
}
```

### SSE 스트리밍 수신

```typescript
export function createProgressSource(
    jobId: string,
    serverId: number,
    onProgress: (data: ProgressData) => void,
    onComplete: (data: ProgressData) => void,
    onError: (error: Event) => void
): EventSource {
    const source = new EventSource(
        `/api/agent/progress/stream?jobId=${jobId}&serverId=${serverId}`
    );

    source.addEventListener('progress', (e: MessageEvent) => {
        onProgress(JSON.parse(e.data));
    });

    source.addEventListener('complete', (e: MessageEvent) => {
        onComplete(JSON.parse(e.data));
        source.close();  // 완료 시 연결 닫기
    });

    source.onerror = onError;

    return source;  // 호출자가 source.close()로 취소 가능
}
```

---

## Proto 필드 추가 시 주의사항

기존 RPC에 필드를 추가할 때:

| 규칙 | 설명 |
|------|------|
| 새 필드 번호 사용 | 기존 번호 재사용 금지 (역호환 파괴) |
| optional 사용 | 기존 클라이언트가 새 필드를 보내지 않아도 동작해야 함 |
| Go/Java 양쪽 동시 업데이트 | 한쪽만 업데이트하면 필드가 무시됨 |

```protobuf
message NewRequest {
    string device_id = 1;
    string param = 2;
    optional string new_field = 3;  // ← 새 필드는 다음 번호로
}
```

---

## 체크리스트

- [ ] Go proto와 Java proto가 동일한 정의 (java_package만 차이)
- [ ] Go proto 컴파일 완료 (`pb/` 디렉토리에 복사)
- [ ] `./mvnw clean install` 성공 (Java 코드 생성 확인)
- [ ] `AgentGrpcClient`에 메서드 추가
- [ ] Controller 엔드포인트 추가
- [ ] 스트리밍 RPC는 SSE 브릿지 패턴 적용
- [ ] 프론트엔드 API 함수 및 타입 추가
- [ ] Go Agent 서버 재시작
