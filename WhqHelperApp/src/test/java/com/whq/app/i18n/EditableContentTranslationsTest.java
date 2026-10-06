package com.whq.app.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.whq.app.AppPaths;

class EditableContentTranslationsTest {

    @TempDir
    Path tempDir;

    @Test
    void savesUserTranslationsToRuntimeHomeWhenSharedFileIsReadOnly() throws Exception {
        Path sharedHome = tempDir.resolve("app/shared");
        Path runtimeHome = tempDir.resolve("user-home");
        Path baseFile = tempDir.resolve("app/content/data/i18n/content-es.xml");
        Files.createDirectories(sharedHome.resolve("data/xml"));
        Files.createDirectories(baseFile.getParent());
        Files.writeString(baseFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <translations>
                  <entry key="monster.orc.name">Orco</entry>
                </translations>
                """);
        String baseContent = Files.readString(baseFile);
        assertTrue(baseFile.toFile().setWritable(false));
        AppPaths.bindSharedHome(runtimeHome, sharedHome);

        EditableContentTranslations translations = EditableContentTranslations.load(runtimeHome, Language.ES);
        translations.put("adventure.custom.name", "Mi aventura");
        translations.save();

        Path userFile = runtimeHome.resolve("data/i18n/userdefined-content-es.xml");
        assertEquals(baseContent, Files.readString(baseFile));
        assertTrue(Files.readString(userFile).contains("adventure.custom.name"));
        assertFalse(Files.readString(userFile).contains("monster.orc.name"));

        ContentTranslations merged = ContentTranslations.load(runtimeHome, Language.ES);
        assertEquals("Orco", merged.t("monster.orc.name", ""));
        assertEquals("Mi aventura", merged.t("adventure.custom.name", ""));
    }
}
