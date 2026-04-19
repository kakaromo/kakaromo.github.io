// @source src/main/java/com/samsung/move/logbrowser/config/LogBrowserConfig.java
// @lines 1-39
// @note @ConditionalOnProperty tentacle.access-mode로 Local vs SSH 빈 선택
// @synced 2026-04-19T09:04:03.502Z

package com.samsung.move.logbrowser.config;

import com.samsung.move.logbrowser.service.LocalLogBrowserService;
import com.samsung.move.logbrowser.service.LogBrowserService;
import com.samsung.move.logbrowser.service.SshLogBrowserService;

import com.samsung.move.admin.service.PortalServerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogBrowserConfig {

    @Bean
    @ConditionalOnProperty(name = "tentacle.access-mode", havingValue = "local")
    public LogBrowserService localLogBrowserService(
            PortalServerService serverService,
            @Value("${tentacle.mount-path}") String mountPath,
            @Value("${tentacle.head.mount-path:/mnt/head/nas}") String headMountPath) {
        return new LocalLogBrowserService(serverService, mountPath, headMountPath);
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnProperty(name = "tentacle.access-mode", havingValue = "ssh", matchIfMissing = true)
    public LogBrowserService sshLogBrowserService(
            PortalServerService serverService,
            @Value("${tentacle.log-prefix:/home/octo/tentacle}") String logPrefix,
            @Value("${tentacle.ssh.username:samsung}") String sshUsername,
            @Value("${tentacle.ssh.password:tentacle}") String sshPassword,
            @Value("${tentacle.ssh.port:22}") int sshPort) {
        SshLogBrowserService service = new SshLogBrowserService(serverService, logPrefix);
        service.setTentacleUsername(sshUsername);
        service.setTentaclePassword(sshPassword);
        service.setTentacleSshPort(sshPort);
        return service;
    }
}
