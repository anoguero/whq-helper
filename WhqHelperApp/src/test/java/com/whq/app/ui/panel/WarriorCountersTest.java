package com.whq.app.ui.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WarriorCountersTest {

    @TempDir
    Path tempDir;

    private static final String CONTENT_HOME_PROPERTY = "whq.content.home";

    private String previousContentHome;

    @BeforeEach
    void rememberContentHome() {
        previousContentHome = System.getProperty(CONTENT_HOME_PROPERTY);
    }

    @AfterEach
    void restoreContentHome() {
        if (previousContentHome == null) {
            System.clearProperty(CONTENT_HOME_PROPERTY);
        } else {
            System.setProperty(CONTENT_HOME_PROPERTY, previousContentHome);
        }
    }

    @Test
    void findsTheCounterImageInTheContentPackage() throws Exception {
        // El fallo: se buscaba junto a la aplicacion (runtime home) y no se encontraba la imagen del paquete.
        Path content = tempDir.resolve("content");
        Path runtimeHome = Files.createDirectories(tempDir.resolve("user-home"));
        Path image = content.resolve("resources/warrior_counters/amber.png");
        Files.createDirectories(image.getParent());
        Files.writeString(image, "png");
        System.setProperty(CONTENT_HOME_PROPERTY, content.toString());

        assertEquals(image, WarriorCounters.counterImagePath(runtimeHome, "resources/warrior_counters/amber.png"));
    }

    @Test
    void findsAUserCounterImageInTheRuntimeHome() throws Exception {
        Path content = Files.createDirectories(tempDir.resolve("content"));
        Path runtimeHome = tempDir.resolve("user-home");
        Path image = runtimeHome.resolve("resources/warrior_counters/custom.png");
        Files.createDirectories(image.getParent());
        Files.writeString(image, "png");
        System.setProperty(CONTENT_HOME_PROPERTY, content.toString());

        assertEquals(image, WarriorCounters.counterImagePath(runtimeHome, "resources/warrior_counters/custom.png"));
    }
}
