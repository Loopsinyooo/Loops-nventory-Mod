package com.example.loopsinv;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Loops inv: R tuşu.
 *  - Oyundayken: ana envanterini (27 slot) birleştirip düzenler.
 *  - Sandık/varil/shulker açıkken: fare hangi tarafın üzerindeyse (sandık ya da envanterin) onu düzenler.
 */
public class LoopsInvClient implements ClientModInitializer {

    private static KeyBinding sortKey;
    private static KeyBinding trackerKey;

    @Override
    public void onInitializeClient() {
        DragMove.init();
        ChestMemory.init();
        SearchBar.init();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of("loopsinv", "highlight"),
                (ctx, tickCounter) -> Highlight.render(ctx));

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("loopsinv", "main"));
        sortKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.loopsinv.sort",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category
        ));
        trackerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.loopsinv.tracker",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F5,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Sorter.tick(client);
            while (sortKey.wasPressed()) {
                sortWorldInventory(client);
            }
            while (trackerKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new TrackerScreen());
            }
        });

        // Sandık ekranlarında tuş olayı gelmediği için tuşu tick'te kontrol ederiz
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof HandledScreen<?> hs) {
                ScreenHandler handler = hs.getScreenHandler();
                if (handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler) {
                    boolean[] wasDown = {false};
                    ScreenEvents.afterTick(screen).register(s -> containerTick(client, hs, wasDown));
                }
            }
        });
    }

    private static void sortWorldInventory(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null) return;
        ScreenHandler handler = player.playerScreenHandler;
        if (player.currentScreenHandler != handler) return;
        Sorter.start(client, handler, slotsOf(handler, player.getInventory(), true));
    }

    private static void containerTick(MinecraftClient client, HandledScreen<?> hs, boolean[] wasDown) {
        PlayerEntity player = client.player;
        if (player == null) return;

        int code = InputUtil.fromTranslationKey(sortKey.getBoundKeyTranslationKey()).getCode();
        if (code < 0) return;
        boolean down = GLFW.glfwGetKey(client.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
        boolean pressed = down && !wasDown[0];
        wasDown[0] = down;
        if (!pressed || SearchBar.isTyping()) return;

        ScreenHandler handler = hs.getScreenHandler();
        double scale = client.getWindow().getScaleFactor();
        Slot hovered = DragMove.slotAt(hs, handler, client.mouse.getX() / scale, client.mouse.getY() / scale);

        Inventory containerInv = handler.getSlot(0).inventory;
        Inventory target = hovered != null ? hovered.inventory : containerInv;

        if (target == player.getInventory()) {
            Sorter.start(client, handler, slotsOf(handler, target, true));
        } else {
            Sorter.start(client, handler, slotsOf(handler, target, false));
        }
    }

    /** Verilen envantere ait slot numaraları. Oyuncu envanterinde sadece ana 27 slot (hotbar hariç). */
    private static List<Integer> slotsOf(ScreenHandler handler, Inventory inv, boolean playerInv) {
        List<Integer> out = new ArrayList<>();
        for (Slot s : handler.slots) {
            if (s.inventory != inv) continue;
            if (playerInv) {
                int idx = s.getIndex();
                if (idx < 9 || idx > 35) continue;
            }
            out.add(s.id);
        }
        return out;
    }
}
