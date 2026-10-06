package com.whq.app;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;

/**
 * Para tests que comprueban el contenido de juego real (el paquete de contenido, que no esta en el
 * repositorio): si no lo encuentran, por ejemplo en CI, se saltan en lugar de fallar.
 */
public final class RealContent {

    private RealContent() {
    }

    public static void assumeAvailable() {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Assumptions.assumeTrue(
                Files.isDirectory(AppPaths.contentPath(projectRoot, "data/xml")),
                () -> "Sin paquete de contenido en " + AppPaths.contentHome(projectRoot) + " (WHQ_CONTENT_HOME)");
    }
}
