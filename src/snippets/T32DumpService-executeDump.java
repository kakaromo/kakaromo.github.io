// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 78-220
// @note executeDump 오케스트레이션 — 4 Step 순차 + 경로 변환 + Canary ZIP
// @synced 2026-04-19T09:32:45.520Z

    public SseEmitter executeDump(Long serverId, String tentacleName, int slotNumber, String fw, String branchPath,
                                     String setLocation, String testToolName, String testTrName) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10분 timeout

        executor.submit(() -> {
            try {
                // T32Config 조회
                T32ConfigServer mapping = configServerRepository.findByServerId(serverId)
                        .orElseThrow(() -> new IllegalArgumentException("T32 설정을 찾을 수 없습니다 (serverId=" + serverId + ")"));
                T32Config config = configRepository.findById(mapping.getT32ConfigId())
                        .orElseThrow(() -> new IllegalArgumentException("T32 설정이 존재하지 않습니다"));

                PortalServer jtagServer = serverRepository.findById(config.getJtagServerId())
                        .orElseThrow(() -> new IllegalArgumentException("JTAG 서버를 찾을 수 없습니다"));
                PortalServer t32Pc = serverRepository.findById(config.getT32PcId())
                        .orElseThrow(() -> new IllegalArgumentException("T32 PC를 찾을 수 없습니다"));

                // T32Config에 커스텀 계정이 있으면 덮어쓰기 (원본 PortalServer는 변경하지 않음)
                jtagServer = applyCustomAccount(jtagServer, config.getJtagUsername(), config.getJtagPassword());
                t32Pc = applyCustomAccount(t32Pc, config.getT32PcUsername(), config.getT32PcPassword());

                // Step 1: JTAG 연결
                sendEvent(emitter, "step-start", Map.of("step", 1, "name", "JTAG 연결"));
                boolean step1Ok = executeStep1Jtag(emitter, config, jtagServer, tentacleName, slotNumber);
                if (!step1Ok) {
                    sendEvent(emitter, "done", Map.of("success", false, "failedStep", 1));
                    emitter.complete();
                    return;
                }

                // Step 2: T32 Attach
                sendEvent(emitter, "step-start", Map.of("step", 2, "name", "T32 Attach"));
                boolean step2Ok = executeStep2Attach(emitter, config, t32Pc);
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
                sendEvent(emitter, "step-start", Map.of("step", 3, "name", "Dump 실행"));
                String resultPath = executeStep3Dump(emitter, config, t32Pc, tentacleName, slotNumber, resultWindowsPath, branchWindowsPath);
                boolean step3Ok = resultPath != null;
                if (!step3Ok) {
                    sendEvent(emitter, "done", Map.of("success", false, "failedStep", 3));
                    emitter.complete();
                    return;
                }

                // Step 4: Canary 압축 + 완료
                sendEvent(emitter, "step-start", Map.of("step", 4, "name", "완료"));

                // Canary 폴더 감지 → ZIP 자동 압축 (T32 PC에서 실행)
                if (!resultWindowsPath.isBlank()) {
                    try {
                        String canaryZipCmd = "powershell -Command \"" +
                                "$canaryPath = Get-ChildItem -Path '" + resultWindowsPath + "' -Directory | " +
                                "Where-Object { $_.Name -match '^(?i)canary$' } | Select-Object -First 1; " +
                                "if ($canaryPath) { " +
                                "Compress-Archive -Path $canaryPath.FullName -DestinationPath (Join-Path '" + resultWindowsPath + "' ($canaryPath.Name + '.zip')) -Force; " +
                                "Write-Output ('Compressed: ' + $canaryPath.Name + '.zip') " +
                                "} else { Write-Output 'No Canary folder found' }\"";
                        CommandResult zipResult = executeSshCommand(t32Pc, canaryZipCmd, 60, emitter, 4);
                        log.info("[T32Dump] Canary 압축 결과: {}", zipResult.output());
                        if (zipResult.output().contains("Compressed:")) {
                            sendEvent(emitter, "step-output", Map.of("step", 4, "text", zipResult.output()));
                        }
                    } catch (Exception e) {
                        log.warn("[T32Dump] Canary 압축 실패 (무시): {}", e.getMessage());
                    }
                }

                sendEvent(emitter, "step-done", Map.of("step", 4, "status", "success", "output", "Dump 완료"));
                sendEvent(emitter, "done", Map.of(
                        "success", true,
                        "resultPath", resultLinuxPath,
                        "resultWindowsPath", resultWindowsPath));

                emitter.complete();

            } catch (Exception e) {
                log.error("[T32Dump] 실행 오류: {}", e.getMessage(), e);
                sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                emitter.complete();
            }
        });

        return emitter;
    }
