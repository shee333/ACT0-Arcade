package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配件（{@link ArcadeAttachment}/{@link AttachmentRegistry}/{@link AttachmentSlotType}）与
 * 服饰（{@link ApparelItem}/{@link ApparelRegistry}/{@link ApparelSelection}/{@link ApparelSlot}）
 * 单元测试。这两套目录结构与 {@link LoadoutItem}/{@link LoadoutRegistry} 平行，按包主题分组到同一文件。
 */
class AttachmentApparelTest {

    // ---------------- ArcadeAttachment ----------------

    @Test
    void attachmentBuilderClampsPriceAndDefaultsIsDefaultFalse() {
        ArcadeAttachment attachment = ArcadeAttachment
                .builder("attach.scope", "tacz:scope_acog_ta31", AttachmentSlotType.SCOPE)
                .price(-100)
                .build();

        assertEquals(0, attachment.price());
        assertFalse(attachment.isDefault());
        assertEquals("attach.scope", attachment.displayName());
    }

    @Test
    void attachmentIsUnlockedByFollowsSameRuleAsLoadoutItem() {
        ArcadeAttachment defaultAttachment = ArcadeAttachment
                .builder("attach.default_grip", "tacz:grip_default", AttachmentSlotType.GRIP)
                .isDefault(true).build();
        ArcadeAttachment paidAttachment = ArcadeAttachment
                .builder("attach.paid_muzzle", "tacz:muzzle_x", AttachmentSlotType.MUZZLE)
                .price(500).build();

        assertTrue(defaultAttachment.isUnlockedBy(null));
        assertTrue(defaultAttachment.isUnlockedBy(Set.of()));
        assertFalse(paidAttachment.isUnlockedBy(null));
        assertFalse(paidAttachment.isUnlockedBy(Set.of("other")));
        assertTrue(paidAttachment.isUnlockedBy(Set.of("attach.paid_muzzle")));
    }

    // ---------------- AttachmentRegistry ----------------

    @Test
    void attachmentRegistryRegisterDuplicateKeyThrows() {
        AttachmentRegistry reg = new AttachmentRegistry();
        ArcadeAttachment a = ArcadeAttachment.builder("attach.a", "tacz:a", AttachmentSlotType.SCOPE).build();
        reg.register(a);

        assertThrows(IllegalStateException.class,
                () -> reg.register(ArcadeAttachment.builder("attach.a", "tacz:a2", AttachmentSlotType.STOCK).build()));
    }

    @Test
    void attachmentRegistryFindsByKeyAndByAttachmentId() {
        AttachmentRegistry reg = new AttachmentRegistry();
        ArcadeAttachment a = ArcadeAttachment.builder("attach.a", "tacz:scope_a", AttachmentSlotType.SCOPE).build();
        reg.register(a);

        assertEquals(a, reg.find("attach.a").orElse(null));
        assertEquals(a, reg.findByAttachmentId("tacz:scope_a").orElse(null));
        assertTrue(reg.findByAttachmentId("tacz:does_not_exist").isEmpty());
        assertTrue(reg.findByAttachmentId(null).isEmpty());
    }

    @Test
    void attachmentRegistryItemsForSlotFiltersBySlotType() {
        AttachmentRegistry reg = new AttachmentRegistry();
        ArcadeAttachment scope = ArcadeAttachment.builder("attach.scope", "tacz:s", AttachmentSlotType.SCOPE).build();
        ArcadeAttachment grip = ArcadeAttachment.builder("attach.grip", "tacz:g", AttachmentSlotType.GRIP).build();
        reg.register(scope);
        reg.register(grip);

        assertEquals(List.of(scope), reg.itemsForSlot(AttachmentSlotType.SCOPE));
        assertTrue(reg.itemsForSlot(null).isEmpty());
    }

    @Test
    void attachmentRegistryAllIsUnmodifiableAndClearEmpties() {
        AttachmentRegistry reg = new AttachmentRegistry();
        reg.register(ArcadeAttachment.builder("attach.a", "tacz:a", AttachmentSlotType.SCOPE).build());

        assertThrows(UnsupportedOperationException.class,
                () -> reg.all().put("x", ArcadeAttachment.builder("x", "tacz:x", AttachmentSlotType.LASER).build()));

        reg.clear();
        assertTrue(reg.all().isEmpty());
    }

    // ---------------- AttachmentSlotType ----------------

    @Test
    void attachmentSlotTypeByNameResolvesEnumAndTaczNameCaseInsensitively() {
        assertEquals(AttachmentSlotType.SCOPE, AttachmentSlotType.byName("SCOPE"));
        assertEquals(AttachmentSlotType.SCOPE, AttachmentSlotType.byName("scope"));
        assertEquals(AttachmentSlotType.EXTENDED_MAG, AttachmentSlotType.byName("extended_mag"));
        assertEquals(AttachmentSlotType.EXTENDED_MAG, AttachmentSlotType.byName("EXTENDED_MAG"));
    }

    @Test
    void attachmentSlotTypeByNameUnknownOrNullReturnsNull() {
        assertNull(AttachmentSlotType.byName("bogus"));
        assertNull(AttachmentSlotType.byName(null));
    }

