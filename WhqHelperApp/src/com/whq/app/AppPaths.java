package com.whq.app;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

public final class AppPaths {

    static final String APP_HOME_PROPERTY = "whq.app.home";
    static final String APP_HOME_ENV = "WHQ_APP_HOME";

    private AppPaths() {
    }

    public static Path resolveAppHome() {
        Path explicitHome = normalize(explicitAppHome());
        if (isAppHome(explicitHome)) {
            return explicitHome;
        }

        Path workingDirectory = normalize(Path.of(""));
        if (isAppHome(workingDirectory)) {
            return workingDirectory;
        }

        Path codeSourceHome = normalize(codeSourceAppHome());
        if (isAppHome(codeSourceHome)) {
            return codeSourceHome;
        }

        return workingDirectory;
    }

    static Path explicitAppHome() {
        String propertyValue = System.getProperty(APP_HOME_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue.trim());
        }

        String envValue = System.getenv(APP_HOME_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return Path.of(envValue.trim());
        }

        return null;
    }

    static Path codeSourceAppHome() {
        try {
            CodeSource codeSource = WhqCardRendererApp.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }

            return codeSourceAppHome(Path.of(codeSource.getLocation().toURI()));
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    static Path codeSourceAppHome(Path location) {
        Path normalizedLocation = normalize(location);
        if (normalizedLocation == null) {
            return null;
        }
        if (looksLikeDevelopmentOutput(normalizedLocation)) {
            return normalizeDevelopmentOutput(normalizedLocation);
        }
        if (Files.isDirectory(normalizedLocation)) {
            return normalizeDevelopmentOutput(normalizedLocation);
        }

        Path parent = normalizedLocation.getParent();
        return parent == null ? null : parent.toAbsolutePath().normalize();
    }

    private static Path normalizeDevelopmentOutput(Path location) {
        String normalized = normalizedPathString(location);
        if (normalized.endsWith("/target/classes")) {
            Path targetDir = location.getParent();
            return targetDir == null ? location : targetDir.getParent();
        }
        if (normalized.endsWith("/build/classes/java/main")) {
            Path javaDir = location.getParent();
            if (javaDir == null) {
                return location;
            }
            Path classesDir = javaDir.getParent();
            if (classesDir == null) {
                return location;
            }
            Path buildDir = classesDir.getParent();
            return buildDir == null ? location : buildDir.getParent();
        }
        return location;
    }

    private static boolean looksLikeDevelopmentOutput(Path location) {
        String normalized = normalizedPathString(location);
        return normalized.endsWith("/target/classes")
                || normalized.endsWith("/build/classes/java/main");
    }

    private static boolean isAppHome(Path directory) {
        if (directory == null) {
            return false;
        }
        return Files.exists(directory.resolve("settings.cfg"))
                || Files.isDirectory(directory.resolve("data"))
                || Files.isDirectory(directory.resolve("resources"));
    }

    private static Path normalize(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize();
    }

    private static String normalizedPathString(Path path) {
        return path.toString().replace('\\', '/');
    }
}
