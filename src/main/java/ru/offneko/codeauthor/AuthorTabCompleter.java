package ru.offneko.codeauthor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AuthorTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("see", "donate", "stop");
    private static final List<String> EMPTY_LIST = Collections.emptyList();

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("see")) {
            return EMPTY_LIST;
        }

        return EMPTY_LIST;
    }
}