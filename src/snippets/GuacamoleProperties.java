// @source src/main/java/com/samsung/move/guacamole/config/GuacamoleProperties.java
// @lines 1-21
// @note guacamole.* yaml 설정 바인딩
// @synced 2026-04-19T08:33:48.674Z

package com.samsung.move.guacamole.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "guacamole")
@Getter
@Setter
public class GuacamoleProperties {

    // guacd daemon connection settings
    private String guacdHost = "localhost";
    private int guacdPort = 4822;

    // Guacamole web application settings
    private String webUrl = "http://localhost:9005/guacamole";
    private String username = "guacadmin";
    private String password = "guacadmin";
    private String dataSource = "mysql";
}
