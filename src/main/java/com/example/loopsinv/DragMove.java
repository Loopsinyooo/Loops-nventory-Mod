package com.example.loopsinv;

import com.example.loopsinv.mixin.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Shift + sol tık basılı tutup sürükleyince, üzerinden geçtiğin slotlardaki eşyalar
 * tek tek Shift+tık yapılmış gibi taşınır. Her slot için tek bir normal tıklama gönderilir,
 * yani elle yapabileceğin işlemden farkı yok.
 *
 * Not: Sağ tıkla sürükleyip eşit dağıtma (ve sol tıkla bölüştürme) oyunun kendisinde zaten var.
 */
public final class DragMove {

    private static final class State {
        boolean active = false;
        double lastX, lastY;
        final Set<Integer> done = new HashSet<>();
    }

    private DragMove() {}

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof HandledScreen<?> hs && !(screen instanceof CreativeInventoryScreen)) {
                State st = new State();
                ScreenEvents.afterTick(screen).register(s -> tick(client, hs, st));
            }
        });
    }

    private static void tick(MinecraftClient client, HandledScreen<?> hs, State st) {
        if (client.player == null || client.interactionManager == null) return;

        long win = client.getWindow().getHandle();
        boolean lmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        ScreenHandler handler = hs.getScreenHandler();
        if (!lmb || !shift || !handler.getCursorStack().isEmpty()) {
            st.active = false;
            st.done.clear();
            return;
        }

        double scale = client.getWindow().getScaleFactor();
        double mx = client.mouse.getX() / scale;
        double my = client.mouse.getY() / scale;

        if (!st.active) {
            // Tıklamanın başladığı slotu oyun kendisi zaten taşır, tekrar dokunma
            st.active = true;
            st.lastX = mx;
            st.lastY = my;
            st.done.clear();
            Slot first = slotAt(hs, handler, mx, my);
            if (first != null) st.done.add(first.id);
            return;
        }

        // Son konumdan şimdiki konuma giden çizgi üzerindeki slotları sırayla işle
        double dx = mx - st.lastX;
        double dy = my - st.lastY;
        int steps = Math.max(1, (int) (Math.hypot(dx, dy) / 4.0));
        Set<Integer> visited = new HashSet<>();
        for (int i = 1; i <= steps; i++) {
            double px = st.lastX + dx * i / steps;
            double py = st.lastY + dy * i / steps;
            Slot slot = slotAt(hs, handler, px, py);
            if (slot == null) continue;
            visited.add(slot.id);
            if (st.done.contains(slot.id) || !slot.hasStack()) continue;
            st.done.add(slot.id);
            client.interactionManager.clickSlot(handler.syncId, slot.id, 0,
                    SlotActionType.QUICK_MOVE, client.player);
        }
        st.lastX = mx;
        st.lastY = my;

        // Mouse Tweaks gibi: slot boşaldıysa (yeni eşya gelirse tekrar taşınsın) ya da
        // imleç slotun üzerinden ayrıldıysa slot tekrar işlenebilir hale gelir.
        st.done.removeIf(id -> !visited.contains(id) || !handler.getSlot(id).hasStack());
    }

    static Slot slotAt(HandledScreen<?> hs, ScreenHandler handler, double mx, double my) {
        HandledScreenAccessor acc = (HandledScreenAccessor) hs;
        int ox = acc.loopsinv_getX();
        int oy = acc.loopsinv_getY();
        for (Slot slot : handler.slots) {
            if (!slot.isEnabled()) continue;
            int sx = ox + slot.x;
            int sy = oy + slot.y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) return slot;
        }
        return null;
    }
}
