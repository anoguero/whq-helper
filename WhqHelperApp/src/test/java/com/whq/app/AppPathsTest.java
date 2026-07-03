package com.whq.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesMavenClassesDirectoryToProjectRoot() {
        Path path = Path.of("/tmp/example/target/classes");

        assertEquals(Path.of("/tmp/example"), AppPaths.codeSourceAppHome(path));
    }

    @Test
    void keepsPackagedJarDirectoryAsAppHome() {
        Path path = Path.of("/tmp/WHQ Helper/app/whq-helper-app-1.0.0.jar");

        assertEquals(Path.of("/tmp/WHQ Helper/app"), AppPaths.codeSourceAppHome(path));
    }

    @Test
    void preparesWritableRuntimeHomeFromExplicitUserHome() throws Exception {
        Path appHome = tempDir.resolve("app");
        Path userHome = tempDir.resolve("user-home");
        Files.createDirectories(appHome.resolve("data/xml"));
        Files.createDirectories(appHome.resolve("resources"));
        Files.writeString(appHome.resolve("settings.cfg"), "Language=ES");
        Files.writeString(appHome.resolve("data/xml/sample.xml"), "<root/>");
        Files.writeString(appHome.resolve("resources/logo.png"), "png");

        String previous = System.getProperty(AppPaths.USER_HOME_PROPERTY);
        System.setProperty(AppPaths.USER_HOME_PROPERTY, userHome.toString());
        try {
            Path runtimeHome = AppPaths.prepareRuntimeHome(appHome);

            assertEquals(userHome, runtimeHome);
            assertTrue(Files.exists(userHome.resolve("settings.cfg")));
            assertTrue(Files.exists(userHome.resolve("data/xml/sample.xml")));
            assertTrue(Files.exists(userHome.resolve("resources/logo.png")));
        } finally {
            if (previous == null) {
                System.clearProperty(AppPaths.USER_HOME_PROPERTY);
            } else {
                System.setProperty(AppPaths.USER_HOME_PROPERTY, previous);
            }
        }
    }
}
