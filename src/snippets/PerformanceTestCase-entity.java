// @source src/main/java/com/samsung/move/testdb/entity/PerformanceTestCase.java
// @lines 1-57
// @note 성능 시나리오 정의 — name / fileName / parserId / category / ioType
// @synced 2026-05-01T01:05:23.637Z

package com.samsung.move.testdb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "PerformanceTestCase")
@Getter
@Setter
@NoArgsConstructor
public class PerformanceTestCase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME")
    private String name;

    @Column(name = "FILE_NAME")
    private String fileName;

    @Column(name = "PARSER_ID")
    private Long parserId;

    @Column(name = "AUTHOR")
    private String author;

    @Column(name = "DATE")
    private LocalDateTime date;

    @PrePersist
    public void prePersist() {
        if (date == null) date = LocalDateTime.now();
    }

    @Column(name = "HIDDEN")
    private Integer hidden;

    @Column(name = "CATEGORY")
    private String category;

    @Column(name = "IO_TYPE")
    private String ioType;

    @Column(name = "TC_OPTION")
    private String tcOption;
}

