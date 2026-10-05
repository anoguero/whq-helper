package com.whq.app.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.whq.app.AppPaths;

class I18nTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreDefaults() {
        I18n.init(Path.of(""));
        I18n.setLanguage(Language.ES);
    }

    @Test
    void uiTranslationFilesHaveExactlyTheSameKeys() {
        Path directory = AppPaths.sharedPath(Path.of(""), "data/i18n");
        Map<String, String> es = ContentTranslations.parse(directory.resolve("ui-es.xml"));
        Map<String, String> en = ContentTranslations.parse(directory.resolve("ui-en.xml"));

        assertFalse(es.isEmpty());
        assertEquals(es.keySet(), en.keySet());
    }

    @Test
    void interpolatesNamedPlaceholders() {
        I18n.init(Path.of(""));
        I18n.setLanguage(Language.ES);
        assertEquals(
                "Se han añadido 3 cartas nuevas al montón 2.",
                I18n.t("simulator.addCardsDone", Map.of("count", 3, "pile", 2)));

        I18n.setLanguage(Language.EN);
        assertEquals("Event\n25%", I18n.t("dashboard.stats.eventProbability", Map.of("value", 25)));
    }

    @Test
    void fallsBackToTheKeyWhenTheFilesCannotBeLoaded() {
        I18n.init(tempDir);

        assertEquals("button.cancel", I18n.t("button.cancel"));
        assertEquals("simulator.addCardsDone", I18n.t("simulator.addCardsDone", Map.of("count", 1)));
    }
}
