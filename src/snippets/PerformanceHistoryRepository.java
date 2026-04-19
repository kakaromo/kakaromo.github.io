// @source src/main/java/com/samsung/move/testdb/repository/PerformanceHistoryRepository.java
// @lines 1-54
// @note JpaSpecificationExecutor + @Query 대시보드 집계 (countByTr/TcAndResult)
// @synced 2026-04-19T10:15:34.668Z

package com.samsung.move.testdb.repository;

import com.samsung.move.testdb.entity.PerformanceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PerformanceHistoryRepository extends JpaRepository<PerformanceHistory, Long>,
        JpaSpecificationExecutor<PerformanceHistory> {

    boolean existsByTrId(Long trId);

    Page<PerformanceHistory> findAll(Pageable pageable);

    @Query("SELECT h.trId AS groupKey, COUNT(h) AS cnt FROM PerformanceHistory h GROUP BY h.trId ORDER BY h.trId DESC")
    List<Object[]> countByTrGroup();

    @Query("SELECT h.tcId AS groupKey, COUNT(h) AS cnt FROM PerformanceHistory h GROUP BY h.tcId")
    List<Object[]> countByTcGroup();

    Page<PerformanceHistory> findByTrId(Long trId, Pageable pageable);

    Page<PerformanceHistory> findByTcId(Long tcId, Pageable pageable);

    Page<PerformanceHistory> findByTrIdAndTcId(Long trId, Long tcId, Pageable pageable);

    @Query("SELECT h.tcId AS groupKey, COUNT(h) AS cnt FROM PerformanceHistory h WHERE h.trId = :trId GROUP BY h.tcId")
    List<Object[]> countByTcGroupForTr(@org.springframework.data.repository.query.Param("trId") Long trId);

    @Query("SELECT h FROM PerformanceHistory h WHERE h.startTime >= :startDate AND h.startTime <= :endDate")
    Page<PerformanceHistory> findByStartTimeBetween(
            @org.springframework.data.repository.query.Param("startDate") String startDate,
            @org.springframework.data.repository.query.Param("endDate") String endDate,
            Pageable pageable);

    // Dashboard 집계 쿼리
    @Query("SELECT h.trId, h.result, COUNT(h) FROM PerformanceHistory h GROUP BY h.trId, h.result")
    List<Object[]> countByTrAndResult();

    @Query("SELECT h.tcId, h.result, COUNT(h) FROM PerformanceHistory h GROUP BY h.tcId, h.result")
    List<Object[]> countByTcAndResult();

    @Query("SELECT COUNT(h) FROM PerformanceHistory h")
    long countAll();

    @Query("SELECT h.result, COUNT(h) FROM PerformanceHistory h GROUP BY h.result")
    List<Object[]> countByResult();

    List<PerformanceHistory> findTop10ByOrderByIdDesc();
}
