package com.whq.app;

import java.net.URISyntaxException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class AppPaths {

    static final String APP_HOME_PROPERTY = "whq.app.home";
    static final String APP_HOME_ENV = "WHQ_APP_HOME";
    static final String USER_HOME_PROPERTY = "whq.user.home";
    static final String USER_HOME_ENV = "WHQ_USER_HOME";
    static final String SHARED_HOME_PROPERTY = "whq.shared.home";
    static final String SHARED_HOME_ENV = "WHQ_SHARED_HOME";
    static final String SHARED_DIR = "shared";
    static final String CONTENT_HOME_PROPERTY = "whq.content.home";
    static final String CONTENT_HOME_ENV = "WHQ_CONTENT_HOME";
    static final String CONTENT_DIR = "content";
    static final String DEVELOPMENT_CONTENT_DIR = "whq-content";
    private static final String APP_NAME = "WHQ Helper";
    private static final String USER_DEFINED_PREFIX = "userdefined-";

    // Shared home asociado a cada runtime home. El runtime home escribible puede vivir fuera del
    // app home (p. ej. ~/.local/share/whq-helper), donde la cascada no encontraria shared/.
    private static final Map<Path, Path> BOUND_SHARED_HOMES = new ConcurrentHashMap<>();

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

    /**
     * Directorio con el contenido base de solo lectura (XML, imagenes, fuentes, i18n).
     * Cascada: -Dwhq.shared.home / WHQ_SHARED_HOME, &lt;appHome&gt;/shared (empaquetado),
     * &lt;appHome&gt;/../shared (desarrollo) y, por compatibilidad, el propio appHome.
     */
    public static Path resolveSharedHome(Path appHome) {
        Path explicitHome = normalize(explicitSharedHome());
        if (isSharedHome(explicitHome)) {
            return explicitHome;
        }

        Path normalizedAppHome = normalize(appHome);
        if (normalizedAppHome == null) {
            return null;
        }

        Path bundledHome = normalizedAppHome.resolve(SHARED_DIR);
        if (isSharedHome(bundledHome)) {
            return bundledHome;
        }

        Path parent = normalizedAppHome.getParent();
        if (parent != null && isSharedHome(parent.resolve(SHARED_DIR))) {
            return parent.resolve(SHARED_DIR);
        }

        return normalizedAppHome;
    }

    /** Asocia el shared home resuelto al arrancar con el runtime home que usara la aplicacion. */
    public static void bindSharedHome(Path runtimeHome, Path sharedHome) {
        Path normalizedRuntimeHome = normalize(runtimeHome);
        Path normalizedSharedHome = normalize(sharedHome);
        if (normalizedRuntimeHome != null && normalizedSharedHome != null) {
            BOUND_SHARED_HOMES.put(normalizedRuntimeHome, normalizedSharedHome);
        }
    }

    /**
     * Paquete de contenido de juego (XML de datos, traducciones de contenido, graficos, losetas,
     * fuentes...). Va aparte del shared home, que solo trae lo propio de la aplicacion (esquemas e
     * i18n de interfaz), porque la aplicacion se distribuye sin contenido. Cascada:
     * -Dwhq.content.home / WHQ_CONTENT_HOME, &lt;appHome&gt;/content (empaquetado, junto a shared/) y
     * &lt;repo&gt;/../whq-content (desarrollo: hermano del repositorio). Si el shared home no es un
     * directorio shared/ (disposicion antigua), el contenido esta en el mismo sitio.
     *
     * <p>Si no hay paquete devuelve igualmente la ruta donde se esperaria (ver {@link #hasContent}):
     * los cargadores ven directorios vacios y el aviso puede decir donde colocarlo.
     */
    public static Path resolveContentHome(Path sharedHome) {
        Path explicitHome = normalize(explicitContentHome());
        if (explicitHome != null) {
            return explicitHome;
        }

        Path normalizedSharedHome = normalize(sharedHome);
        if (normalizedSharedHome == null) {
            return null;
        }
        // Disposicion antigua: sin shared/, el propio app home hace de shared home y trae el contenido.
        Path sharedName = normalizedSharedHome.getFileName();
        if (sharedName == null || !SHARED_DIR.equals(sharedName.toString())) {
            return normalizedSharedHome;
        }
        Path appOrRepoHome = normalizedSharedHome.getParent();
        if (appOrRepoHome == null) {
            return normalizedSharedHome.resolve(CONTENT_DIR);
        }

        Path bundledHome = appOrRepoHome.resolve(CONTENT_DIR);
        if (Files.isDirectory(bundledHome)) {
            return bundledHome;
        }

        Path workspace = appOrRepoHome.getParent();
        Path developmentHome = workspace == null ? null : workspace.resolve(DEVELOPMENT_CONTENT_DIR);
        if (developmentHome != null && Files.isDirectory(developmentHome)) {
            return developmentHome;
        }

        // Sin paquete: en un checkout del repositorio (shared/ junto a WhqHelperApp/) se espera como
        // hermano del repositorio; en una instalacion, junto a shared/.
        boolean repositoryCheckout = Files.isDirectory(appOrRepoHome.resolve("WhqHelperApp"));
        return repositoryCheckout && developmentHome != null ? developmentHome : bundledHome;
    }

    /** Paquete de contenido del runtime home (ver {@link #resolveContentHome}). */
    public static Path contentHome(Path runtimeHome) {
        return resolveContentHome(sharedHome(runtimeHome));
    }

    /** Ruta de un recurso de contenido relativa al paquete de contenido. */
    public static Path contentPath(Path runtimeHome, String relativePath) {
        Path contentHome = Objects.requireNonNull(
                contentHome(runtimeHome),
                () -> "No se puede resolver el contenido: runtimeHome es null (ruta pedida: " + relativePath + ").");
        return contentHome.resolve(relativePath);
    }

    /** Si hay paquete de contenido: su directorio existe. */
    public static boolean hasContent(Path runtimeHome) {
        Path contentHome = contentHome(runtimeHome);
        return contentHome != null && Files.isDirectory(contentHome);
    }

    /** Shared home de un runtime home: el asociado al arrancar o, si no hay, el de la cascada. */
    public static Path sharedHome(Path runtimeHome) {
        Path normalizedRuntimeHome = normalize(runtimeHome);
        if (normalizedRuntimeHome == null) {
            return null;
        }
        Path bound = BOUND_SHARED_HOMES.get(normalizedRuntimeHome);
        return bound != null ? bound : resolveSharedHome(normalizedRuntimeHome);
    }

    /** Ruta de un recurso propio de la aplicacion (esquemas, i18n de interfaz) relativa al shared home. */
    public static Path sharedPath(Path runtimeHome, String relativePath) {
        Path sharedHome = Objects.requireNonNull(
                sharedHome(runtimeHome),
                () -> "No se puede resolver el shared home: runtimeHome es null (ruta pedida: " + relativePath + ").");
        return sharedHome.resolve(relativePath);
    }

    /**
     * Resuelve una ruta de contenido relativa (p. ej. un tileImagePath): primero en el paquete de
     * contenido y, si no existe ahi, en el runtime home, donde el usuario puede tener imagenes propias.
     */
    public static Path resolveContent(Path runtimeHome, String relativePath) {
        Path path = Path.of(relativePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path contentPath = contentPath(runtimeHome, relativePath).normalize();
        if (Files.exists(contentPath)) {
            return contentPath;
        }
        Path userPath = normalize(runtimeHome).resolve(relativePath).normalize();
        return Files.exists(userPath) ? userPath : contentPath;
    }

    public static boolean isUserDefined(Path file) {
        Path fileName = file == null ? null : file.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).startsWith(USER_DEFINED_PREFIX);
    }

    /**
     * Ficheros de una categoria de contenido: los base del paquete de contenido y los userdefined-* del
     * runtime home. Del runtime home solo se toman userdefined-*, asi una copia antigua del contenido
     * base que haya quedado en el directorio escribible no se carga.
     */
    public static List<Path> listContentFiles(Path runtimeHome, String relativeDirectory) throws IOException {
        Path normalizedRuntimeHome = normalize(runtimeHome);
        Path contentDirectory = contentHome(normalizedRuntimeHome).resolve(relativeDirectory).normalize();
        Path userDirectory = normalizedRuntimeHome.resolve(relativeDirectory).normalize();

        List<Path> files = new ArrayList<>();
        if (contentDirectory.equals(userDirectory)) {
            addFiles(files, contentDirectory, path -> true);
        } else {
            addFiles(files, contentDirectory, path -> !isUserDefined(path));
            addFiles(files, userDirectory, AppPaths::isUserDefined);
        }
        return files;
    }

    private static void addFiles(List<Path> files, Path directory, Predicate<Path> filter) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile).filter(filter).forEach(files::add);
        }
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
            // El contenido base se lee del shared home: solo se copia el contenido del usuario.
            copyUserDefinedIfMissing(normalizedAppHome.resolve("data"), writableHome.resolve("data"));
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

    static Path explicitContentHome() {
        String propertyValue = System.getProperty(CONTENT_HOME_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue.trim());
        }

        String envValue = System.getenv(CONTENT_HOME_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return Path.of(envValue.trim());
        }

        return null;
    }

    static Path explicitSharedHome() {
        String propertyValue = System.getProperty(SHARED_HOME_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue.trim());
        }

        String envValue = System.getenv(SHARED_HOME_ENV);
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
                || Files.isDirectory(directory.resolve("resources"))
                || Files.isDirectory(directory.resolve(SHARED_DIR));
    }

    private static boolean isSharedHome(Path directory) {
        return directory != null && Files.isDirectory(directory.resolve("data/xml"));
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

    private static void copyUserDefinedIfMissing(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isUserDefined(file)) {
                    copyIfMissing(file, target.resolve(source.relativize(file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
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
