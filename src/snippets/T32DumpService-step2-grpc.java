// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 603-701
// @note Step 2 gRPC Attach — t32remote.attach(t32_version 검증) + STATE.TARGET() 타겟 판정
// @synced 2026-06-22T22:22:10.908Z

    private boolean executeStep2AttachGrpc(SseEmitter emitter, T32Config config, PortalServer t32Pc) {
        String host = config.getT32RemoteHost();
        int port = config.getT32RemotePort();
        log.info("[T32Dump] Step2(gRPC) attach 대상 t32remote={}:{}", host, port);

        // PowerView 는 콘솔 세션에 미리 떠 있다고 가정하고 attach 만 한다.
        // ping/attach 결과와 무관하게 같은 Session 으로 STATE.TARGET() 까지 검증해야 하므로
        // 항상 Session 을 먼저 연다.
        // attach 시 t32remote 가 PowerView 에 붙는 RCL(NETASSIST) 포트는 admin 의
        // t32PortCheckCommand 필드에 넣은 포트 번호를 쓴다. 숫자가 아니거나 비면 기본값(20000).
        int rclPort = parsePort(config.getT32PortCheckCommand());
        log.info("[T32Dump] Step2(gRPC) RCL 포트(t32PortCheckCommand)={}", rclPort > 0 ? rclPort : "기본값");

        try (T32RemoteClient.Session s = t32RemoteClient.open(host, port)) {
            String attachInfo;
            try {
                // ping 은 t32remote 가 살아있는지만 본다(attach 여부 무관 — 미attach 여도 OK
                // 반환). 그래서 ping 으로 attach 를 건너뛰지 않고, 항상 attach 를 호출한다.
                // t32remote 의 Attach 는 idempotent(이미 붙어있으면 그대로 성공)하고,
                // 응답의 t32_version 이 비어있으면 PowerView 가 실제로 안 붙은 것이다.
                AttachResponse resp = t32RemoteClient.attach(s, rclPort);
                String version = resp.getT32Version();
                log.info("[T32Dump] Step2(gRPC) attach 응답 t32_version={}", version);
                if (version == null || version.isBlank()) {
                    log.warn("[T32Dump] Step2(gRPC) FAIL: attach 응답에 t32_version 없음 (PowerView 미attach)");
                    sendEvent(emitter, "step-done", Map.of("step", 2, "status", "failed",
                            "output", "Attach 응답에 t32_version 없음\n"
                                    + "→ T32 PowerView 가 실행 중인지, RCL(NETASSIST PORT=20000) 설정이 있는지 확인 필요"));
                    return false;
                }
                attachInfo = "Attach OK (t32_version=" + version + ")";
            } catch (StatusRuntimeException attachEx) {
                // ping/attach RPC 자체 실패 = t32remote 가 T32 PowerView 에 못 붙음 (= 미실행/RCL 문제)
                log.warn("[T32Dump] Step2(gRPC) FAIL: ping/attach RPC 예외 status={} desc={}",
                        attachEx.getStatus().getCode(), attachEx.getStatus().getDescription(), attachEx);
                sendEvent(emitter, "step-done", Map.of("step", 2, "status", "failed",
                        "output", "T32 PowerView 연결 실패 (" + attachEx.getStatus().getCode() + ")\n"
                                + "→ T32 PowerView 가 실행되지 않았거나 RCL(NETASSIST PORT=20000) 설정이 없습니다.\n"
                                + "   T32 PC 에서 PowerView 실행 상태와 config.t32 의 RCL 설정을 확인하세요."));
                return false;
            }

            // ── 타겟 상태 검증 (STATE.TARGET() 에 error → dump 중단) ──
            // attach 직후 STATE.TARGET() 평가가 일부 환경에서 PowerView 의 USB 재연결
            // ("try USB connect")을 유발해 fatal #FF 를 내는 경우가 있다. 그런 환경은
            // T32_TARGET_CHECK=false 로 이 검증을 끄면 attach 성공으로 바로 진행한다.
            if ("false".equalsIgnoreCase(System.getenv("T32_TARGET_CHECK"))) {
                log.info("[T32Dump] Step2(gRPC) SUCCESS: {} (STATE.TARGET 검증 생략: T32_TARGET_CHECK=false)", attachInfo);
                sendEvent(emitter, "step-done", Map.of("step", 2, "status", "success",
                        "output", attachInfo + "\n(STATE.TARGET 검증 생략)"));
                return true;
            }
            String target;
            try {
                target = t32RemoteClient.eval(s, "STATE.TARGET()");
                log.info("[T32Dump] Step2(gRPC) STATE.TARGET()={}", target);
            } catch (Exception evalEx) {
                // eval RPC 자체 실패(구버전 t32remote / 일시 오류)는 soft — 검증 생략하고 진행
                log.warn("[T32Dump] STATE.TARGET() eval 실패 (검증 생략): {}", evalEx.getMessage());
                sendEvent(emitter, "step-done", Map.of("step", 2, "status", "success",
                        "output", attachInfo + "\n(상태 검증 생략: " + evalEx.getMessage() + ")"));
                return true;
            }

            if (isTargetError(target)) {
                // PowerView 는 떠 있으나 타겟 디버그 연결 실패
                log.warn("[T32Dump] Step2(gRPC) FAIL: STATE.TARGET()='{}' 에 에러 신호 → 타겟 디버그 연결 실패", target);
                sendEvent(emitter, "step-done", Map.of("step", 2, "status", "failed",
                        "output", attachInfo + "\nSTATE.TARGET()=" + target
                                + "\n→ T32 PowerView 는 실행 중이나 타겟 디버그(JTAG) 연결에 실패했습니다.\n"
                                + "   JTAG 케이블/전원/디버그 포트를 확인하세요."));
                return false;
            }
            log.info("[T32Dump] Step2(gRPC) SUCCESS: {} STATE.TARGET()={}", attachInfo, target);
            sendEvent(emitter, "step-done", Map.of("step", 2, "status", "success",
                    "output", attachInfo + "\nSTATE.TARGET()=" + target));
            return true;
        } catch (Exception e) {
            log.error("[T32Dump] Step2(gRPC) 예외(Session open 등): {}", e.getMessage(), e);
            sendEvent(emitter, "step-done", Map.of("step", 2, "status", "failed",
                    "output", "오류: " + e.getMessage()));
            return false;
        }
    }

    /**
     * t32PortCheckCommand 에 적어둔 RCL 포트 번호를 파싱한다. 순수 숫자(앞뒤 공백 허용)
     * 면 그 값, 그 외(빈값·명령어 등)면 0 을 돌려준다(호출 측에서 기본 포트로 폴백).
     */
    private int parsePort(String s) {
        if (s == null) return 0;
        String t = s.trim();
        try {
            int p = Integer.parseInt(t);
            return (p > 0 && p <= 65535) ? p : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
