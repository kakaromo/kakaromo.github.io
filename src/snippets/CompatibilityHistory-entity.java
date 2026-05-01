// @source src/main/java/com/samsung/move/testdb/entity/CompatibilityHistory.java
// @lines 1-60
// @note 호환성 실행 결과 — LocalDateTime 기반 + failCause + setProductName
// @synced 2026-05-01T01:05:23.638Z

package com.samsung.move.testdb.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "CompatibilityHistory")
@Getter
@Setter
@NoArgsConstructor
public class CompatibilityHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "RESULT")
    private String result;

    @Column(name = "FAIL_CAUSE")
    private String failCause;

    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    @Column(name = "RUNNING_TIME")
    private String runningTime;

    @Column(name = "SET_SERIAL")
    private String setSerial;

    @Column(name = "SET_PRODUCT_NAME")
    private String setProductName;

    @Column(name = "SET_MODEL_NAME")
    private String setModelName;

    @Column(name = "SET_DEVICE_NAME")
    private String setDeviceName;

    @Column(name = "TENTACLE_IP")
    private String tentacleIp;

    @Column(name = "USB_ID")
    private String usbId;

    @Column(name = "LOG_PATH")
    private String logPath;
