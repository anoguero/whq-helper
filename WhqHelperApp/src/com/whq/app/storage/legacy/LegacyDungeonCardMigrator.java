package com.whq.app.storage.legacy;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import com.whq.app.db.CardRepository;
import com.whq.app.model.DungeonCard;

public final class LegacyDungeonCardMigrator {
    private final Path projectRoot;

    public LegacyDungeonCardMigrator(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    public List<DungeonCard> loadLegacyCards() {
        try {
            return new CardRepository(projectRoot.resolve("data/whq-cards.db")).loadCards();
        } catch (SQLException ex) {
            return List.of();
        }
    }
}
