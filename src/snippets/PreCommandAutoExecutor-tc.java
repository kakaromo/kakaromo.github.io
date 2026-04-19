// @source src/main/java/com/samsung/move/head/precmd/service/PreCommandAutoExecutor.java
// @lines 85-172
// @note tryExecuteTcPreCommand — testcaseStatus에서 첫 미완료 position 찾고 TC Pre-Command 실행
// @synced 2026-04-19T08:19:17.631Z

    /**
     * testcaseStatus에서 현재 대상 position을 결정하고, TC Pre-Command를 실행.
     * executedTcs(position 기반)로 중복 방지.
     */
    private boolean tryExecuteTcPreCommand(String source, HeadSlotData newData, String setLocation,
                                            String tcPreCommandIds) {
        try {
            if (tcPreCommandIds == null || tcPreCommandIds.isBlank()) return false;

            // setLocation에서 tentacleName/slotNumber 추출
            Matcher locMatcher = SET_LOCATION_PATTERN.matcher(setLocation);
            if (!locMatcher.matches()) return false;
            String tentacleName = locMatcher.group(1);
            int slotNumber = Integer.parseInt(locMatcher.group(2));

            // SlotInfomation 조회
            var slotInfoId = new SlotInfomation.SlotInfomationId();
            slotInfoId.setTentacleName(tentacleName);
            slotInfoId.setSlotNumber(slotNumber);
            var slotInfoOpt = slotInfomationRepository.findById(slotInfoId);
            if (slotInfoOpt.isEmpty()) return false;

            var slotInfo = slotInfoOpt.get();
            String testcaseIdsStr = slotInfo.getTestcaseIds();
            String testcaseStatusStr = slotInfo.getTestcaseStatus();

            if (testcaseIdsStr == null || testcaseIdsStr.isBlank()) return false;
            if (testcaseStatusStr == null || testcaseStatusStr.isBlank()) return false;

            // 선행 "/" 제거 (DB에 "/42/43" 형태로 저장될 수 있음)
            if (testcaseIdsStr.startsWith("/")) testcaseIdsStr = testcaseIdsStr.substring(1);
            if (testcaseStatusStr.startsWith("/")) testcaseStatusStr = testcaseStatusStr.substring(1);

            String[] tcIds = testcaseIdsStr.split("/", -1);
            String[] statuses = testcaseStatusStr.split("/", -1);
            String[] preCommandIdArr = tcPreCommandIds.split(",", -1);

            // 현재 실행 대상 position: 첫 번째 "완료되지 않은" TC
            // 완료 상태: PASS(27,35,36), FAIL(4,11,15,37,38), WARNING_PASS(28,50),
            //           TIMEOUT_FAIL(30,39,40), BOOTING_FAIL(31,32,41-44), STOP(33,45,46),
            //           DISCONNECT(34,47,48), EMERGENCY_STOP(51,52,53), CRITICAL_FAIL(55,56,57)
            // 미완료: 0(NOTSTART), 14(NOTSTART), RUNNING(1-19,21), WARNING(22), PAUSE(23), CHARGE(24-26), COUNT(49)
            int targetPosition = -1;
            for (int i = 0; i < tcIds.length; i++) {
                int status = 0;
                if (i < statuses.length) {
                    try { status = Integer.parseInt(statuses[i].trim()); }
                    catch (NumberFormatException ignored) {}
                }
                if (!isCompletedStatus(status)) {
                    targetPosition = i;
                    break;
                }
            }
            if (targetPosition < 0) return false;

            // 해당 position에 Pre-Command 등록 확인
            if (targetPosition >= preCommandIdArr.length) return false;
            long tcPreCmdId;
            try { tcPreCmdId = Long.parseLong(preCommandIdArr[targetPosition].trim()); }
            catch (NumberFormatException e) { return false; }
            if (tcPreCmdId <= 0) return false;

            // testToolName 매칭
            String testToolName = newData.getTestToolName();
            if (testToolName == null || testToolName.isBlank()) return false;

            if (targetPosition >= tcIds.length) return false;
            String tcName = getTcName(tcIds[targetPosition].trim(), newData.getHeadType());
            if (tcName == null || !testToolName.equalsIgnoreCase(tcName)) return false;

            // 중복 실행 방지
            final int pos = targetPosition;
            String tcKey = setLocation + ":" + pos;
            if (!executedTcs.add(tcKey)) return true;

            executor.submit(() -> {
                try {
                    preCommandService.executeSync(tcPreCmdId, source, List.of(newData.getSlotIndex()));
                } catch (Exception ignored) {}
            });

            return true;

        } catch (Exception e) {
            return false;
        }
    }
