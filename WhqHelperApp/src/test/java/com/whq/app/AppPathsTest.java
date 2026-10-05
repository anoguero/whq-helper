package com.whq.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

    @TempDir
    Path tempDir;

    private String previousSharedHome;

    @BeforeEach
    void clearSharedHomeProperty() {
        previousSharedHome = System.getProperty(AppPaths.SHARED_HOME_PROPERTY);
        System.clearProperty(AppPaths.SHARED_HOME_PROPERTY);
    }

    @AfterEach
    void restoreSharedHomeProperty() {
        if (previousSharedHome == null) {
            System.clearProperty(AppPaths.SHARED_HOME_PROPERTY);
        } else {
            System.setProperty(AppPaths.SHARED_HOME_PROPERTY, previousSharedHome);
        }
    }

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
    void preparesWritableRuntimeHomeWithOnlyUserContent() throws Exception {
        Path appHome = tempDir.resolve("app");
        Path userHome = tempDir.resolve("user-home");
        Files.createDirectories(appHome.resolve("data/xml/monsters"));
        Files.createDirectories(appHome.resolve("resources"));
        Files.writeString(appHome.resolve("settings.cfg"), "Language=ES");
        Files.writeString(appHome.resolve("data/xml/sample.xml"), "<root/>");
        Files.writeString(appHome.resolve("data/xml/monsters/userdefined-monsters.xml"), "<monsters/>");
        Files.writeString(appHome.resolve("resources/logo.png"), "png");

        String previous = System.getProperty(AppPaths.USER_HOME_PROPERTY);
        System.setProperty(AppPaths.USER_HOME_PROPERTY, userHome.toString());
        try {
            Path runtimeHome = AppPaths.prepareRuntimeHome(appHome);

            assertEquals(userHome, runtimeHome);
            assertTrue(Files.exists(userHome.resolve("settings.cfg")));
            assertTrue(Files.exists(userHome.resolve("data/xml/monsters/userdefined-monsters.xml")));
            assertFalse(Files.exists(userHome.resolve("data/xml/sample.xml")));
            assertFalse(Files.exists(userHome.resolve("resources")));
        } finally {
            if (previous == null) {
                System.clearProperty(AppPaths.USER_HOME_PROPERTY);
            } else {
                System.setProperty(AppPaths.USER_HOME_PROPERTY, previous);
            }
        }
    }

    @Test
    void sharedHomeFromExplicitPropertyWins() throws Exception {
        Path appHome = createSharedHome(tempDir.resolve("repo/app"));
        createSharedHome(appHome.resolve("shared"));
        Path explicit = createSharedHome(tempDir.resolve("elsewhere"));
        System.setProperty(AppPaths.SHARED_HOME_PROPERTY, explicit.toString());

        assertEquals(explicit, AppPaths.resolveSharedHome(appHome));
    }

    @Test
    void sharedHomeBundledInsideAppHome() throws Exception {
        Path appHome = tempDir.resolve("app");
        Path bundled = createSharedHome(appHome.resolve("shared"));
        createSharedHome(tempDir.resolve("shared"));

        assertEquals(bundled, AppPaths.resolveSharedHome(appHome));
    }

    @Test
    void sharedHomeNextToAppHomeInDevelopment() throws Exception {
        Path appHome = tempDir.resolve("WhqHelperApp");
        Files.createDirectories(appHome);
        Path sibling = createSharedHome(tempDir.resolve("shared"));

        assertEquals(sibling, AppPaths.resolveSharedHome(appHome));
    }

    @Test
    void sharedHomeFallsBackToAppHome() throws Exception {
        Path appHome = createSharedHome(tempDir.resolve("legacy-app"));

        assertEquals(appHome, AppPaths.resolveSharedHome(appHome));
    }

    @Test
    void ignoresExplicitSharedHomeWithoutContent() throws Exception {
        Path appHome = tempDir.resolve("app");
        Path bundled = createSharedHome(appHome.resolve("shared"));
        System.setProperty(AppPaths.SHARED_HOME_PROPERTY, tempDir.resolve("missing").toString());

        assertEquals(bundled, AppPaths.resolveSharedHome(appHome));
    }

    @Test
    void boundSharedHomeIsUsedForRuntimeHomeOutsideAppHome() throws Exception {
        Path sharedHome = createSharedHome(tempDir.resolve("app/shared"));
        Path runtimeHome = tempDir.resolve("user-home");
        Files.createDirectories(runtimeHome);

        AppPaths.bindSharedHome(runtimeHome, sharedHome);

        assertEquals(sharedHome, AppPaths.sharedHome(runtimeHome));
        assertEquals(sharedHome.resolve("data/fonts"), AppPaths.sharedPath(runtimeHome, "data/fonts"));
    }

    @Test
    void listsBaseContentFromSharedAndUserContentFromRuntimeHome() throws Exception {
        Path sharedHome = createSharedHome(tempDir.resolve("app/shared"));
        Path runtimeHome = tempDir.resolve("user-home");
        Files.createDirectories(sharedHome.resolve("data/xml/monsters"));
        Files.createDirectories(runtimeHome.resolve("data/xml/monsters"));
        Files.writeString(sharedHome.resolve("data/xml/monsters/base-monsters.xml"), "<monsters/>");
        Files.writeString(sharedHome.resolve("data/xml/monsters/userdefined-stray.xml"), "<monsters/>");
        Files.writeString(runtimeHome.resolve("data/xml/monsters/userdefined-monsters.xml"), "<monsters/>");
        // Copia antigua del contenido base en el directorio escribible: no debe cargarse.
        Files.writeString(runtimeHome.resolve("data/xml/monsters/base-monsters.xml"), "<monsters/>");
        AppPaths.bindSharedHome(runtimeHome, sharedHome);

        List<Path> files = AppPaths.listContentFiles(runtimeHome, "data/xml/monsters");

        assertEquals(
                List.of(
                        sharedHome.resolve("data/xml/monsters/base-monsters.xml"),
                        runtimeHome.resolve("data/xml/monsters/userdefined-monsters.xml")),
                files.stream().sorted().toList());
    }

    @Test
    void listsEverythingWhenSharedHomeIsTheRuntimeHome() throws Exception {
        Path home = createSharedHome(tempDir.resolve("legacy-app"));
        Files.createDirectories(home.resolve("data/xml/monsters"));
        Files.writeString(home.resolve("data/xml/monsters/base-monsters.xml"), "<monsters/>");
        Files.writeString(home.resolve("data/xml/monsters/userdefined-monsters.xml"), "<monsters/>");

        assertEquals(2, AppPaths.listContentFiles(home, "data/xml/monsters").size());
    }

    @Test
    void resolvesContentFromSharedHomeThenRuntimeHome() throws Exception {
        Path sharedHome = createSharedHome(tempDir.resolve("app/shared"));
        Path runtimeHome = tempDir.resolve("user-home");
        Files.createDirectories(sharedHome.resolve("resources/tiles"));
        Files.createDirectories(runtimeHome.resolve("resources/tiles"));
        Files.writeString(sharedHome.resolve("resources/tiles/base.png"), "png");
        Files.writeString(runtimeHome.resolve("resources/tiles/custom.png"), "png");
        AppPaths.bindSharedHome(runtimeHome, sharedHome);

        assertEquals(sharedHome.resolve("resources/tiles/base.png"), AppPaths.resolveContent(runtimeHome, "resources/tiles/base.png"));
        assertEquals(runtimeHome.resolve("resources/tiles/custom.png"), AppPaths.resolveContent(runtimeHome, "resources/tiles/custom.png"));
    }

    private static Path createSharedHome(Path directory) throws Exception {
        Files.createDirectories(directory.resolve("data/xml"));
        return directory.toAbsolutePath().normalize();
    }
}
