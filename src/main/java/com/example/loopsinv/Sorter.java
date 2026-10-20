package com.example.loopsinv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Önce aynı eşyaları birleştirir, sonra gruplara göre dizer.
 * Tüm işlem normal tıklamalara çevrilir ve tick başına birkaç tıklama gönderilir
 * (elle yapılmış gibi, sunucuyu tek seferde tıklamayla boğmaz).
 */
public final class Sorter {

    private static final int CLICKS_PER_TICK = 4;

    private static final ArrayDeque<Integer> queue = new ArrayDeque<>();
    private static ScreenHandler activeHandler;

    private Sorter() {}

    public static boolean busy() {
        return !queue.isEmpty();
    }

    /** slotIds: ScreenHandler içindeki slot numaraları (düzenlenecek bölge). */
    public static void start(MinecraftClient client, ScreenHandler handler, List<Integer> slotIds) {
        if (busy() || client.player == null || client.interactionManager == null) return;
        if (slotIds.isEmpty() || !handler.getCursorStack().isEmpty()) return;

        List<Integer> clicks = buildPlan(handler, slotIds);
        if (clicks.isEmpty()) return;
        activeHandler = handler;
        queue.addAll(clicks);
    }

    public static void tick(MinecraftClient client) {
        if (queue.isEmpty()) return;
        if (client.player == null || client.interactionManager == null
                || client.player.currentScreenHandler != activeHandler) {
            queue.clear();
            activeHandler = null;
            return;
        }
        for (int n = 0; n < CLICKS_PER_TICK && !queue.isEmpty(); n++) {
            int slot = queue.poll();
            client.interactionManager.clickSlot(activeHandler.syncId, slot, 0,
                    SlotActionType.PICKUP, client.player);
        }
        if (queue.isEmpty()) {
            activeHandler = null;
        }
    }

    // ---- Plan: hangi slotlara hangi sırayla tıklanacak ----

    private static List<Integer> buildPlan(ScreenHandler handler, List<Integer> ids) {
        int n = ids.size();
        List<ItemStack> m = new ArrayList<>(n);
        for (int id : ids) m.add(handler.getSlot(id).getStack().copy());
        List<Integer> clicks = new ArrayList<>();

        // 1) Birleştirme: yarım stackleri aynı türdekilerle doldur
        for (int i = 0; i < n; i++) {
            ItemStack a = m.get(i);
            if (a.isEmpty() || a.getCount() >= a.getMaxCount()) continue;
            for (int j = i + 1; j < n; j++) {
                ItemStack b = m.get(j);
                if (b.isEmpty() || !ItemStack.areItemsAndComponentsEqual(a, b)) continue;
                int moved = Math.min(a.getMaxCount() - a.getCount(), b.getCount());
                if (moved <= 0) break;
                clicks.add(ids.get(j));            // j'yi eline al
                clicks.add(ids.get(i));            // i'ye bırak (birleşir)
                if (moved < b.getCount()) {
                    clicks.add(ids.get(j));        // artanı j'ye geri bırak
                }
                a.increment(moved);
                b.decrement(moved);
                if (a.getCount() >= a.getMaxCount()) break;
            }
        }

        // 2) Sıralama
        List<Key> target = new ArrayList<>(n);
        for (ItemStack s : m) target.add(new Key(s));
        target.sort(ORDER);

        for (int i = 0; i < n; i++) {
            ItemStack want = target.get(i).stack;
            if (ItemStack.areEqual(m.get(i), want)) continue;

            int j = -1;
            for (int k = i + 1; k < n; k++) {
                if (ItemStack.areEqual(m.get(k), want)) {
                    j = k;
                    break;
                }
            }
            if (j == -1) continue;

            boolean iEmpty = m.get(i).isEmpty();
            clicks.add(ids.get(j));                    // j'yi al
            clicks.add(ids.get(i));                    // i'ye koy / takas et
            if (!iEmpty) clicks.add(ids.get(j));       // i'den çıkanı j'ye bırak

            ItemStack tmp = m.get(i);
            m.set(i, m.get(j));
            m.set(j, tmp);
        }
        return clicks;
    }

    // Sıralama anahtarı: bir kere hesaplanır (karşılaştırmada tekrar tekrar hesaplanmaz)
    private static final class Key {
        final ItemStack stack;
        final int rank;
        final String id;

        Key(ItemStack s) {
            this.stack = s;
            if (s.isEmpty()) {
                this.rank = 99;
                this.id = "";
            } else {
                this.rank = rank(s);
                this.id = Registries.ITEM.getId(s.getItem()).toString();
            }
        }
    }

    private static final Comparator<Key> ORDER = (x, y) -> {
        int r = Integer.compare(x.rank, y.rank);
        if (r != 0) return r;
        r = x.id.compareTo(y.id);
        if (r != 0) return r;
        return Integer.compare(y.stack.getCount(), x.stack.getCount());
    };

    private static int rank(ItemStack s) {
        if (s.isDamageable()) return 1;                       // alet, silah, zırh
        if (s.contains(DataComponentTypes.FOOD)) return 2;    // yiyecek
        if (s.getItem() instanceof BlockItem) return 0;       // bloklar
        return 3;                                             // diğer
    }
}
