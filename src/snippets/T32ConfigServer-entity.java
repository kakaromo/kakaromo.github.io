// @source src/main/java/com/samsung/move/t32/entity/T32ConfigServer.java
// @note portal_t32_config_servers — many-to-many (config ↔ portal_servers) + 복합 unique
// @synced 2026-05-01T01:10:31.194Z

package com.samsung.move.t32.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portal_t32_config_servers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"t32ConfigId", "serverId"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class T32ConfigServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long t32ConfigId;

    @Column(nullable = false)
    private Long serverId;
}

