package ru.offneko.codeauthor;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CodeAuthor extends JavaPlugin implements Listener {

    private DatabaseManager db;
    private FileConfiguration notifConfig;
    private Economy econ = null;
    private PlayerPointsAPI pointsAPI = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("notifications.yml", false);
        notifConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "notifications.yml"));

        db = new DatabaseManager(this);
        getServer().getPluginManager().registerEvents(this, this);

        // Регистрируем codeauthor
        if (getCommand("codeauthor") != null) {
            getCommand("codeauthor").setExecutor(new AdminCommand(this));
        } else {
            getLogger().severe("Команда 'codeauthor' не найдена в plugin.yml!");
        }

        // Ручная регистрация author (чтобы сервер не вставлял свой usage)
        try {
            java.lang.reflect.Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            org.bukkit.command.CommandMap commandMap = (org.bukkit.command.CommandMap) commandMapField.get(Bukkit.getServer());

            org.bukkit.command.Command authorCommand = new org.bukkit.command.Command("author") {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Только для игроков.");
                        return true;
                    }
                    return new AuthorCommand(CodeAuthor.this).onCommand(sender, this, commandLabel, args);
                }
            };
            commandMap.register("CodeAuthor", authorCommand);
            getLogger().info("Команда /author зарегистрирована вручную!");
        } catch (Exception e) {
            getLogger().severe("Не удалось зарегистрировать команду /author: " + e.getMessage());
        }

        if (!setupEconomy()) {
            getLogger().warning("Vault не найден! Выдача денег будет недоступна.");
        }

        if (getServer().getPluginManager().getPlugin("PlayerPoints") != null) {
            pointsAPI = PlayerPoints.getInstance().getAPI();
            getLogger().info("PlayerPoints успешно подключен!");
        }

        sendStartupMessage();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public void reloadNotifConfig() {
        notifConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "notifications.yml"));
    }

    private void sendStartupMessage() {
        if (!getConfig().getBoolean("console-startup.enabled", true)) {
            getLogger().info("CodeAuthor включён!");
            return;
        }

        List<String> messages = getConfig().getStringList("console-startup.message");
        if (messages.isEmpty()) {
            getLogger().info("CodeAuthor включён!");
            return;
        }

        for (String msg : messages) {
            String formatted = msg
                    .replace("%version%", getDescription().getVersion())
                    .replace("%author%", String.join(", ", getDescription().getAuthors()))
                    .replace('&', '§');

            Bukkit.getConsoleSender().sendMessage(formatted);
        }
    }

    @Override
    public void onDisable() {
        if (db != null) db.close();
    }

    public boolean activateAuthor(Player player, String code) {
        ConfigurationSection authors = getConfig().getConfigurationSection("authors");
        if (authors == null || !authors.contains(code)) return false;

        db.setPlayerAuthor(player.getUniqueId(), code);
        String authorName = authors.getString(code + ".name");

        String msg = getConfig().getString("messages.author-activated", "&aВы активировали автора: &e%author%")
                .replace("%author%", authorName);
        player.sendMessage(ColorUtil.translate(msg));
        return true;
    }

    public void removeAuthor(Player player) {
        String currentCode = db.getPlayerAuthor(player.getUniqueId());
        if (currentCode == null) {
            player.sendMessage(ColorUtil.translate(getConfig().getString("messages.not-supporting", "&cВы не поддерживаете ни одного автора.")));
            return;
        }

        db.removePlayerAuthor(player.getUniqueId());
        String authorName = getAuthorNameByCode(currentCode);

        String msg = getConfig().getString("messages.author-removed", "&cВы больше не поддерживаете автора: &e%author%")
                .replace("%author%", authorName != null ? authorName : "Неизвестно");
        player.sendMessage(ColorUtil.translate(msg));
    }

    public String getAuthorNameByCode(String code) {
        ConfigurationSection authors = getConfig().getConfigurationSection("authors");
        if (authors == null) return null;
        return authors.getString(code + ".name");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        processCommand(event.getMessage().substring(1));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        processCommand(event.getCommand());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRconCommand(RemoteServerCommandEvent event) {
        processCommand(event.getCommand());
    }

    private void processCommand(String rawCommand) {
        String[] parts = rawCommand.split(" ");
        if (parts.length < 3) return;

        List<Map<?, ?>> payouts = getConfig().getMapList("payouts");

        String matchedCommand = null;
        int percent = 0;
        String displayName = "";
        String matchedType = "";
        int commandLength = 0;

        for (Map<?, ?> payout : payouts) {
            String configCmd = (String) payout.get("command");
            if (configCmd != null && rawCommand.toLowerCase().startsWith(configCmd.toLowerCase())) {
                matchedCommand = configCmd;
                Number p = (Number) payout.get("percent");
                percent = p != null ? p.intValue() : 0;
                displayName = (String) payout.get("display");
                matchedType = (String) payout.get("type");
                commandLength = configCmd.split(" ").length;
                break;
            }
        }

        if (matchedCommand == null || percent <= 0) {
            return;
        }

        int playerIndex = commandLength;

        if (parts.length <= playerIndex) return;

        Player target = Bukkit.getPlayerExact(parts[playerIndex]);
        if (target == null) return;

        String authorCode = db.getPlayerAuthor(target.getUniqueId());
        if (authorCode == null) return;

        int amount = parseAmount(parts[parts.length - 1]);
        if (amount <= 0) return;

        int bonus = (int) Math.ceil(amount * (percent / 100.0));
        if (bonus <= 0) return;

        String authorName = getAuthorNameByCode(authorCode);
        Player authorPlayer = Bukkit.getPlayerExact(authorName);

        if (authorPlayer != null && authorPlayer.isOnline()) {
            if (matchedType != null) {
                switch (matchedType.toLowerCase()) {
                    case "vault":
                        if (econ != null) econ.depositPlayer(authorPlayer, bonus);
                        break;
                    case "playerpoints":
                        if (pointsAPI != null) pointsAPI.give(authorPlayer.getUniqueId(), bonus);
                        break;
                }
            }
        }

        db.addAuthorBalance(authorCode, bonus);
        notifyAuthor(authorCode, target.getName(), bonus, percent, matchedCommand, displayName);
    }

    private void notifyAuthor(String authorCode, String playerName, int amount, int percent, String command, String displayName) {
        db.addDonationHistory(authorCode, playerName, command, amount, percent);

        String authorName = getAuthorNameByCode(authorCode);
        Player authorPlayer = Bukkit.getPlayerExact(authorName);

        if (authorPlayer != null && authorPlayer.isOnline()) {
            String prettyCommand = (displayName != null && !displayName.isEmpty()) ? displayName : command;

            String msg = notifConfig.getString("notify.message", "&a+{percent}% ({amount}) &eот игрока &6{player} &e(покупка: {command})")
                    .replace("{percent}", String.valueOf(percent))
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{player}", playerName)
                    .replace("{command}", prettyCommand);

            authorPlayer.sendMessage(ColorUtil.translate(msg));

            if (notifConfig.getBoolean("notify.sound.enabled", true)) {
                try {
                    String soundName = notifConfig.getString("notify.sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP");
                    float volume = (float) notifConfig.getDouble("notify.sound.volume", 1.0);
                    float pitch = (float) notifConfig.getDouble("notify.sound.pitch", 1.2);
                    authorPlayer.playSound(authorPlayer.getLocation(), Sound.valueOf(soundName), volume, pitch);
                } catch (Exception ignored) {}
            }
        } else {
            db.addPendingNotification(authorCode, playerName, command, amount, percent);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ConfigurationSection authors = getConfig().getConfigurationSection("authors");
        if (authors == null) return;

        for (String code : authors.getKeys(false)) {
            String authorName = authors.getString(code + ".name");
            if (authorName != null && authorName.equalsIgnoreCase(player.getName())) {
                List<String[]> pending = db.getPendingNotifications(code);
                if (pending.isEmpty()) return;

                Map<String, Integer> grouped = new LinkedHashMap<>();
                for (String[] data : pending) {
                    String cmd = data[1];
                    int amt = Integer.parseInt(data[2]);
                    grouped.put(cmd, grouped.getOrDefault(cmd, 0) + amt);
                }

                StringBuilder rewards = new StringBuilder();
                boolean first = true;
                for (Map.Entry<String, Integer> entry : grouped.entrySet()) {
                    String cmd = entry.getKey();
                    int totalAmt = entry.getValue();

                    String prettyCmd = cmd;
                    for (Map<?, ?> payout : getConfig().getMapList("payouts")) {
                        if (cmd.equalsIgnoreCase((String) payout.get("command"))) {
                            prettyCmd = (String) payout.get("display");
                            break;
                        }
                    }

                    int percent = 0;
                    for (String[] data : pending) {
                        if (data[1].equals(cmd)) {
                            percent = Integer.parseInt(data[3]);
                            break;
                        }
                    }

                    if (!first) {
                        rewards.append(", ");
                    }
                    rewards.append("&e").append(percent).append("% (").append(totalAmt).append(" шт.) &6").append(prettyCmd);
                    first = false;
                }

                if (notifConfig.getBoolean("notify-afk.enabled", true)) {
                    List<String> messages = notifConfig.getStringList("notify-afk.messages");
                    for (String msg : messages) {
                        String formatted = msg
                                .replace("%player_name%", player.getName())
                                .replace("%code%", code)
                                .replace("%rewards%", rewards.toString());
                        player.sendMessage(ColorUtil.translate(formatted));
                    }
                }

                db.clearPendingNotifications(code);
                break;
            }
        }
    }

    private int parseAmount(String str) {
        try {
            return Integer.parseInt(str.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public DatabaseManager getDb() { return db; }
    public FileConfiguration getNotifConfig() { return notifConfig; }
}