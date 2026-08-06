package org.shee33.act0.arcade.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HotZoneArea} 单元测试：min/max 归一化、{@link HotZoneArea#fromClicks} 的垂直容差、
 * 以及 {@link HotZoneArea#contains} 的 AABB 判定（含边界值、跨面判定）。
 */
class HotZoneAreaTest {

    // ---------------- 构造与归一化 ----------------

    @Test
    void constructorNormalizesMinMaxRegardlessOfArgumentOrder() {
        HotZoneArea a = new HotZoneArea("minecraft:overworld", 10, 0, 5, 1, 20, -20);
        assertEquals(0, a.minX());
        assertEquals(10, a.maxX());
        assertEquals(1, a.minY());
        assertEquals(5, a.maxY());
        assertEquals(-20, a.minZ());
        assertEquals(20, a.maxZ());
    }

    @Test
    void constructorRejectsNullDimension() {
        assertThrows(NullPointerException.class, () -> new HotZoneArea(null, 0, 1, 0, 1, 0, 1));
    }

    @Test
    void toStringContainsDimensionAndBounds() {
        HotZoneArea a = new HotZoneArea("minecraft:overworld", 0, 10, 0, 5, 0, 10);
        String s = a.toString();
        assertTrue(s.contains("minecraft:overworld"));
        assertTrue(s.contains("10.0"));
    }

    // ---------------- fromClicks：XZ 直接归一化 + Y 轴垂直容差 ----------------

    @Test
    void fromClicksNormalizesXZRegardlessOfCornerOrder() {
        // 角 1 是"右下"，角 2 是"左上"：不要求先点哪个角
        HotZoneArea a = HotZoneArea.fromClicks("minecraft:overworld",
                100, 64, 50,
                0, 64, -50);
        assertEquals(0, a.minX());
        assertEquals(100, a.maxX());
        assertEquals(-50, a.minZ());
        assertEquals(50, a.maxZ());
    }

    @Test
    void fromClicksExpandsYAxisByVerticalMarginOnBothSides() {
        HotZoneArea a = HotZoneArea.fromClicks("minecraft:overworld",
                0, 70, 0,
                10, 64, 10);
        // 两次点击 y=70/64 → 原始 min/max = 64/70，各向外扩 VERTICAL_MARGIN
        assertEquals(64 - HotZoneArea.VERTICAL_MARGIN, a.minY());
        assertEquals(70 + HotZoneArea.VERTICAL_MARGIN, a.maxY());
    }

    @Test
    void fromClicksSameYStillGetsSymmetricMargin() {
        HotZoneArea a = HotZoneArea.fromClicks("minecraft:overworld",
                0, 64, 0,
                10, 64, 10);
        assertEquals(64 - HotZoneArea.VERTICAL_MARGIN, a.minY());
        assertEquals(64 + HotZoneArea.VERTICAL_MARGIN, a.maxY());
    }

    // ---------------- contains：维度 + 边界 + 跨面判定 ----------------

    @Test
    void containsFalseWhenDimensionDiffers() {
        HotZoneArea a = new HotZoneArea("minecraft:overworld", 0, 10, 0, 10, 0, 10);
        assertFalse(a.contains("minecraft:the_nether", 5, 5, 5));
    }

    @Test
    void containsTrueForPointStrictlyInside() {
        HotZoneArea a = new HotZoneArea("minecraft:overworld", 0, 10, 0, 10, 0, 10);
        assertTrue(a.contains("minecraft:overworld", 5, 5, 5));
    }

    @Test
    void containsTrueOnExactBoundaries() {
        HotZoneArea a = new HotZoneArea("minecraft:overworld", 0, 10, 0, 10, 0, 10);
        assertTrue(a.contains("minecraft:overworld", 0, 0, 0));
        assertTrue(a.contains("minecraft:overworld", 10, 10, 10));
        assertTrue(a.contains("minecraft:overworld", 0, 5, 10));
    }

    @Test
    void containsFalseJustOutsideEachAxis() {
        HotZoneArea a = new HotZoneArea("minecraft:overworld", 0, 10, 0, 10, 0, 10);
        assertFalse(a.contains("minecraft:overworld", -0.01, 5, 5));
        assertFalse(a.contains("minecraft:overworld", 10.01, 5, 5));
        assertFalse(a.contains("minecraft:overworld", 5, -0.01, 5));
        assertFalse(a.contains("minecraft:overworld", 5, 10.01, 5));
        assertFalse(a.contains("minecraft:overworld", 5, 5, -0.01));
        assertFalse(a.contains("minecraft:overworld", 5, 5, 10.01));
    }

    @Test
    void containsFalseWhenOnlySomeAxesMatch() {
        // 跨面判定：x/y 在范围内，但 z 越界——必须三轴同时满足才算命中
        HotZoneArea a = new HotZoneArea("minecraft:overworld", 0, 10, 0, 10, 0, 10);
        assertFalse(a.contains("minecraft:overworld", 5, 5, 50));
        assertFalse(a.contains("minecraft:overworld", 50, 5, 5));
        assertFalse(a.contains("minecraft:overworld", 5, 50, 5));
    }
}
