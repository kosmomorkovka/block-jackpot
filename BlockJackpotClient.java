package ru.blockjackpot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class BlockJackpotClient implements ClientModInitializer {
    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.blockjackpot.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            KeyBinding.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.player.networkHandler.sendChatCommand("bj");
                    client.player.sendMessage(Text.literal("§6Block Jackpot: меню команд открыто в чате. Следующий этап — полноценное GUI."), false);
                }
            }
        });
    }
}
