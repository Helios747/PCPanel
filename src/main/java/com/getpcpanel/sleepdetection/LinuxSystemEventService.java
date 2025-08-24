package com.getpcpanel.sleepdetection;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnLinux;
import com.getpcpanel.sleepdetection.WindowsSystemEventService.WindowsSystemEvent;
import com.getpcpanel.sleepdetection.WindowsSystemEventService.WindowsSystemEventType;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Linux system event service for detecting suspend/resume events using systemd-logind.
 * This service monitors dbus signals to detect system suspend and resume events.
 */
@Log4j2
@Service
@ConditionalOnLinux
@RequiredArgsConstructor
public class LinuxSystemEventService {
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LinuxSystemEventService");
        t.setDaemon(true);
        return t;
    });
    
    private Process dbusProcess;
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(this::startMonitoring, executorService);
    }

    @PreDestroy
    public void cleanup() {
        running = false;
        if (dbusProcess != null && dbusProcess.isAlive()) {
            dbusProcess.destroyForcibly();
        }
        executorService.shutdown();
    }

    private void startMonitoring() {
        log.info("Starting Linux system event monitoring");
        
        try {
            // Monitor systemd-logind PrepareForSleep signal
            ProcessBuilder pb = new ProcessBuilder(
                "dbus-monitor",
                "--system",
                "interface='org.freedesktop.login1.Manager',member='PrepareForSleep'"
            );
            
            dbusProcess = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(dbusProcess.getInputStream()))) {
                
                String line;
                boolean preparingForSleep = false;
                
                while (running && (line = reader.readLine()) != null) {
                    log.trace("dbus-monitor output: {}", line);
                    
                    // Look for PrepareForSleep signal
                    if (line.contains("signal time=") && line.contains("member=PrepareForSleep")) {
                        preparingForSleep = true;
                        log.debug("Detected PrepareForSleep signal");
                    }
                    
                    // Look for the boolean parameter indicating suspend (true) or resume (false)
                    if (preparingForSleep && line.trim().startsWith("boolean")) {
                        boolean isSuspending = line.contains("true");
                        preparingForSleep = false;
                        
                        if (isSuspending) {
                            log.info("System is preparing to suspend");
                            publishEvent(WindowsSystemEventType.goingToSuspend);
                        } else {
                            log.info("System has resumed from suspend");
                            publishEvent(WindowsSystemEventType.resumedFromSuspend);
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                log.error("Error monitoring system events, trying fallback method", e);
                startFallbackMonitoring();
            }
        } catch (Exception e) {
            log.error("Unexpected error in system event monitoring", e);
        }
    }

    private void startFallbackMonitoring() {
        log.info("Starting fallback system event monitoring using journalctl");
        
        try {
            // Fallback: monitor systemd journal for suspend/resume events
            ProcessBuilder pb = new ProcessBuilder(
                "journalctl",
                "-f",
                "--no-pager",
                "_SYSTEMD_UNIT=systemd-suspend.service",
                "_SYSTEMD_UNIT=systemd-hybrid-sleep.service",
                "_SYSTEMD_UNIT=systemd-hibernate.service"
            );
            
            dbusProcess = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(dbusProcess.getInputStream()))) {
                
                String line;
                while (running && (line = reader.readLine()) != null) {
                    log.trace("journalctl output: {}", line);
                    
                    if (line.contains("Starting Suspend...") || 
                        line.contains("Starting Hibernate...") || 
                        line.contains("Starting Hybrid Suspend...")) {
                        log.info("System is suspending");
                        publishEvent(WindowsSystemEventType.goingToSuspend);
                    } else if (line.contains("Finished Suspend.") || 
                              line.contains("Finished Hibernate.") || 
                              line.contains("Finished Hybrid Suspend.")) {
                        log.info("System has resumed from suspend");
                        publishEvent(WindowsSystemEventType.resumedFromSuspend);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                log.warn("Fallback monitoring also failed. System suspend/resume events will not be detected.", e);
            }
        } catch (Exception e) {
            log.error("Unexpected error in fallback system event monitoring", e);
        }
    }

    private void publishEvent(WindowsSystemEventType eventType) {
        try {
            eventPublisher.publishEvent(new WindowsSystemEvent(eventType));
            log.debug("Published system event: {}", eventType);
        } catch (Exception e) {
            log.error("Error publishing system event: {}", eventType, e);
        }
    }
}