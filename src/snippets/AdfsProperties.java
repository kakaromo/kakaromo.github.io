// @source src/main/java/com/samsung/move/auth/config/AdfsProperties.java
// @lines 1-20
// @note @ConfigurationProperties portal.adfs — enabled/clientId/authorizeUrl/redirectUrl/logoutUrl/scope
// @synced 2026-04-19T09:49:20.696Z

package com.samsung.move.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "portal.adfs")
public class AdfsProperties {
    private boolean enabled = false;
    private String clientId = "";
    private String authorizeUrl = "";
    private String redirectUrl = "";
    private String logoutUrl = "";
    private String scope = "openid profile";
}

