package com.whq.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AppPathsTest {

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
}
