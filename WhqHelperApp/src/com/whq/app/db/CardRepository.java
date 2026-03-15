package com.whq.app.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

@Deprecated(forRemoval = false)
public class CardRepository {
    public static final String DEFAULT_ENVIRONMENT = "The Old World";

    private final String jdbcUrl;

    public CardRepository(Path dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    public List<DungeonCard> loadCards() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            ensureSchema(connection);
            seedDataIfRequired(connection);
            return readCards(connection);
        }
    }

    public List<String> loadEnvironments() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            ensureSchema(connection);
            List<String> environments = new ArrayList<>();
            String sql = """
                SELECT DISTINCT environment
                FROM dungeon_cards
                ORDER BY environment COLLATE NOCASE
                """;
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    environments.add(normalizeEnvironment(resultSet.getString("environment")));
                }
            }
            return environments;
        }
    }

    public List<DungeonCard> loadObjectiveRoomsByEnvironment(String environment) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            ensureSchema(connection);
            List<DungeonCard> cards = new ArrayList<>();
            String sql = """
                SELECT id, name, type, environment, copy_count, enabled, description_text, rules_text, tile_image_path
                FROM dungeon_cards
                WHERE UPPER(environment) = UPPER(?)
                  AND type = ?
                  AND enabled = 1
                  AND copy_count > 0
                ORDER BY name COLLATE NOCASE
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, normalizeEnvironment(environment));
                statement.setString(2, CardType.OBJECTIVE_ROOM.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        cards.add(new DungeonCard(
                                resultSet.getLong("id"),
                                resultSet.getString("name"),
                                CardType.valueOf(resultSet.getString("type").toUpperCase(Locale.ROOT)),
                                normalizeEnvironment(resultSet.getString("environment")),
                                resultSet.getInt("copy_count"),
                                resultSet.getInt("enabled") == 1,
                                resultSet.getString("description_text"),
                                resultSet.getString("rules_text"),
                                resultSet.getString("tile_image_path")));
                    }
                }
            }
            return cards;
        }
    }

    public void updateCardAvailability(long cardId, int copyCount, boolean enabled) throws SQLException {
        if (copyCount < 0) {
            throw new IllegalArgumentException("El número de copias no puede ser negativo.");
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            ensureSchema(connection);
            String sql = """
                UPDATE dungeon_cards
                SET copy_count = ?, enabled = ?
                WHERE id = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, copyCount);
                statement.setInt(2, enabled ? 1 : 0);
                statement.setLong(3, cardId);
                statement.executeUpdate();
            }
        }
    }

    public void deleteCard(long cardId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            ensureSchema(connection);
            String sql = "DELETE FROM dungeon_cards WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, cardId);
                statement.executeUpdate();
            }
        }
    }

    public void insertCards(List<DungeonCard> cards) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                insertCardBatch(connection, cards);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void ensureSchema(Connection connection) throws SQLException {
        createSchema(connection);
        ensureEnvironmentColumn(connection);
        ensureAvailabilityColumns(connection);
    }

    private void createSchema(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS dungeon_cards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                environment TEXT NOT NULL DEFAULT 'The Old World',
                copy_count INTEGER NOT NULL DEFAULT 1 CHECK(copy_count >= 0),
                enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0, 1)),
                description_text TEXT NOT NULL,
                rules_text TEXT NOT NULL,
                tile_image_path TEXT NOT NULL
            )
            """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void ensureEnvironmentColumn(Connection connection) throws SQLException {
        if (!hasColumn(connection, "environment")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "ALTER TABLE dungeon_cards ADD COLUMN environment TEXT NOT NULL DEFAULT 'The Old World'");
            }
        }
    }

    private void ensureAvailabilityColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!hasColumn(connection, "copy_count")) {
                statement.execute("ALTER TABLE dungeon_cards ADD COLUMN copy_count INTEGER NOT NULL DEFAULT 1");
            }
            if (!hasColumn(connection, "enabled")) {
                statement.execute("ALTER TABLE dungeon_cards ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1");
            }
        }
    }

    private boolean hasColumn(Connection connection, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(dungeon_cards)")) {
            while (columns.next()) {
                String name = columns.getString("name");
                if (columnName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void seedDataIfRequired(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM dungeon_cards")) {
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                return;
            }
        }

        List<DungeonCard> seedCards = List.of(
                new DungeonCard(
                        0,
                        "SYLVAN RESPITE",
                        CardType.DUNGEON_ROOM,
                        DEFAULT_ENVIRONMENT,
                        1,
                        true,
                        "Autumn scents fill the air as leaves crackle underfoot, a long hidden Elven shrine appears ahead.",
                        "The Sylvan Respite will always trigger an event card. The Wood Elf player gains 1 extra attack should monsters appear.",
                        "resources/tiles/sylvan-respite.png"),
                new DungeonCard(
                        0,
                        "EERIE CHASM",
                        CardType.CORRIDOR,
                        DEFAULT_ENVIRONMENT,
                        1,
                        true,
                        "The mists coil about your feet and fill the chasm ahead, be sure your chances to cross.",
                        "The Eerie Chasm can be crossed through use of ropes taking D6 turns to prepare, or leap. Roll a D6 to leap, a 1 is a deadly fall.",
                        "resources/tiles/eerie-chasm.png"),
                new DungeonCard(
                        0,
                        "WICKED WELL",
                        CardType.OBJECTIVE_ROOM,
                        DEFAULT_ENVIRONMENT,
                        1,
                        true,
                        "Vile waters stir beneath broken boards, the last sacred water font mere steps away.",
                        "You arrive just in time to protect the sacred font. See the Adventure Book or ask the GM for what you encounter.",
                        "resources/tiles/wicked-well.png"),
                new DungeonCard(
                        0,
                        "RUNIC ANTECHAMBER",
                        CardType.SPECIAL,
                        DEFAULT_ENVIRONMENT,
                        1,
                        true,
                        "Ancient runes glow as soon as a warrior crosses the threshold. Cold whispers fill the chamber.",
                        "When revealed, draw one event card. Wizards gain +1 to all casting rolls until the start of the next Power Phase.",
                        "resources/tiles/sylvan-respite.png"));

        insertCardBatch(connection, seedCards);
    }

    private void insertCardBatch(Connection connection, List<DungeonCard> cards) throws SQLException {
        if (cards.isEmpty()) {
            return;
        }

        String insertSql = """
            INSERT INTO dungeon_cards (name, type, environment, copy_count, enabled, description_text, rules_text, tile_image_path)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            for (DungeonCard card : cards) {
                insert.setString(1, card.getName());
                insert.setString(2, card.getType().name());
                insert.setString(3, normalizeEnvironment(card.getEnvironment()));
                insert.setInt(4, Math.max(0, card.getCopyCount()));
                insert.setInt(5, card.isEnabled() ? 1 : 0);
                insert.setString(6, card.getDescriptionText());
                insert.setString(7, card.getRulesText());
                insert.setString(8, card.getTileImagePath());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private List<DungeonCard> readCards(Connection connection) throws SQLException {
        List<DungeonCard> cards = new ArrayList<>();
        String sql = """
            SELECT id, name, type, environment, copy_count, enabled, description_text, rules_text, tile_image_path
            FROM dungeon_cards
            ORDER BY id
            """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                cards.add(new DungeonCard(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        CardType.valueOf(resultSet.getString("type").toUpperCase(Locale.ROOT)),
                        normalizeEnvironment(resultSet.getString("environment")),
                        resultSet.getInt("copy_count"),
                        resultSet.getInt("enabled") == 1,
                        resultSet.getString("description_text"),
                        resultSet.getString("rules_text"),
                        resultSet.getString("tile_image_path")));
            }
        }
        return cards;
    }

    private String normalizeEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return DEFAULT_ENVIRONMENT;
        }
        return environment.trim();
    }
}
