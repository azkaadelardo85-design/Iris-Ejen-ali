package dev.irisejen.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Semua keybind hanya mengirim perintah /iris ke server, jadi tidak perlu networking khusus.
 * Jika versi 26.3 mengganti nama API keybind (mis. KeyBindingHelper / Category), cukup perbaiki file ini.
 */
public class IrisEjenClient implements ClientModInitializer {
    private static KeyMapping scan, override, use, next, prev, evade;

    private static KeyMapping reg(String name, int key, KeyMapping.Category cat) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, key, cat));
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("irisejen", "iris"));
        scan = reg("key.irisejen.scan", GLFW.GLFW_KEY_R, cat);
        override = reg("key.irisejen.override", GLFW.GLFW_KEY_Z, cat);
        use = reg("key.irisejen.use", GLFW.GLFW_KEY_X, cat);
        next = reg("key.irisejen.next", GLFW.GLFW_KEY_C, cat);
        prev = reg("key.irisejen.prev", GLFW.GLFW_KEY_V, cat);
        evade = reg("key.irisejen.evade", GLFW.GLFW_KEY_B, cat);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            while (scan.consumeClick()) send(mc, "iris scan");
            while (override.consumeClick()) send(mc, "iris override");
            while (use.consumeClick()) send(mc, "iris use");
            while (next.consumeClick()) send(mc, "iris next");
            while (prev.consumeClick()) send(mc, "iris prev");
            while (evade.consumeClick()) send(mc, "iris evade");
        });
    }

    private static void send(Minecraft mc, String cmd) {
        mc.player.connection.sendCommand(cmd);
    }
}
