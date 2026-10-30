package me.foesio.foPvPToggle.data;

import me.foesio.core.logging.FoFileLogger;
import me.foesio.foPvPToggle.FoPvPToggle;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UserDataStore {
    private final FoPvPToggle plugin;
    private final FoFileLogger fileLogService;
    private Connection connection;

    public UserDataStore(FoPvPToggle plugin, FoFileLogger fileLogService) {
        this.plugin = plugin;
        this.fileLogService = fileLogService;
    }

    public synchronized void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("failed to create plugin data folder");
            }
            File database = new File(plugin.getDataFolder(), "userdata.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_pvp_state (
                            uuid TEXT PRIMARY KEY,
                            pvp_enabled INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """);
            }
            fileLogService.info("Initialized userdata.db.");
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized Map<UUID, Boolean> loadStates() {
        Map<UUID, Boolean> states = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, pvp_enabled FROM player_pvp_state");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                try {
                    states.put(UUID.fromString(resultSet.getString("uuid")), resultSet.getInt("pvp_enabled") != 0);
                } catch (IllegalArgumentException ignored) {
                    fileLogService.warn("Skipped invalid UUID in userdata.db.");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load player PvP states", exception);
        }
        return states;
    }

    public synchronized void saveState(UUID playerId, boolean pvpEnabled) {
        trySaveState(playerId, pvpEnabled);
    }

    public synchronized boolean trySaveState(UUID playerId, boolean pvpEnabled) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_pvp_state(uuid, pvp_enabled, updated_at)
                VALUES(?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    pvp_enabled = excluded.pvp_enabled,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, pvpEnabled ? 1 : 0);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning(plugin.plainPrefix() + " Failed to save PvP state for " + playerId + ": " + exception.getMessage());
            fileLogService.error("Failed to save PvP state for " + playerId + ": " + exception.getMessage(), exception);
            return false;
        }
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning(plugin.plainPrefix() + " Failed to close userdata.db: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }
}
