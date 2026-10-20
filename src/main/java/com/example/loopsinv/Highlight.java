package com.example.loopsinv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Takipçi ekranında aradığın eşyanın bulunduğu sandıkları ekranda parlatır.
 * Duvarın arkasındaysa da görünür (ekrana izdüşüm), ekran dışındaysa kenarda ok gibi durur.
 */
public final class Highlight {

    private static final long GLOW_MS = 30_000;

    private record Target(String dim, int x, int y, int z, String itemId, long expires) {}

    private static final List<Target> targets = new ArrayList<>();

    private Highlight() {}

    public static void clear() {
        targets.clear();
    }

    public static void set(List<ChestMemory.Hit> hits) {
        targets.clear();
        Set<String> seen = new HashSet<>();
        for (ChestMemory.Hit h : hits) {
            ChestMemory.Chest c = h.chest;
            String key = c.dim + "|" + c.x + "|" + c.y + "|" + c.z;
            if (seen.add(key)) targets.add(new Target(c.dim, c.x, c.y, c.z, h.itemId, System.currentTimeMillis() + GLOW_MS));
        }
    }

    /** Sandık açılınca (içi görülünce) parlaması söner. */
    public static void remove(String dim, int x, int y, int z) {
        targets.removeIf(t -> t.dim.equals(dim) && t.x == x && t.y == y && t.z == z);
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        targets.removeIf(t -> now > t.expires);
        if (targets.isEmpty() || mc.player == null || mc.world == null || mc.options.hudHidden) return;

        String dim = mc.world.getRegistryKey().getValue().toString();
        Vec3d eye = mc.player.getEyePos();
        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());

        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) w / h;

        double pulse = 0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 250.0);

        for (Target t : targets) {
            if (!dim.equals(t.dim)) continue;

            double dx = t.x + 0.5 - eye.x;
            double dy = t.y + 0.5 - eye.y;
            double dz = t.z + 0.5 - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            double xc = dx * rx + dz * rz;
            double yc = dx * ux + dy * uy + dz * uz;
            double zc = dx * fx + dy * fy + dz * fz;

            double sx, sy;
            int size;
            boolean onScreen = false;
            if (zc > 0.05) {
                double nx = xc / (zc * tanHalf * aspect);
                double ny = yc / (zc * tanHalf);
                sx = (nx + 1) / 2 * w;
                sy = (1 - ny) / 2 * h;
                onScreen = Math.abs(nx) <= 1 && Math.abs(ny) <= 1;
                size = (int) Math.max(12, Math.min(90, (1.0 / zc) / tanHalf * (h / 2.0)));
            } else {
                sx = w / 2.0;
                sy = h / 2.0;
                size = 12;
            }

            if (!onScreen) {
                // Ekran dışında: yönü kenara sıkıştır
                double dirx = zc > 0.05 ? sx - w / 2.0 : xc;
                double diry = zc > 0.05 ? sy - h / 2.0 : -yc;
                if (Math.abs(dirx) < 1e-6 && Math.abs(diry) < 1e-6) dirx = 1;
                double margin = 16;
                double tx = Math.abs(dirx) < 1e-6 ? Double.MAX_VALUE : (w / 2.0 - margin) / Math.abs(dirx);
                double ty = Math.abs(diry) < 1e-6 ? Double.MAX_VALUE : (h / 2.0 - margin) / Math.abs(diry);
                double k = Math.min(tx, ty);
                sx = w / 2.0 + dirx * k;
                sy = h / 2.0 + diry * k;
                size = 12;
            }

            int cx = (int) Math.round(sx);
            int cy = (int) Math.round(sy);
            int half = size / 2;
            int x1 = cx - half, y1 = cy - half, x2 = cx + half, y2 = cy + half;

            // Parlama: dıştan içe artan opaklıkta halkalar + hafif dolgu
            ctx.fill(x1, y1, x2, y2, argb((int) (45 * pulse)));
            for (int i = 5; i >= 0; i--) {
                int pad = i * 2;
                int a = (int) ((i == 0 ? 255 : 70 - i * 10) * pulse);
                border(ctx, x1 - pad, y1 - pad, x2 + pad, y2 + pad, 2, argb(Math.max(a, 8)));
            }

            // Eşya ikonu + mesafe
            Item item = Registries.ITEM.get(Identifier.of(t.itemId));
            int iconY = Math.max(2, y1 - 24);
            ctx.drawItem(new ItemStack(item), cx - 8, iconY);
            String txt = Math.round(dist) + "m";
            int tw = mc.textRenderer.getWidth(txt);
            int textY = Math.min(h - 10, y2 + 8);
            ctx.drawTextWithShadow(mc.textRenderer, txt, cx - tw / 2, textY, 0xFFFFE08A);
        }
    }

    private static int argb(int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFD250;
    }

    private static void border(DrawContext ctx, int x1, int y1, int x2, int y2, int t, int color) {
        ctx.fill(x1, y1, x2, y1 + t, color);
        ctx.fill(x1, y2 - t, x2, y2, color);
        ctx.fill(x1, y1 + t, x1 + t, y2 - t, color);
        ctx.fill(x2 - t, y1 + t, x2, y2 - t, color);
    }
}