    @Test
    void attachmentSlotTypeTaczNameIsLowercaseEnumName() {
        for (AttachmentSlotType type : AttachmentSlotType.values()) {
            assertEquals(type.name().toLowerCase(java.util.Locale.ROOT), type.taczName());
            assertEquals(type.name(), type.taczEnumName());
        }
    }

    // ---------------- ApparelItem ----------------

    @Test
    void apparelItemClampsPriceAndFallsBackDisplayNameToKey() {
        ApparelItem item = new ApparelItem("helmet_basic", null, ApparelSlot.HELMET, -50, false, null);

        assertEquals(0, item.price());
        assertEquals("helmet_basic", item.displayName());
    }

    @Test
    void apparelItemIsUnlockedByFollowsDefaultOrMembershipRule() {
        ApparelItem freeItem = new ApparelItem("boots_basic", "基础靴子", ApparelSlot.BOOTS, 0, true, null);
        ApparelItem paidItem = new ApparelItem("boots_gold", "黄金靴子", ApparelSlot.BOOTS, 2000, false, null);

        assertTrue(freeItem.isUnlockedBy(null));
        assertFalse(paidItem.isUnlockedBy(Set.of("boots_basic")));
        assertTrue(paidItem.isUnlockedBy(Set.of("boots_gold")));
    }

    // ---------------- ApparelRegistry ----------------

    @Test
    void apparelRegistryDuplicateKeyThrowsAndItemsForSlotFilters() {
        ApparelRegistry reg = new ApparelRegistry();
        ApparelItem helmet = new ApparelItem("helmet_basic", "头盔", ApparelSlot.HELMET, 0, true, null);
        reg.register(helmet);

        assertThrows(IllegalStateException.class,
                () -> reg.register(new ApparelItem("helmet_basic", "重复", ApparelSlot.HELMET, 0, false, null)));
        assertEquals(List.of(helmet), reg.itemsForSlot(ApparelSlot.HELMET));
        assertTrue(reg.itemsForSlot(ApparelSlot.BOOTS).isEmpty());
        assertEquals(helmet, reg.find("helmet_basic").orElse(null));

        reg.clear();
        assertTrue(reg.all().isEmpty());
    }

    // ---------------- ApparelSelection ----------------

    @Test
    void apparelSelectionSetAndRetrieveBySlot() {
        ApparelSelection selection = new ApparelSelection();
        selection.set(ApparelSlot.HELMET, "helmet_basic");

        assertEquals("helmet_basic", selection.selectedKey(ApparelSlot.HELMET).orElse(null));
        assertTrue(selection.selectedKey(ApparelSlot.BOOTS).isEmpty());
    }

    @Test
    void apparelSelectionSetNullOrBlankKeyRemovesSelection() {
        ApparelSelection selection = new ApparelSelection();
        selection.set(ApparelSlot.HELMET, "helmet_basic");

        selection.set(ApparelSlot.HELMET, "");
        assertTrue(selection.selectedKey(ApparelSlot.HELMET).isEmpty());

        selection.set(ApparelSlot.HELMET, "helmet_basic");
        selection.set(ApparelSlot.HELMET, null);
        assertTrue(selection.selectedKey(ApparelSlot.HELMET).isEmpty());
    }

    @Test
    void apparelSelectionCopyIsIndependentFromOriginal() {
        ApparelSelection original = new ApparelSelection();
        original.set(ApparelSlot.HELMET, "helmet_basic");

        ApparelSelection copy = original.copy();
        copy.set(ApparelSlot.HELMET, "helmet_gold");

        assertEquals("helmet_basic", original.selectedKey(ApparelSlot.HELMET).orElse(null));
        assertEquals("helmet_gold", copy.selectedKey(ApparelSlot.HELMET).orElse(null));
    }

    @Test
    void apparelSelectionSetWithNullSlotIsNoOp() {
        ApparelSelection selection = new ApparelSelection();
        selection.set(null, "x");
        assertTrue(selection.all().isEmpty());
    }

    // ---------------- ApparelSlot ----------------

    @Test
    void apparelSlotByNameResolvesEnumAndDisplayName() {
        assertEquals(ApparelSlot.HELMET, ApparelSlot.byName("HELMET"));
        assertEquals(ApparelSlot.HELMET, ApparelSlot.byName("头盔"));
        assertNull(ApparelSlot.byName(null));
    }

    @Test
    void apparelSlotByNameResolvesLegacyAliasesAndTrimsWhitespace() {
        assertEquals(ApparelSlot.HELMET, ApparelSlot.byName("  head  "));
        assertEquals(ApparelSlot.CHESTPLATE, ApparelSlot.byName("armor"));
        assertEquals(ApparelSlot.CHESTPLATE, ApparelSlot.byName("chest"));
        assertEquals(ApparelSlot.LEGGINGS, ApparelSlot.byName("pants"));
        assertEquals(ApparelSlot.BOOTS, ApparelSlot.byName("shoes"));
        assertNull(ApparelSlot.byName("bogus"));
    }
}
