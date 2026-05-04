package me.colinstudios.lobbygames.cookie;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CookieStorage {
    private final JavaPlugin plugin;
    private Connection connection;

    public CookieStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Could not create plugin data folder");
            }

            File database = new File(plugin.getDataFolder(), "cookies.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cookie_accounts (
                        uuid TEXT PRIMARY KEY,
                        name TEXT,
                        cookies INTEGER NOT NULL DEFAULT 0,
                        click_power_level INTEGER NOT NULL DEFAULT 0,
                        auto_clicker_level INTEGER NOT NULL DEFAULT 0,
                        oven_level INTEGER NOT NULL DEFAULT 0,
                        fortune_level INTEGER NOT NULL DEFAULT 0,
                        discount_level INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            }
            addColumnIfMissing("oven_level", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing("fortune_level", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing("discount_level", "INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not open cookie SQLite database", exception);
        }
    }

    public synchronized CookieAccount getAccount(OfflinePlayer player) {
        ensurePlayerRow(player);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT cookies, click_power_level, auto_clicker_level, oven_level, fortune_level, discount_level FROM cookie_accounts WHERE uuid = ?"
        )) {
            statement.setString(1, player.getUniqueId().toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new CookieAccount(
                        result.getLong("cookies"),
                        result.getInt("click_power_level"),
                        result.getInt("auto_clicker_level"),
                        result.getInt("oven_level"),
                        result.getInt("fortune_level"),
                        result.getInt("discount_level")
                    );
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load cookie account", exception);
        }
        return new CookieAccount(0L, 0, 0, 0, 0, 0);
    }

    public synchronized Map<UUID, CookieAccount> getAutoClickerAccounts() {
        Map<UUID, CookieAccount> accounts = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT uuid, cookies, click_power_level, auto_clicker_level, oven_level, fortune_level, discount_level FROM cookie_accounts WHERE auto_clicker_level > 0"
        );
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                accounts.put(
                    UUID.fromString(result.getString("uuid")),
                    new CookieAccount(
                        result.getLong("cookies"),
                        result.getInt("click_power_level"),
                        result.getInt("auto_clicker_level"),
                        result.getInt("oven_level"),
                        result.getInt("fortune_level"),
                        result.getInt("discount_level")
                    )
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load auto clicker accounts", exception);
        }
        return accounts;
    }

    public synchronized long getTotalCookies() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(sum(cookies), 0) AS total FROM cookie_accounts");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong("total") : 0L;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load total cookies", exception);
        }
    }

    public synchronized List<CookieLeaderboardEntry> getTopAccounts(int limit) {
        List<CookieLeaderboardEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT uuid, COALESCE(name, uuid) AS name, cookies
            FROM cookie_accounts
            ORDER BY cookies DESC, updated_at ASC
            LIMIT ?
            """)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                int rank = 1;
                while (result.next()) {
                    entries.add(new CookieLeaderboardEntry(
                        rank++,
                        UUID.fromString(result.getString("uuid")),
                        result.getString("name"),
                        result.getLong("cookies")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load cookie leaderboard", exception);
        }
        return entries;
    }

    public synchronized long addCookies(OfflinePlayer player, long amount) {
        ensurePlayerRow(player);
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE cookie_accounts
            SET cookies = min(?, max(0, cookies + ?)), name = ?, updated_at = ?
            WHERE uuid = ?
            """)) {
            statement.setLong(1, CookieClickerService.MAX_COOKIES);
            statement.setLong(2, amount);
            statement.setString(3, player.getName());
            statement.setLong(4, System.currentTimeMillis());
            statement.setString(5, player.getUniqueId().toString());
            statement.executeUpdate();
            return getAccount(player).cookies();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update cookies", exception);
        }
    }

    public synchronized void addCookies(UUID uuid, long amount) {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE cookie_accounts
            SET cookies = min(?, max(0, cookies + ?)), updated_at = ?
            WHERE uuid = ?
            """)) {
            statement.setLong(1, CookieClickerService.MAX_COOKIES);
            statement.setLong(2, amount);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not add automatic cookies", exception);
        }
    }

    public synchronized int addAutoClickerCookies() {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE cookie_accounts
            SET cookies = min(?, cookies + (auto_clicker_level * (1 + oven_level))), updated_at = ?
            WHERE auto_clicker_level > 0
            """)) {
            statement.setLong(1, CookieClickerService.MAX_COOKIES);
            statement.setLong(2, System.currentTimeMillis());
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not add automatic cookies", exception);
        }
    }

    public synchronized long setCookies(OfflinePlayer player, long amount) {
        ensurePlayerRow(player);
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE cookie_accounts
            SET cookies = ?, name = ?, updated_at = ?
            WHERE uuid = ?
            """)) {
            statement.setLong(1, Math.min(CookieClickerService.MAX_COOKIES, Math.max(0L, amount)));
            statement.setString(2, player.getName());
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, player.getUniqueId().toString());
            statement.executeUpdate();
            return getAccount(player).cookies();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not set cookies", exception);
        }
    }

    public synchronized void resetAccount(OfflinePlayer player) {
        ensurePlayerRow(player);
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE cookie_accounts
            SET cookies = 0, click_power_level = 0, auto_clicker_level = 0, oven_level = 0, fortune_level = 0, discount_level = 0, name = ?, updated_at = ?
            WHERE uuid = ?
            """)) {
            statement.setString(1, player.getName());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, player.getUniqueId().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not reset cookie account", exception);
        }
    }

    public synchronized int resetAllAccounts() {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE cookie_accounts
            SET cookies = 0, click_power_level = 0, auto_clicker_level = 0, oven_level = 0, fortune_level = 0, discount_level = 0, updated_at = ?
            """)) {
            statement.setLong(1, System.currentTimeMillis());
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not reset all cookie accounts", exception);
        }
    }

    public synchronized boolean buyUpgrade(OfflinePlayer player, CookieUpgrade upgrade, long cost) {
        ensurePlayerRow(player);
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE cookie_accounts SET cookies = cookies - ?, " + upgrade.column() + " = " + upgrade.column() + " + 1, name = ?, updated_at = ? WHERE uuid = ? AND cookies >= ? AND " + upgrade.column() + " < ?"
        )) {
            statement.setLong(1, cost);
            statement.setString(2, player.getName());
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, player.getUniqueId().toString());
            statement.setLong(5, cost);
            statement.setInt(6, upgrade.maxLevel());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not buy cookie upgrade", exception);
        }
    }

    private void ensurePlayerRow(OfflinePlayer player) {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO cookie_accounts (uuid, name, cookies, click_power_level, auto_clicker_level, oven_level, fortune_level, discount_level, updated_at)
            VALUES (?, ?, 0, 0, 0, 0, 0, 0, ?)
            ON CONFLICT(uuid) DO UPDATE SET name = excluded.name
            """)) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, player.getName());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create cookie account", exception);
        }
    }

    private void addColumnIfMissing(String column, String definition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(cookie_accounts)");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE cookie_accounts ADD COLUMN " + column + " " + definition);
        }
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not close cookie SQLite database: " + exception.getMessage());
        }
    }
}
