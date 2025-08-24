package com.getpcpanel.sleepdetection;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.LightingConfig;

import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public final class SleepDetector {
    private static final LightingConfig ALL_OFF = LightingConfig.createAllColor(Color.BLACK);
    private final DeviceScanner deviceScanner;
    private final OutputInterpreter outputInterpreter;
    private final DeviceHolder devices;

    @PostConstruct
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> onSuspended(true), "Shutdown SleepDetector Hook Thread"));
    }

    @EventListener
    public void onEvent(WindowsSystemEventService.WindowsSystemEvent event) {
        switch (event.type()) {
            case goingToSuspend, locked -> onSuspended(false);
            case resumedFromSuspend, unlocked -> onResumed();
        }
    }

    private void onSuspended(boolean shutdown) {
        Runnable r = () -> {
            for (var device : devices.values()) {
                log.debug("Pause: {}", device.getSerialNumber());
                outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), ALL_OFF, true);

                if (shutdown) {
                    waitUntilEmptyPrioQueue(device);
                }
            }
            if (shutdown)
                deviceScanner.close();
        };

        if (shutdown) {
            r.run();
        } else {
            Platform.runLater(r);
        }
        log.info("Stopped sleep detector");
    }

    private void waitUntilEmptyPrioQueue(Device device) {
        var handler = deviceScanner.getConnectedDevice(device.getSerialNumber());
        for (var i = 0; i < 20; i++) {
            if (handler.getQueue().isEmpty())
                break;
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                log.warn("Unable to sleep", e);
            }
        }
    }

    private void onResumed() {
        Platform.runLater(() -> {
            // Wait a bit for USB subsystem to stabilize after resume
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for system to stabilize after resume", e);
            }

            // Try to reconnect any disconnected devices
            log.info("Attempting to reconnect devices after system resume");
            deviceScanner.triggerDeviceRescan();

            // Wait a bit more for device reconnection to complete
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for device reconnection", e);
            }

            // Restore lighting configuration for all devices
            for (var device : devices.values()) {
                log.info("RESUME: Restoring lighting for {}", device.getSerialNumber());
                outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), device.getLightingConfig(), true);
            }
        });
    }
}
