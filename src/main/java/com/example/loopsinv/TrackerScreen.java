package com.example.loopsinv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Açtığın sandıklardaki eşyaları arayıp koordinatını gösteren ekran. */
public class TrackerScreen extends Screen {

    private static final int ROW_H = 20;

    private TextFieldWidget field;
    private String query = "";
    private List<ChestMemory.Hit> hits = new ArrayList<>();
    private int scroll = 0;

    public TrackerScreen() {
        super(Text.translatable("screen.loopsinv.tracker"));
    }

    @Override
    protected void init() {
        int panelW = Math.min(width - 40, 360);
        int left = (width - panelW) / 2;
        field = new TextFieldWidget(textRenderer, left, 22, panelW, 16, Text.translatable("loopsinv.search"));
        field.setPlaceholder(Text.translatable("loopsinv.search"));
        field.setMaxLength(40);
        field.setText(query);
        field.setChangedListener(t -> {
            query = t;
            refresh();
            scroll = 0;
        });
        addDrawableChild(field);
        setInitialFocus(field);
        refresh();
    }

    private void refresh() {
        hits = ChestMemory.search(MinecraftClient.getInstance(), query);
    }

    private int visibleRows() {
        return Math.max(1, (height - 66) / ROW_H);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, hits.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public void removed() {
        if (query.trim().isEmpty()) Highlight.clear();
        else Highlight.set(hits);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        int panelW = Math.min(width - 40, 360);
        int left = (width - panelW) / 2;
        ctx.drawTextWithShadow(textRenderer, title, left, 8, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("loopsinv.tracker.hint"), left, height - 14, 0xFF888888);

        if (hits.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("loopsinv.tracker.empty"), left, 52, 0xFFAAAAAA);
            return;
        }

        int rows = visibleRows();
        int y = 46;
        for (int i = scroll; i < Math.min(hits.size(), scroll + rows); i++) {
            ChestMemory.Hit h = hits.get(i);
            Item item = Registries.ITEM.get(Identifier.of(h.itemId));
            ctx.drawItem(new ItemStack(item), left, y);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(item.getName().getString() + " x" + h.count), left + 22, y + 4, 0xFFFFFFFF);
            String where = Math.round(h.dist) + "m";
            ctx.drawTextWithShadow(textRenderer, Text.literal(where),
                    left + panelW - textRenderer.getWidth(where), y + 4, 0xFFFFD27F);
            y += ROW_H;
        }
    }
}
