package io.github.schachsebastian.itemimageexport;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

public class ChatUtil {
    public static void sendFolderLink(String path, String displayName) {
        ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_FILE, path);

        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open: " + path));

        Style style = Style.EMPTY
                .withClickEvent(click)
                .withHoverEvent(hover)
                .withUnderlined(true);

        Component message = Component.literal("Folder: ")
                                     .append(Component.literal(displayName).withStyle(style));

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(message);
        }
    }
}
