package ru.offneko.codeauthor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AdminCommand implements CommandExecutor {
    private final CodeAuthor plugin;

    public AdminCommand(CodeAuthor plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("codeauthor.admin")) {
            sender.sendMessage(ColorUtil.translate(plugin.getConfig().getString("messages.no-permission", "&cУ вас нет прав.")));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.reloadNotifConfig();

            String msg = plugin.getConfig().getString("messages.reload-success", "&aПлагин успешно перезагружен!");
            sender.sendMessage(ColorUtil.translate(msg));
            return true;
        }

        sender.sendMessage(ColorUtil.translate("&cИспользование: /codeauthor reload"));
        return true;
    }
}