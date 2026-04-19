// @source src/main/java/com/samsung/move/testdb/entity/PerformanceTestRequest.java
// @lines 1-70
// @note FW 메타(컨트롤러·NAND·CS 버전) + getFw() @Transient + Redis setFw 무시
// @synced 2026-04-19T09:04:03.512Z

package com.samsung.move.testdb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "PerformanceTestRequest")
@Getter
@Setter
@NoArgsConstructor
public class PerformanceTestRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CONTROLLER", nullable = false)
    private String controller;

    @Column(name = "SPEC_VERSION", nullable = false)
    private String specVersion;

    @Column(name = "CELL_TYPE", nullable = false)
    private String cellType;

    @Column(name = "NAND_TYPE", nullable = false)
    private String nandType;

    @Column(name = "NAND_SIZE", nullable = false)
    private String nandSize;

    @Column(name = "DENSITY", nullable = false)
    private String density;

    @Column(name = "FW_VERSION", nullable = false)
    private String fwVersion;

    @Column(name = "BASE_FW_VERSION", nullable = false)
    private String baseFwVersion;

    @Column(name = "OEM")
    private String oem;

    @Column(name = "CS_VERSION")
    private Integer csVersion;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @PrePersist
    public void prePersist() {
        if (date == null) date = LocalDateTime.now();
    }

    @Transient
    public String getFw() {
        return String.join("_", controller, cellType, nandType, nandSize, density, fwVersion);
    }

    // Redis JSON 역직렬화 시 getFw()로 저장된 "fw" 필드를 무시하기 위한 setter
    public void setFw(String ignored) { }
}
