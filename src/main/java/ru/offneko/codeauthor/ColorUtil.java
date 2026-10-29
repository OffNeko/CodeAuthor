package ru.offneko.codeauthor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ColorUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static Component translate(String text) {
        if (text == null) return Component.empty();

        if (text.contains("<") && text.contains(">")) {
            try {
                return MINI_MESSAGE.deserialize(text);
            } catch (Exception e) {
            }
        }
        return LEGACY_SERIALIZER.deserialize(text);
    }
}
