// @source src/main/java/com/samsung/move/head/service/HeadSlotStateStore.java
// @lines 1-119
// @note ConcurrentHashMap + AtomicLong version + updateSlots
// @synced 2026-05-01T01:05:23.610Z

package com.samsung.move.head.service;

import com.samsung.move.head.entity.HeadSlotData;
import com.samsung.move.head.precmd.service.PreCommandAutoExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class HeadSlotStateStore {

    // key = "source:slotIndex" e.g. "compatibility:0"
    private final ConcurrentHashMap<String, HeadSlotData> slots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> connectionStatuses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> connectionErrors = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(0);

    private final PreCommandAutoExecutor preCommandAutoExecutor;
    private final SlotInfoMerger slotInfoMerger;

    public HeadSlotStateStore(@Lazy PreCommandAutoExecutor preCommandAutoExecutor,
                              SlotInfoMerger slotInfoMerger) {
        this.preCommandAutoExecutor = preCommandAutoExecutor;
        this.slotInfoMerger = slotInfoMerger;
    }

    public void updateSlots(String source, List<HeadSlotData> slotDataList) {
        // HEAD TCP 수신 시점에 SlotInfomation(DB)을 병합 → 모든 consumer가 testTrId 등을 바로 사용 가능
        slotInfoMerger.merge(slotDataList);

        for (HeadSlotData data : slotDataList) {
            String key = source + ":" + data.getSlotIndex();
            HeadSlotData oldData = slots.put(key, data);
            // init 상태 진입 감지 → 사전 명령어 자동 실행
            try {
                preCommandAutoExecutor.onSlotStateChanged(source, oldData, data);
            } catch (Exception e) {
                log.debug("PreCommand auto-exec check failed for {}: {}", key, e.getMessage());
            }
        }
        version.incrementAndGet();
    }

    public void updateSlot(String source, HeadSlotData data) {
        String key = source + ":" + data.getSlotIndex();
        slots.put(key, data);
        version.incrementAndGet();
    }

    public List<HeadSlotData> getSlotsBySource(String source) {
        List<HeadSlotData> result = new ArrayList<>();
        for (var entry : slots.entrySet()) {
            if (entry.getKey().startsWith(source + ":")) {
                result.add(entry.getValue());
            }
        }
        result.sort((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()));
        return Collections.unmodifiableList(result);
    }

    public List<HeadSlotData> getAllSlots() {
        List<HeadSlotData> result = new ArrayList<>(slots.values());
        result.sort((a, b) -> {
            int cmp = String.valueOf(a.getSource()).compareTo(String.valueOf(b.getSource()));
            return cmp != 0 ? cmp : Integer.compare(a.getSlotIndex(), b.getSlotIndex());
        });
        return Collections.unmodifiableList(result);
    }

    public long getVersion() {
        return version.get();
    }

    public void clearBySource(String source) {
        slots.entrySet().removeIf(e -> e.getKey().startsWith(source + ":"));
        version.incrementAndGet();
    }

    public void updateConnectionStatus(String source, boolean connected) {
        connectionStatuses.put(source, connected);
        if (connected) {
            connectionErrors.remove(source);
        }
        version.incrementAndGet();
    }

    public void updateConnectionStatus(String source, boolean connected, String errorMessage) {
        connectionStatuses.put(source, connected);
        if (errorMessage != null) {
            connectionErrors.put(source, errorMessage);
        } else {
            connectionErrors.remove(source);
        }
        version.incrementAndGet();
    }

    public String getConnectionError(String source) {
        return connectionErrors.get(source);
    }

    public boolean isConnected(String source) {
        return connectionStatuses.getOrDefault(source, false);
    }

    public void removeConnectionStatus(String source) {
        connectionStatuses.remove(source);
        connectionErrors.remove(source);
        version.incrementAndGet();
    }

    public ConcurrentHashMap<String, Boolean> getConnectionStatuses() {
        return connectionStatuses;
    }
}
