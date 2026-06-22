// @source src/main/java/com/samsung/move/t32/service/T32DumpLockService.java
// @lines 1-71
// @note 단독 점유 lock — configId 키 putIfAbsent + 점유자 일치 시에만 release
// @synced 2026-06-22T22:22:10.909Z

package com.samsung.move.t32.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T32 dump 의 단독 점유(exclusive lock) 관리자. RDP 단독 접속처럼 한 T32 장비에서는
 * 한 번에 한 명만 dump 할 수 있게 한다.
 *
 * <p><b>lock 키는 {@code t32ConfigId}</b> 다. 여러 slot/serverId 가 하나의 T32 PC(=하나의
 * {@link com.samsung.move.t32.entity.T32Config})를 공유하므로, serverId 단위로 잠그면
 * 같은 장비를 공유하는 다른 slot 의 동시 dump 를 막지 못한다.
 *
 * <p>저장소는 in-memory({@link ConcurrentHashMap}) — 운영은 단일 인스턴스 가정. 서버 재시작
 * 시 lock 이 초기화되지만, 그 경우 진행 중이던 dump 도 함께 죽으므로 정합성에 문제 없다.
 * 멀티 인스턴스로 확장하면 DB row lock / Redis 로 승격해야 한다.
 */
@Slf4j
@Service
public class T32DumpLockService {

    /** 현재 dump 점유 정보. 누가(표시명/사용자키) 언제 시작했는지. */
    public record Holder(String userKey, String displayName, LocalDateTime since) {}

    private final ConcurrentHashMap<Long, Holder> locks = new ConcurrentHashMap<>();

    /**
     * configId 에 대한 단독 점유를 시도한다.
     *
     * @return 획득 성공 시 {@link Optional#empty()}, 이미 점유 중이면 현재 점유자({@link Holder}).
     */
    public Optional<Holder> tryAcquire(Long configId, String userKey, String displayName) {
        if (configId == null) return Optional.empty(); // configId 없으면 lock 대상 아님(통과)
        Holder attempt = new Holder(userKey, displayName, LocalDateTime.now());
        Holder existing = locks.putIfAbsent(configId, attempt);
        if (existing != null) {
            log.info("[T32DumpLock] 거부: config={} 시도자={} → 점유자={}({} 부터)",
                    configId, displayName, existing.displayName(), existing.since());
            return Optional.of(existing);
        }
        log.info("[T32DumpLock] 획득: config={} 점유자={}", configId, displayName);
        return Optional.empty();
    }

    /**
     * 점유를 해제한다. 같은 사용자가 잡은 lock 일 때만 푼다(다른 사람 lock 을 실수로
     * 덮어쓰지 않도록). configId 가 null 이면 no-op.
     */
    public void release(Long configId, String userKey) {
        if (configId == null) return;
        locks.computeIfPresent(configId, (k, holder) -> {
            if (holder.userKey().equals(userKey)) {
                log.info("[T32DumpLock] 해제: config={} 점유자={}", configId, holder.displayName());
                return null; // 제거
            }
            log.warn("[T32DumpLock] 해제 무시: config={} 요청자={} ≠ 점유자={}",
                    configId, userKey, holder.userKey());
            return holder;
        });
    }

    /** 현재 점유자 조회(없으면 empty). check 단계에서 점유 여부 표시용. */
    public Optional<Holder> getHolder(Long configId) {
        if (configId == null) return Optional.empty();
        return Optional.ofNullable(locks.get(configId));
    }
}
