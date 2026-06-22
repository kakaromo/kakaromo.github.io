// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 172-396
// @note executeDump — lock 획득 → 워커 submit → Step1~4 디스패치(gRPC/SSH) → finally lock 해제
// @synced 2026-06-22T22:22:10.907Z

    public SseEmitter executeDump(Long serverId, String tentacleName, int slotNumber, String fw, String branchPath,
                                     String setLocation, String testToolName, String testTrName,
                                     Long historyId, String testType, String source,
                                     String userKey, String userDisplayName) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10분 timeout

        log.info("[T32Dump] executeDump 진입: serverId={} tentacle={} slot={} fw={} branchPath={} setLocation={} testTool={} testTr={} historyId={} testType={} source={} user={}",
                serverId, tentacleName, slotNumber, fw, branchPath, setLocation, testToolName, testTrName, historyId, testType, source, userDisplayName);

        // ── 단독 점유 lock (RDP 단독접속 방식) ──
        // configId 단위로 잠근다. 같은 T32 PC 를 공유하는 다른 slot 의 동시 dump 도 막아야 하므로
        // serverId 가 아니라 configId 로 키를 잡는다. submit 전에 획득해야 동시 실행을 차단한다.
        final Long lockConfigId = resolveConfigId(serverId);
        Optional<T32DumpLockService.Holder> busy = lockService.tryAcquire(lockConfigId, userKey, userDisplayName);
        if (busy.isPresent()) {
            T32DumpLockService.Holder h = busy.get();
            // 거부: 진행하지 않고 점유자를 알려준 뒤 즉시 종료.
            sendEvent(emitter, "locked", Map.of(
                    "lockedBy", h.displayName() == null ? "다른 사용자" : h.displayName(),
                    "since", h.since().toString()));
            emitter.complete();
            return emitter;
        }

        // ── 인코딩 진단 (한글 깨짐 원인 추적용, 확인 후 제거) ──
        // 하드코딩 한글 리터럴이 컴파일 단계에서 '?'(0x3F) 로 박제됐는지, 아니면
        // JVM 기본 charset 이 UTF-8 이 아닌지를 회사 환경 로그에서 바로 가린다.
        String probe = "한글연결실패";
        log.info("[T32Dump][ENC] file.encoding={} defaultCharset={} sun.jnu.encoding={}",
                System.getProperty("file.encoding"),
                java.nio.charset.Charset.defaultCharset(),
                System.getProperty("sun.jnu.encoding"));
        log.info("[T32Dump][ENC] probe='{}' len={} bytes(UTF-8)={} bytes(default)={}",
                probe, probe.length(),
                bytesToHex(probe.getBytes(StandardCharsets.UTF_8)),
                bytesToHex(probe.getBytes(java.nio.charset.Charset.defaultCharset())));

        // 워커 finally 에서 running 맵의 자기 엔트리만 정확히 제거하기 위한 식별자 홀더.
        // future 생성 후에 채워지며, 람다는 배열을 통해 그 값을 본다(effectively-final 우회).
        final RunningDump[] runningRef = new RunningDump[1];

        java.util.concurrent.Future<?> future = executor.submit(() -> {
            try {
                // T32Config 조회
                log.info("[T32Dump] 1) T32Config 매핑 조회: serverId={}", serverId);
                T32ConfigServer mapping = configServerRepository.findByServerId(serverId)
                        .orElseThrow(() -> new IllegalArgumentException("T32 설정을 찾을 수 없습니다 (serverId=" + serverId + ")"));
                log.info("[T32Dump]    매핑 OK: t32ConfigId={}", mapping.getT32ConfigId());
                T32Config config = configRepository.findById(mapping.getT32ConfigId())
                        .orElseThrow(() -> new IllegalArgumentException("T32 설정이 존재하지 않습니다"));
                log.info("[T32Dump]    config OK: id={} desc={} jtagServerId={} t32PcId={} t32RemoteHost={} t32RemotePort={} coreScriptsJson={}",
                        config.getId(), config.getDescription(), config.getJtagServerId(), config.getT32PcId(),
                        config.getT32RemoteHost(), config.getT32RemotePort(),
                        config.getCoreScriptsJson() == null ? "null" : "len=" + config.getCoreScriptsJson().length());

                PortalServer jtagServer = serverRepository.findById(config.getJtagServerId())
                        .orElseThrow(() -> new IllegalArgumentException("JTAG 서버를 찾을 수 없습니다"));
                PortalServer t32Pc = serverRepository.findById(config.getT32PcId())
                        .orElseThrow(() -> new IllegalArgumentException("T32 PC를 찾을 수 없습니다"));
                log.info("[T32Dump]    jtagServer={}({}) t32Pc={}({})",
                        jtagServer.getName(), jtagServer.getIp(), t32Pc.getName(), t32Pc.getIp());

                // T32Config에 커스텀 계정이 있으면 덮어쓰기 (원본 PortalServer는 변경하지 않음)
                boolean jtagCustom = config.getJtagUsername() != null && !config.getJtagUsername().isBlank();
                boolean t32PcCustom = config.getT32PcUsername() != null && !config.getT32PcUsername().isBlank();
                jtagServer = applyCustomAccount(jtagServer, config.getJtagUsername(), config.getJtagPassword());
                t32Pc = applyCustomAccount(t32Pc, config.getT32PcUsername(), config.getT32PcPassword());
                log.info("[T32Dump]    SSH 계정 jtag: user={} ({}), pw={}",
                        jtagServer.getUsername(), jtagCustom ? "config 커스텀" : "등록 서버",
                        (jtagServer.getPassword() == null || jtagServer.getPassword().isBlank()) ? "비어있음" : "설정됨");
                log.info("[T32Dump]    SSH 계정 t32Pc: user={} ({}), pw={}",
                        t32Pc.getUsername(), t32PcCustom ? "config 커스텀" : "등록 서버",
                        (t32Pc.getPassword() == null || t32Pc.getPassword().isBlank()) ? "비어있음" : "설정됨");

                // Step 1: JTAG 연결
                log.info("[T32Dump] Step 1: JTAG 연결 시작");
                sendEvent(emitter, "step-start", Map.of("step", 1, "name", "JTAG 연결"));
                boolean step1Ok = executeStep1Jtag(emitter, config, jtagServer, tentacleName, slotNumber);
                log.info("[T32Dump] Step 1 결과: {}", step1Ok ? "OK" : "FAIL");
                if (!step1Ok) {
                    sendEvent(emitter, "done", Map.of("success", false, "failedStep", 1));
                    emitter.complete();
                    return;
                }

                // JTAG 를 변경하면 이미 떠 있던 PowerView 가 fatal #FF 상태가 되어
                // 재시작이 필요하다. SSH/schtasks 로 띄우면 콘솔 세션 밖이라 USB 를 못
                // 보므로, taskkill 후 PsExec 로 RDP/콘솔의 활성 세션에 다시 띄운다.
                // t32StartCommand 가 비어있으면 재시작을 건너뛴다(수동 운영).
                restartPowerView(emitter, config, t32Pc);

                boolean useGrpc = config.getT32RemoteHost() != null
                        && !config.getT32RemoteHost().isBlank()
                        && config.getT32RemotePort() != null;
                log.info("[T32Dump] Step 2: T32 Attach 시작 (경로={})", useGrpc ? "gRPC" : "SSH+bat(legacy)");

                // Step 2: T32 Attach
                sendEvent(emitter, "step-start", Map.of("step", 2, "name", "T32 Attach"));
                boolean step2Ok = useGrpc
                        ? executeStep2AttachGrpc(emitter, config, t32Pc)
                        : executeStep2Attach(emitter, config, t32Pc);
                log.info("[T32Dump] Step 2 결과: {}", step2Ok ? "OK" : "FAIL");
                if (!step2Ok) {
                    sendEvent(emitter, "done", Map.of("success", false, "failedStep", 2));
                    emitter.complete();
                    return;
                }

                // 결과 폴더 생성: {branchPath}/{datetime}_{setLocation}_{testToolName}_{testTrName}/
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                String datetime = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HH'h'mm'm'ss's'"));
                StringBuilder dirName = new StringBuilder(datetime);
                if (setLocation != null && !setLocation.isBlank()) dirName.append("_").append(setLocation);
                if (testToolName != null && !testToolName.isBlank()) dirName.append("_").append(testToolName);
                if (testTrName != null && !testTrName.isBlank()) dirName.append("_").append(testTrName);
                String resultDirName = dirName.toString();

                // 결과를 FW branch 압축해제 폴더 안에 저장
                String resultLinuxPath = "";
                String resultWindowsPath = "";
                if (branchPath != null && !branchPath.isBlank()) {
                    resultLinuxPath = branchPath.replaceAll("/$", "") + "/" + resultDirName;
                    try {
                        java.nio.file.Files.createDirectories(java.nio.file.Path.of(resultLinuxPath));
                    } catch (Exception e) {
                        log.warn("[T32Dump] 결과 폴더 생성 실패: {}", e.getMessage());
                    }
                } else if (config.getResultBasePath() != null && !config.getResultBasePath().isBlank()) {
                    // branchPath 없을 경우 fallback으로 기존 resultBasePath 사용
                    resultLinuxPath = config.getResultBasePath().replaceAll("/$", "") + "/" + resultDirName;
                    try {
                        java.nio.file.Files.createDirectories(java.nio.file.Path.of(resultLinuxPath));
                    } catch (Exception e) {
                        log.warn("[T32Dump] 결과 폴더 생성 실패: {}", e.getMessage());
                    }
                }

                // 브랜치 폴더 Windows 경로 변환 (fwCodeLinuxBase → fwCodeWindowsBase)
                String branchWindowsPath = "";
                if (branchPath != null && !branchPath.isBlank()) {
                    String linuxBase = config.getFwCodeLinuxBase();
                    String winBase = config.getFwCodeWindowsBase();
                    if (linuxBase != null && !linuxBase.isBlank() && winBase != null && !winBase.isBlank()) {
                        String normalLinux = linuxBase.replaceAll("/$", "");
                        if (branchPath.startsWith(normalLinux)) {
                            String relative = branchPath.substring(normalLinux.length());
                            branchWindowsPath = winBase.replaceAll("[/\\\\]$", "") + relative.replace("/", "\\");
                        } else {
                            branchWindowsPath = branchPath;
                        }
                    } else {
                        branchWindowsPath = branchPath;
                    }
                }

                // resultWindowsPath: branchWindowsPath 기반으로 생성
                if (!branchWindowsPath.isBlank()) {
                    resultWindowsPath = branchWindowsPath.replaceAll("[/\\\\]$", "") + "\\" + resultDirName;
                } else if (config.getResultWindowsBasePath() != null && !config.getResultWindowsBasePath().isBlank()) {
                    resultWindowsPath = config.getResultWindowsBasePath().replaceAll("[/\\\\]$", "") + "\\" + resultDirName;
                }

                // Step 3: Dump 실행
                log.info("[T32Dump] Step 3: Dump 실행 시작 (경로={}, branchWindowsPath={}, resultLinuxPath={}, resultWindowsPath={})",
                        useGrpc ? "gRPC" : "SSH", branchWindowsPath, resultLinuxPath, resultWindowsPath);
                sendEvent(emitter, "step-start", Map.of("step", 3, "name", "Dump 실행"));
                String resultPath = useGrpc
                        ? executeStep3DumpGrpc(emitter, config, t32Pc, branchWindowsPath, resultLinuxPath, resultWindowsPath)
                        : executeStep3Dump(emitter, config, t32Pc, tentacleName, slotNumber, resultWindowsPath, branchWindowsPath);
                boolean step3Ok = resultPath != null;
                log.info("[T32Dump] Step 3 결과: {} (resultPath={})", step3Ok ? "OK" : "FAIL", resultPath);
                if (!step3Ok) {
                    sendEvent(emitter, "done", Map.of("success", false, "failedStep", 3));
                    emitter.complete();
                    return;
                }

                // Step 4: 결과 압축본 S3 업로드 + DB 기록 후 완료
                // (zip 자체는 Step3 executeStep3DumpGrpc 에서 Z: 매핑 세션으로 생성됨.
                //  Canary 복사·정리도 Step3 finishCanary 에서 수행됨.)
                log.info("[T32Dump] Step 4: 결과 업로드 + 완료");
                sendEvent(emitter, "step-start", Map.of("step", 4, "name", "결과 업로드"));
                uploadResultArtifact(emitter, resultLinuxPath, resultDirName,
                        setLocation, testToolName, testTrName, historyId, testType, source);
                sendEvent(emitter, "step-done", Map.of("step", 4, "status", "success", "output", "Dump 완료"));
                sendEvent(emitter, "done", Map.of(
                        "success", true,
                        "resultPath", resultLinuxPath,
                        "resultWindowsPath", resultWindowsPath));
                log.info("[T32Dump] 완료: success=true resultLinuxPath={} resultWindowsPath={}", resultLinuxPath, resultWindowsPath);

                emitter.complete();

            } catch (Exception e) {
                // 중단(interrupt) 으로 끝난 경우엔 정상적인 취소이므로 error 로그를 남기지 않는다.
                if (Thread.currentThread().isInterrupted() || e instanceof InterruptedException) {
                    log.info("[T32Dump] 사용자 중단으로 정리: config={}", lockConfigId);
                    sendEvent(emitter, "cancelled", Map.of("message", "사용자에 의해 중단됨"));
                } else {
                    log.error("[T32Dump] 실행 오류: {}", e.getMessage(), e);
                    sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                }
                emitter.complete();
            } finally {
                // 성공/각 step 실패/예외/중단 어느 경로로 끝나든 lock 을 단일 지점에서 해제한다.
                lockService.release(lockConfigId, userKey);
                // 진행 워커 등록 해제(자기 자신일 때만 — 이후 재실행이 덮어쓴 경우 보존).
                running.remove(lockConfigId, runningRef[0]);
            }
        });

        // 워커가 등록되기 전에 cancel 이 오면 놓치므로, future 를 만든 뒤 등록한다.
        // 등록 식별자(runningRef)를 워커 finally 의 remove 에서도 동일하게 참조한다.
        RunningDump rd = new RunningDump(future, userKey);
        runningRef[0] = rd;
        running.put(lockConfigId, rd);

        // 클라이언트가 연결을 끊거나(브라우저 종료/네트워크 단절) emitter 가 timeout 되면
        // 워커를 interrupt 해 lock·세션을 즉시 정리한다. complete 정상 종료 시엔 이미
        // 워커 finally 가 정리를 마친 뒤라 cancel 은 no-op.
        emitter.onError(ex -> cancelDump(lockConfigId, userKey));
        emitter.onTimeout(() -> cancelDump(lockConfigId, userKey));

        return emitter;
    }
