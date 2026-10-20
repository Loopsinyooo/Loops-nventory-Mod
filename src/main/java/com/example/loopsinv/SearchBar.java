package com.example.loopsinv;

import com.example.loopsinv.mixin.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Envanter / sandık / shulker ekranlarının üstüne arama çubuğu koyar.
 * Yazdığın şeye uymayan slotlar kararır, uyanlar parlak kalır.
 */
public final class SearchBar {

    private static TextFieldWidget current;
    private static Screen lastScreen;
    private static String lastQuery = "";

    private SearchBar() {}

    /** Arama kutusuna yazı yazarken R gibi tuşlar sıralamayı tetiklemesin. */
    public static boolean isTyping() {
        return current != null && current.isFocused();
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            current = null;
            if (!(screen instanceof HandledScreen<?> hs)) return;
            ScreenHandler handler = hs.getScreenHandler();
            boolean ok = handler instanceof GenericContainerScreenHandler
                    || handler instanceof ShulkerBoxScreenHandler
                    || (handler instanceof PlayerScreenHandler && !(screen instanceof CreativeInventoryScreen));
            if (!ok) return;

            if (screen != lastScreen) {
                lastScreen = screen;
                lastQuery = "";
            }

            HandledScreenAccessor acc = (HandledScreenAccessor) hs;
            TextFieldWidget field = new TextFieldWidget(client.textRenderer, 0, 0, 90, 12,
                    Text.translatable("loopsinv.search"));
            field.setPlaceholder(Text.translatable("loopsinv.search"));
            field.setMaxLength(40);
            field.setText(lastQuery);
            field.setChangedListener(t -> lastQuery = t);
            field.setX(acc.loopsinv_getX() + 176 - 90);
            field.setY(acc.loopsinv_getY() - 14);
            Screens.getButtons(screen).add(field);
            current = field;

            ScreenEvents.afterTick(screen).register(s -> {
                field.setX(acc.loopsinv_getX() + 176 - 90);
                field.setY(acc.loopsinv_getY() - 14);
            });

            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                String q = field.getText().trim().toLowerCase(Locale.ROOT);
                if (q.isEmpty()) return;
                int ox = acc.loopsinv_getX();
                int oy = acc.loopsinv_getY();
                for (Slot slot : handler.slots) {
                    if (!slot.isEnabled()) continue;
                    ItemStack st = slot.getStack();
                    boolean match = !st.isEmpty()
                            && ChestMemory.matches(Registries.ITEM.getId(st.getItem()).toString(), q);
                    if (!match) {
                        int sx = ox + slot.x;
                        int sy = oy + slot.y;
                        ctx.fill(sx, sy, sx + 16, sy + 16, 0xB0000000);
                    }
                }
            });

            ScreenEvents.remove(screen).register(s -> {
                if (current == field) current = null;
            });
        });
    }
}
