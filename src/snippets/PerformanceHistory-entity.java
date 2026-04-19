// @source src/main/java/com/samsung/move/testdb/entity/PerformanceHistory.java
// @lines 1-68
// @note 실행 결과 스냅샷 — logPath(원격 JSON 포인터) + result + ManyToOne TR/TC
// @synced 2026-04-19T09:49:20.700Z

package com.samsung.move.testdb.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "PerformanceHistory")
@Getter
@Setter
@NoArgsConstructor
public class PerformanceHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "START_TIME")
    private String startTime;

    @Column(name = "END_TIME")
    private String endTime;

    @Column(name = "RUNNING_TIME")
    private String runningTime;

    @Column(name = "SET_ID")
    private Long setId;

    @Column(name = "LOG_PATH")
    private String logPath;

    @Column(name = "TC_ID")
    private Long tcId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TC_ID", referencedColumnName = "ID", insertable = false, updatable = false)
    @JsonIgnore
    private PerformanceTestCase testCase;

    @Column(name = "TR_ID")
    private Long trId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TR_ID", referencedColumnName = "ID", insertable = false, updatable = false)
    @JsonIgnore
    private PerformanceTestRequest testRequest;

    @Column(name = "SLOT_LOCATION")
    private String slotLocation;

    @Column(name = "UPLOADED")
    private String uploaded;

    @Column(name = "RESULT")
    private String result;

    @Column(name = "FILE_SYSTEM")
    private String fileSystem;
}
