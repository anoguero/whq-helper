package com.whq.app.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DungeonCardXmlValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsValidXmlCatalog() throws Exception {
        Path schemaPath = tempDir.resolve("data/xml/dungeon/whq-dungeon-cards-schema.xsd");
        Path xmlPath = tempDir.resolve("data/xml/dungeon/dungeon-cards.xml");
        Path tilePath = tempDir.resolve("resources/tiles/room.png");
        Files.createDirectories(schemaPath.getParent());
        Files.createDirectories(tilePath.getParent());
        Files.writeString(schemaPath, DungeonCardXmlValidator.defaultSchema());
        Files.writeString(tilePath, "tile");
        Files.writeString(xmlPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <dungeonCards>
                  <card id="1" name="ROOM" type="DUNGEON_ROOM" environment="The Old World" copyCount="1" enabled="true">
                    <description>Desc</description>
                    <rules>Rules</rules>
                    <tileImagePath>resources/tiles/room.png</tileImagePath>
                  </card>
                </dungeonCards>
                """);

        DungeonCardXmlValidator validator = new DungeonCardXmlValidator(tempDir, schemaPath, xmlPath);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void rejectsMissingTileReference() throws Exception {
        Path schemaPath = tempDir.resolve("data/xml/dungeon/whq-dungeon-cards-schema.xsd");
        Path xmlPath = tempDir.resolve("data/xml/dungeon/dungeon-cards.xml");
        Files.createDirectories(schemaPath.getParent());
        Files.writeString(schemaPath, DungeonCardXmlValidator.defaultSchema());
        Files.writeString(xmlPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <dungeonCards>
                  <card id="1" name="ROOM" type="DUNGEON_ROOM" environment="The Old World" copyCount="1" enabled="true">
                    <description>Desc</description>
                    <rules>Rules</rules>
                    <tileImagePath>resources/tiles/missing.png</tileImagePath>
                  </card>
                </dungeonCards>
                """);

        DungeonCardXmlValidator validator = new DungeonCardXmlValidator(tempDir, schemaPath, xmlPath);

        assertThrows(DungeonCardStorageException.class, validator::validate);
    }
}
