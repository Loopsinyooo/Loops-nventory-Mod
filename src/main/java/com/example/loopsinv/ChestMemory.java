package com.example.loopsinv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Açtığın sandık / varil / shulker kutularının içeriğini hatırlar (dünya başına ayrı dosya).
 * Sadece senin gerçekten açıp gördüğün içerik kaydedilir.
 */
public final class ChestMemory {

    public static final class Chest {
        public int x, y, z;
        public String dim;
        public Map<String, Integer> items = new LinkedHashMap<>();
    }

    public static final class Hit {
        public final Chest chest;
        public final String itemId;
        public final int count;
        public final double dist;

        Hit(Chest chest, String itemId, int count, double dist) {
            this.chest = chest;
            this.itemId = itemId;
            this.count = count;
            this.dist = dist;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Chest>>() {}.getType();

    private static Map<String, Chest> chests = new LinkedHashMap<>();
    private static String loadedWorld = null;

    private static BlockPos lastPos;
    private static long lastTime;

    private static ScreenHandler curHandler;
    private static BlockPos curPos;
    private static String curDim;

    private ChestMemory() {}

    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient()) {
                lastPos = hit.getBlockPos();
                lastTime = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) lastPos = null;
            return ActionResult.PASS;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen<?> hs)) return;
            ScreenHandler handler = hs.getScreenHandler();
            if (!(handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler)) return;

            if (handler != curHandler) {
                curHandler = handler;
                curPos = null;
                if (lastPos != null && System.currentTimeMillis() - lastTime < 5000 && client.world != null) {
                    if (!client.world.getBlockState(lastPos).isOf(Blocks.ENDER_CHEST)) {
                        curPos = lastPos;
                        curDim = client.world.getRegistryKey().getValue().toString();
                    }
                }
                lastPos = null;
            }
            if (curPos == null) return;

            BlockPos pos = curPos;
            String dim = curDim;
            ScreenEvents.remove(screen).register(s -> record(client, handler, pos, dim));
        });
    }

    private static void record(MinecraftClient client, ScreenHandler handler, BlockPos pos, String dim) {
        ensureLoaded(client);
        Inventory inv = handler.getSlot(0).inventory;
        Map<String, Integer> items = new TreeMap<>();
        for (Slot s : handler.slots) {
            if (s.inventory != inv || !s.hasStack()) continue;
            items.merge(Registries.ITEM.getId(s.getStack().getItem()).toString(), s.getStack().getCount(), Integer::sum);
        }
        String key = dim + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
        Highlight.remove(dim, pos.getX(), pos.getY(), pos.getZ());
        if (items.isEmpty()) {
            chests.remove(key);
        } else {
            Chest c = new Chest();
            c.x = pos.getX();
            c.y = pos.getY();
            c.z = pos.getZ();
            c.dim = dim;
            c.items = items;
            chests.put(key, c);
        }
        save();
    }

    // ---- Arama ----

    public static boolean matches(String itemId, String query) {
        if (query.isEmpty()) return true;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return false;
        if (id.getPath().contains(query.replace(' ', '_'))) return true;
        Item item = Registries.ITEM.get(id);
        return item.getName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    public static List<Hit> search(MinecraftClient client, String rawQuery) {
        ensureLoaded(client);
        List<Hit> out = new ArrayList<>();
        if (client.player == null || client.world == null) return out;
        String q = rawQuery.trim().toLowerCase(Locale.ROOT);
        String dim = client.world.getRegistryKey().getValue().toString();
        for (Chest c : chests.values()) {
            if (!dim.equals(c.dim)) continue;
            double d = Math.sqrt(client.player.squaredDistanceTo(c.x + 0.5, c.y + 0.5, c.z + 0.5));
            for (Map.Entry<String, Integer> e : c.items.entrySet()) {
                if (matches(e.getKey(), q)) out.add(new Hit(c, e.getKey(), e.getValue(), d));
            }
        }
        out.sort(Comparator.<Hit>comparingDouble(h -> h.dist).thenComparing(h -> h.itemId));
        return out;
    }

    // ---- Dosya ----

    private static String worldKey(MinecraftClient client) {
        String key;
        if (client.isInSingleplayer() && client.getServer() != null) {
            key = "sp_" + client.getServer().getSaveProperties().getLevelName();
        } else if (client.getCurrentServerEntry() != null) {
            key = "mp_" + client.getCurrentServerEntry().address;
        } else {
            key = "unknown";
        }
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Path file(String worldKey) {
        return FabricLoader.getInstance().getConfigDir().resolve("loopsinv").resolve(worldKey + ".json");
    }

    private static void ensureLoaded(MinecraftClient client) {
        String key = worldKey(client);
        if (key.equals(loadedWorld)) return;
        loadedWorld = key;
        Highlight.clear();
        chests = new LinkedHashMap<>();
        Path p = file(key);
        if (!Files.exists(p)) return;
        try {
            Map<String, Chest> loaded = GSON.fromJson(Files.readString(p), MAP_TYPE);
            if (loaded != null) chests = loaded;
        } catch (Exception ignored) {
            chests = new LinkedHashMap<>();
        }
    }

    private static void save() {
        if (loadedWorld == null) return;
        Path p = file(loadedWorld);
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(chests, MAP_TYPE));
        } catch (IOException ignored) {
        }
    }
}
