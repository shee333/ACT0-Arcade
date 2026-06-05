package org.shee33.act0.arcade.loadout;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个玩家的多套配装集合。
 *
 * <p>首版固定 5 个方案槽，{@link #activeIndex()} 表示当前激活方案；开局/重生发装备时使用激活方案，
 * GUI 可切换不同方案编辑。旧版单套配装会迁移为第 1 套。
 */
public final class LoadoutSet {

    public static final int MAX_SLOTS = 5;

    private final List<Loadout> loadouts = new ArrayList<>(MAX_SLOTS);
    private int activeIndex;

    public LoadoutSet(List<Loadout> loadouts, int activeIndex) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            Loadout l = loadouts != null && i < loadouts.size() ? loadouts.get(i) : null;
            this.loadouts.add(l != null ? l : emptyLoadout(i));
        }
        this.activeIndex = clamp(activeIndex);
    }

    public static LoadoutSet single(Loadout loadout) {
        List<Loadout> list = new ArrayList<>();
        list.add(loadout != null ? loadout : emptyLoadout(0));
        return new LoadoutSet(list, 0);
    }

    public int activeIndex() {
        return activeIndex;
    }

    public void setActiveIndex(int index) {
        this.activeIndex = clamp(index);
    }

    public Loadout active() {
        return loadouts.get(activeIndex);
    }

    public Loadout get(int index) {
        return loadouts.get(clamp(index));
    }

    public void set(int index, Loadout loadout) {
        loadouts.set(clamp(index), loadout != null ? loadout : emptyLoadout(index));
    }

    public List<Loadout> all() {
        return List.copyOf(loadouts);
    }

    private static int clamp(int index) {
        if (index < 0) {
            return 0;
        }
        return Math.min(MAX_SLOTS - 1, index);
    }

    private static Loadout emptyLoadout(int index) {
        return new Loadout("配装 " + (index + 1));
    }
}
