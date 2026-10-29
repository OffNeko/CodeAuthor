package ru.offneko.codeauthor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public class AuthorCommand implements CommandExecutor {

    private final CodeAuthor plugin;

    public AuthorCommand(CodeAuthor plugin) {
        this.plugin = plugin;
    }

    private String getMsg(String path) {
        return plugin.getConfig().getString("messages." + path, "&cMessage not found: " + path);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("stop")) {
            plugin.removeAuthor(player);
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("see")) {
            if (args.length < 2) {
                player.sendMessage(ColorUtil.translate(getMsg("usage-see")));
                return true;
            }
            String code = args[1];
            int count = plugin.getDb().getSupportersCount(code);
            String authorName = plugin.getAuthorNameByCode(code);

            if (authorName == null) {
                player.sendMessage(ColorUtil.translate(getMsg("code-not-found").replace("%code%", code)));
                return true;
            }

            String msg = plugin.getNotifConfig().getString("see-command.format", "&eКод &6{code} &eподдерживают: &a{count}")
                    .replace("{code}", code)
                    .replace("{author}", authorName)
                    .replace("{count}", String.valueOf(count));
            player.sendMessage(ColorUtil.translate(msg));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("donate")) {
            String perm = plugin.getConfig().getString("donate-command.permission", "codeauthor.donate");
            if (!player.hasPermission(perm)) {
                player.sendMessage(ColorUtil.translate(getMsg("no-permission")));
                return true;
            }

            String authorCode = null;
            ConfigurationSection authors = plugin.getConfig().getConfigurationSection("authors");
            if (authors != null) {
                for (String code : authors.getKeys(false)) {
                    String authorName = authors.getString(code + ".name");
                    if (authorName != null && authorName.equalsIgnoreCase(player.getName())) {
                        authorCode = code;
                        break;
                    }
                }
            }

            if (authorCode == null) {
                player.sendMessage(ColorUtil.translate(getMsg("not-registered-author")));
                return true;
            }

            List<String[]> history = plugin.getDb().getDonationHistory(authorCode);
            if (history.isEmpty()) {
                player.sendMessage(ColorUtil.translate(plugin.getConfig().getString("donate-command.empty", "&cПока никто не донатил с вашим кодом.")));
                return true;
            }

            String header = plugin.getConfig().getString("donate-command.header", "&e=== История донатов по вашему коду &6%code% &e===");
            player.sendMessage(ColorUtil.translate(header.replace("%code%", authorCode)));

            String format = plugin.getConfig().getString("donate-command.format", "&7- &6%player% &eкупил &b%command% &e(%amount% шт.) [&a%percent%%&e]");
            for (String[] data : history) {
                String cmdDisplay = data[1];
                for (java.util.Map<?, ?> payout : plugin.getConfig().getMapList("payouts")) {
                    if (data[1].equalsIgnoreCase((String) payout.get("command"))) {
                        cmdDisplay = (String) payout.get("display");
                        break;
                    }
                }
                String msg = format
                        .replace("%player%", data[0])
                        .replace("%command%", cmdDisplay)
                        .replace("%amount%", data[2])
                        .replace("%percent%", data[3]);
                player.sendMessage(ColorUtil.translate(msg));
            }
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ColorUtil.translate(getMsg("usage-author")));
            return true;
        }

        boolean success = plugin.activateAuthor(player, args[0]);
        if (!success) {
            player.sendMessage(ColorUtil.translate(getMsg("invalid-code")));
        }
        return true;
    }
}