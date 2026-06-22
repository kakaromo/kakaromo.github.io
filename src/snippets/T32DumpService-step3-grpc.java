// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 807-924
// @note Step 3 gRPC Dump — core별 CD.DO + 스크린샷/Canary 후처리 + zip
// @synced 2026-06-22T22:22:10.908Z

    private String executeStep3DumpGrpc(SseEmitter emitter, T32Config config, PortalServer t32Pc,
                                        String branchWindowsPath, String resultLinuxPath, String resultWindowsPath) {
        List<CoreScript> scripts = parseCoreScripts(config.getCoreScriptsJson());
        log.info("[T32Dump] Step3(gRPC) coreScripts 파싱: {}개 cores={}",
                scripts.size(), scripts.stream().map(CoreScript::core).toList());
        if (scripts.isEmpty()) {
            sendEvent(emitter, "step-done", Map.of("step", 3, "status", "failed",
                    "output", "coreScriptsJson 이 비어있거나 형식이 잘못됨"));
            return null;
        }
        String host = config.getT32RemoteHost();
        int port = config.getT32RemotePort();
        String winBase = branchWindowsPath;   // fwCodeWindowsBase\{FW 경로} 까지 포함
        StringBuilder accumulated = new StringBuilder();
        try (T32RemoteClient.Session s = t32RemoteClient.open(host, port)) {
            try {
                for (CoreScript cs : scripts) {
                    // 사용자 중단(interrupt) 시 다음 core 로 넘어가지 않고 즉시 종료한다.
                    // (try-with-resources 가 세션을 close, 워커 finally 가 lock 해제)
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("dump 중단됨");
                    }
                    // 경로 끝에 개행·공백이 섞이면 CD.DO "경로\n" 이 되어 깨진다(네트워크
                    // 경로에서 특히 치명적). 양쪽 모두 strip 한다.
                    String relPath = cs.cmmRelPath() == null ? "" : cs.cmmRelPath().strip();
                    String base = winBase == null ? "" : winBase.strip();
                    String cmmPath = base.isBlank()
                            ? relPath
                            : base.replaceAll("[/\\\\]$", "") + "\\" + relPath.replace("/", "\\");

                    // 한 줄 = exec 명령어 하나. CD.DO 를 먼저 보내고, optionalCommands 의
                    // 각 줄을 개별 ExecuteCommand 로 순차 전송한다(멀티라인을 한 번에 보내지
                    // 않음 — T32_Cmd 는 한 번에 한 PRACTICE 명령이 정석). 빈 줄은 건너뛴다.
                    List<String> cmds = new java.util.ArrayList<>();
                    cmds.add("CD.DO \"" + cmmPath + "\"");
                    if (cs.optionalCommands() != null && !cs.optionalCommands().isBlank()) {
                        for (String line : cs.optionalCommands().split("\\r?\\n")) {
                            String t = line.strip();
                            if (!t.isEmpty()) cmds.add(t);
                        }
                    }

                    sendEvent(emitter, "dump-progress", Map.of(
                            "step", 3, "core", cs.core(), "phase", "dump", "status", "running"));

                    // Canary 폴더 경로(T32 PC) 와, cmm 실행 전 원본 항목 스냅샷.
                    // cmm 이 만든 폴더만 나중에 지우기 위해 baseline 을 먼저 잡는다.
                    String canaryDir = null;
                    java.util.Set<String> canaryBaseline = java.util.Collections.emptySet();
                    if (isCanary(cs.core())) {
                        // cmm 과 동일하게 Z: 경로를 쓴다. 후처리는 t32remote(RunCommand)에서 실행.
                        // 경로에 보이지 않는 제어문자(\r 등)가 섞이면 cmd 가 "구문이 잘못되었습니다"
                        // 를 내므로 제어문자를 모두 제거한다.
                        String cleanBase = base.replaceAll("\\p{Cntrl}", "").replaceAll("[/\\\\]$", "");
                        canaryDir = cleanBase + "\\00_BUILD\\00_SIMULATOR\\CANARY";
                        log.info("[T32Dump] Canary 폴더 경로=[{}]", canaryDir);
                        canaryBaseline = snapshotCanary(s, canaryDir);
                        log.info("[T32Dump] Canary baseline ({}개): {}", canaryBaseline.size(), canaryBaseline);
                    }

                    boolean ok = true;
                    for (int ci = 0; ci < cmds.size(); ci++) {
                        String oneCmd = cmds.get(ci);
                        log.info("[T32Dump] Step3(gRPC) core={} executeCommand 전송: {}", cs.core(), oneCmd);
                        ok = t32RemoteClient.executeCommand(s, oneCmd, DUMP_TIMEOUT_SECONDS, ev -> {
                            String line = formatEvent(ev, cs.core());
                            if (line != null) {
                                accumulated.append(line).append("\n");
                                sendEvent(emitter, "step-output", Map.of("step", 3, "line", line));
                            }
                        });
                        log.info("[T32Dump] Step3(gRPC) core={} cmd 결과={}: {}", cs.core(), ok ? "OK" : "FAIL", oneCmd);
                        if (!ok) break;   // 한 명령이라도 실패하면 이 core 중단
                        // cmm 실행(CD.DO) 뒤에만 5초 대기(뒤에 명령이 더 있을 때). 그 외
                        // 보조 명령들 사이엔 대기하지 않는다.
                        if (oneCmd.toUpperCase().startsWith("CD.DO") && ci < cmds.size() - 1) {
                            Thread.sleep(5000);
                        }
                    }
                    String finalStatus = ok ? "done" : "failed";
                    sendEvent(emitter, "dump-progress", Map.of(
                            "step", 3, "core", cs.core(), "phase", "dump", "status", finalStatus));
                    if (!ok) {
                        sendEvent(emitter, "step-done", Map.of("step", 3, "status", "failed",
                                "output", accumulated + "\n[FAIL] core=" + cs.core() + " cmm=" + cmmPath));
                        return null;
                    }

                    if (isCanary(cs.core())) {
                        // Canary 는 cmm 이 폴더 생성·dump·report 까지 진행한다. report/*.html
                        // 생성을 완료로 보고(10분 timeout=fail) → Canary 폴더 전체를 result 로
                        // 복사 → 성공 시 baseline(원본) 제외 새로 생긴 항목 삭제.
                        finishCanary(emitter, s, canaryDir, canaryBaseline, resultWindowsPath);
                    } else {
                        // 그 외 core 는 dump 직후 전체 PowerView 창을 캡처해 result 폴더에 저장
                        captureCoreScreenshot(emitter, s, cs.core(), resultLinuxPath);
                    }
                }
                // 모든 core dump/캡처/Canary 후처리가 끝난 result 폴더를 T32 PC 에서 zip 한다.
                // 이 세션(s)은 cmm 과 같은 Z: 매핑을 쓰므로 net use 문제 없음(snapshotCanary 주석 참고).
                zipResultFolder(emitter, s, resultWindowsPath);
                sendEvent(emitter, "step-done", Map.of("step", 3, "status", "success",
                        "output", accumulated.toString()));
                return config.getResultBasePath() != null ? config.getResultBasePath() : "";
            } finally {
                t32RemoteClient.detach(s);   // 성공/실패 무관 — t32remote 세션 정리
            }
        } catch (InterruptedException ie) {
            // 사용자 중단: 인터럽트 플래그를 복원해 워커 바깥 catch 가 '중단'으로 처리하게 한다.
            Thread.currentThread().interrupt();
            log.info("[T32Dump] Step3(gRPC) 중단됨");
            return null;
        } catch (Exception e) {
            sendEvent(emitter, "step-done", Map.of("step", 3, "status", "failed",
                    "output", "오류: " + e.getMessage()));
            return null;
        }
    }
