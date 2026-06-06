package org.shee33.act0.arcade.loadout;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个玩家的多套配装集合。
 *
 * <p>固定 5 个方案槽，{@link #activeIndex()} 表示下次发装备使用的方案；{@link #defaultIndex()}
 * 表示玩家默认方案。旧版单套配装会迁移为第 1 套。
 */
public final class LoadoutSet {

    public static final int MAX_SLOTS = 5;

    private final List<Loadout> loadouts = new ArrayList<>(MAX_SLOTS);
    private int activeIndex;
    private int defaultIndex;

    public LoadoutSet(List<Loadout> loadouts, int activeIndex) {
        this(loadouts, activeIndex, 0);
    }

    public LoadoutSet(List<Loadout> loadouts, int activeIndex, int defaultIndex) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            Loadout l = loadouts != null && i < loadouts.size() ? loadouts.get(i) : null;
            this.loadouts.add(l != null ? l : emptyLoadout(i));
        }
        this.activeIndex = clamp(activeIndex);
        this.defaultIndex = clamp(defaultIndex);
    }

    public static LoadoutSet single(Loadout loadout) {
        List<Loadout> list = new ArrayList<>();
        list.add(loadout != null ? loadout : emptyLoadout(0));
        return new LoadoutSet(list, 0);
    }

    public int activeIndex() {
        return activeIndex;
    }

    public int defaultIndex() {
        return defaultIndex;
    }

    public void setActiveIndex(int index) {
        this.activeIndex = clamp(index);
    }

    public void setDefaultIndex(int index) {
        this.defaultIndex = clamp(index);
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
