package ru.offneko.codeauthor;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {
    private final CodeAuthor plugin;
    private Connection connection;

    public DatabaseManager(CodeAuthor plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "database.db").getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS player_authors (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "author_code TEXT NOT NULL)");

                stmt.execute("CREATE TABLE IF NOT EXISTS author_balances (" +
                        "author_code TEXT PRIMARY KEY, " +
                        "amount INTEGER DEFAULT 0)");

                stmt.execute("CREATE TABLE IF NOT EXISTS pending_notifications (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "author_code TEXT NOT NULL, " +
                        "player_name TEXT NOT NULL, " +
                        "command_name TEXT NOT NULL, " +
                        "amount INTEGER NOT NULL, " +
                        "percent INTEGER NOT NULL)");

                stmt.execute("CREATE TABLE IF NOT EXISTS donation_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "author_code TEXT NOT NULL, " +
                        "player_name TEXT NOT NULL, " +
                        "command_name TEXT NOT NULL, " +
                        "amount INTEGER NOT NULL, " +
                        "percent INTEGER NOT NULL)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setPlayerAuthor(UUID uuid, String code) {
        String sql = "INSERT OR REPLACE INTO player_authors(uuid, author_code) VALUES(?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removePlayerAuthor(UUID uuid) {
        String sql = "DELETE FROM player_authors WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPlayerAuthor(UUID uuid) {
        String sql = "SELECT author_code FROM player_authors WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("author_code");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void addAuthorBalance(String code, int amount) {
        String sql = "INSERT INTO author_balances(author_code, amount) VALUES(?, ?) " +
                "ON CONFLICT(author_code) DO UPDATE SET amount = amount + ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setInt(2, amount);
            ps.setInt(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getAuthorBalance(String code) {
        String sql = "SELECT amount FROM author_balances WHERE author_code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("amount");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getSupportersCount(String code) {
        String sql = "SELECT COUNT(*) FROM player_authors WHERE author_code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void addPendingNotification(String authorCode, String playerName, String command, int amount, int percent) {
        String sql = "INSERT INTO pending_notifications(author_code, player_name, command_name, amount, percent) VALUES(?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, authorCode);
            ps.setString(2, playerName);
            ps.setString(3, command);
            ps.setInt(4, amount);
            ps.setInt(5, percent);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String[]> getPendingNotifications(String authorCode) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT player_name, command_name, amount, percent FROM pending_notifications WHERE author_code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, authorCode);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        rs.getString("player_name"),
                        rs.getString("command_name"),
                        String.valueOf(rs.getInt("amount")),
                        String.valueOf(rs.getInt("percent"))
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void clearPendingNotifications(String authorCode) {
        String sql = "DELETE FROM pending_notifications WHERE author_code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, authorCode);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addDonationHistory(String authorCode, String playerName, String command, int amount, int percent) {
        String sql = "INSERT INTO donation_history(author_code, player_name, command_name, amount, percent) VALUES(?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, authorCode);
            ps.setString(2, playerName);
            ps.setString(3, command);
            ps.setInt(4, amount);
            ps.setInt(5, percent);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String[]> getDonationHistory(String authorCode) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT player_name, command_name, amount, percent FROM donation_history WHERE author_code = ? ORDER BY id DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, authorCode);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        rs.getString("player_name"),
                        rs.getString("command_name"),
                        String.valueOf(rs.getInt("amount")),
                        String.valueOf(rs.getInt("percent"))
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
