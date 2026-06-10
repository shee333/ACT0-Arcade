package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.ApparelSlot;

/** 服饰选择 NBT 编解码。 */
public final class ApparelSelectionCodec {
    private ApparelSelectionCodec() {
    }

    public static CompoundTag write(ApparelSelection selection) {
        CompoundTag tag = new CompoundTag();
        if (selection == null) {
            return tag;
        }
        for (var e : selection.all().entrySet()) {
            tag.putString(e.getKey().name(), e.getValue());
        }
        return tag;
    }

    public static ApparelSelection read(CompoundTag tag) {
        ApparelSelection selection = new ApparelSelection();
        if (tag == null) {
            return selection;
        }
        for (ApparelSlot slot : ApparelSlot.values()) {
            if (tag.contains(slot.name())) {
                selection.set(slot, tag.getString(slot.name()));
            }
        }
        return selection;
    }
}
