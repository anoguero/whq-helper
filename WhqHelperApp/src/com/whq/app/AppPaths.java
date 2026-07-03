package com.whq.app;

import java.net.URISyntaxException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;

public final class AppPaths {

    static final String APP_HOME_PROPERTY = "whq.app.home";
    static final String APP_HOME_ENV = "WHQ_APP_HOME";
    static final String USER_HOME_PROPERTY = "whq.user.home";
    static final String USER_HOME_ENV = "WHQ_USER_HOME";
    private static final String APP_NAME = "WHQ Helper";

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

    public static Path prepareRuntimeHome(Path appHome) {
        Path normalizedAppHome = normalize(appHome);
        Path writableHome = resolveWritableHome(normalizedAppHome);
        if (normalizedAppHome == null || writableHome == null || normalizedAppHome.equals(writableHome)) {
            return normalizedAppHome;
        }

        try {
            Files.createDirectories(writableHome);
            copyIfMissing(normalizedAppHome.resolve("settings.cfg"), writableHome.resolve("settings.cfg"));
            copyDirectoryIfMissing(normalizedAppHome.resolve("data"), writableHome.resolve("data"));
            copyDirectoryIfMissing(normalizedAppHome.resolve("resources"), writableHome.resolve("resources"));
            copyDirectoryIfMissing(normalizedAppHome.resolve("lib"), writableHome.resolve("lib"));
            return writableHome;
        } catch (IOException ex) {
            return normalizedAppHome;
        }
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

    static Path explicitUserHome() {
        String propertyValue = System.getProperty(USER_HOME_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue.trim());
        }

        String envValue = System.getenv(USER_HOME_ENV);
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

    private static Path resolveWritableHome(Path appHome) {
        Path explicitHome = normalize(explicitUserHome());
        if (explicitHome != null) {
            return explicitHome;
        }

        if (isWritableDirectory(appHome)) {
            return appHome;
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        Path userHome = normalize(Path.of(System.getProperty("user.home", ".")));
        if (userHome == null) {
            return appHome;
        }

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData).resolve(APP_NAME).toAbsolutePath().normalize();
            }
            return userHome.resolve("AppData/Roaming").resolve(APP_NAME);
        }
        if (os.contains("mac")) {
            return userHome.resolve("Library/Application Support").resolve(APP_NAME);
        }

        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome).resolve("whq-helper").toAbsolutePath().normalize();
        }
        return userHome.resolve(".local/share/whq-helper");
    }

    private static boolean isWritableDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        try {
            Path tempFile = Files.createTempFile(directory, ".whq-write-test", ".tmp");
            Files.deleteIfExists(tempFile);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void copyIfMissing(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source) || Files.exists(target)) {
            return;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, target);
    }

    private static void copyDirectoryIfMissing(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative);
                if (!Files.exists(destination)) {
                    Files.copy(file, destination);
                }
                return FileVisitResult.CONTINUE;
            }
        });
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
