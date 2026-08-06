package org.shee33.act0.arcade.arena;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HotZoneAreaEdges} 单元测试：12 条棱的数量、每条棱只沿单一坐标轴延伸（不出现对角线），
 * 以及 12 条棱拼起来恰好覆盖 AABB 的全部 8 个顶点、每个顶点被恰好 3 条棱共享。
 */
class HotZoneAreaEdgesTest {

    private static final double MIN_X = 0;
    private static final double MAX_X = 10;
    private static final double MIN_Y = 1;
    private static final double MAX_Y = 5;
    private static final double MIN_Z = -20;
    private static final double MAX_Z = 20;

    @Test
    void returnsExactlyTwelveEdges() {
        double[][] edges = HotZoneAreaEdges.edgesOf(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        assertEquals(HotZoneAreaEdges.EDGE_COUNT, edges.length);
        assertEquals(12, edges.length);
    }

    @Test
    void everyEdgeHasSixCoordinates() {
        double[][] edges = HotZoneAreaEdges.edgesOf(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        for (double[] edge : edges) {
            assertEquals(6, edge.length);
        }
    }

    @Test
    void firstFourEdgesAreFloorEdgesAtMinY() {
        double[][] edges = HotZoneAreaEdges.edgesOf(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        for (int i = 0; i < HotZoneAreaEdges.FLOOR_EDGE_COUNT; i++) {
            double[] e = edges[i];
            assertEquals(MIN_Y, e[1], "起点 Y 应为 minY");
            assertEquals(MIN_Y, e[4], "终点 Y 应为 minY");
        }
    }

    @Test
    void everyEdgeIsAxisAlignedNotDiagonal() {
        // 每条棱只应在恰好一个坐标轴上有非零跨度（AABB 的棱不应是对角线）。
        double[][] edges = HotZoneAreaEdges.edgesOf(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        for (double[] e : edges) {
            int nonZeroAxes = 0;
            if (e[0] != e[3]) {
                nonZeroAxes++;
            }
            if (e[1] != e[4]) {
                nonZeroAxes++;
            }
            if (e[2] != e[5]) {
                nonZeroAxes++;
            }
            assertEquals(1, nonZeroAxes, "每条棱只能沿一个坐标轴延伸：" + java.util.Arrays.toString(e));
        }
    }

    @Test
    void edgeLengthsMatchOneOfTheThreeAxisSpans() {
        double xSpan = MAX_X - MIN_X;
        double ySpan = MAX_Y - MIN_Y;
        double zSpan = MAX_Z - MIN_Z;
        double[][] edges = HotZoneAreaEdges.edgesOf(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        for (double[] e : edges) {
            double dx = Math.abs(e[3] - e[0]);
            double dy = Math.abs(e[4] - e[1]);
            double dz = Math.abs(e[5] - e[2]);
            double length = dx + dy + dz; // 恰好一轴非零，其余为 0，求和即总长
            assertTrue(length == xSpan || length == ySpan || length == zSpan,
                    "棱长应等于三个轴向跨度之一，实际=" + length);
        }
    }

    @Test
    void twelveEdgesCoverExactlyEightDistinctCorners() {
        double[][] edges = HotZoneAreaEdges.edgesOf(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        Set<String> corners = new HashSet<>();
        for (double[] e : edges) {
            corners.add(e[0] + "," + e[1] + "," + e[2]);
            corners.add(e[3] + "," + e[4] + "," + e[5]);
        }
        assertEquals(8, corners.size(), "AABB 恰有 8 个顶点");
    }

    @Test
    void everyCornerIsSharedByExactlyThreeEdges() {
        double[][] edges = HotZoneAreaEdges.edgesOf(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        java.util.Map<String, Integer> occurrences = new java.util.HashMap<>();
        for (double[] e : edges) {
            String a = e[0] + "," + e[1] + "," + e[2];
            String b = e[3] + "," + e[4] + "," + e[5];
            occurrences.merge(a, 1, Integer::sum);
            occurrences.merge(b, 1, Integer::sum);
        }
        assertEquals(8, occurrences.size());
        for (int count : occurrences.values()) {
            assertEquals(3, count, "AABB 每个顶点应恰好被 3 条棱共享");
        }
    }

    @Test
    void overloadFromHotZoneAreaMatchesDirectBoundsCall() {
        HotZoneArea area = new HotZoneArea("minecraft:overworld", MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        double[][] fromArea = HotZoneAreaEdges.edgesOf(area);
        double[][] fromBounds = HotZoneAreaEdges.edgesOf(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        assertEquals(fromBounds.length, fromArea.length);
        for (int i = 0; i < fromBounds.length; i++) {
            assertArrayEquals(fromBounds[i], fromArea[i], 1e-9);
        }
    }

    @Test
    void degenerateZeroSizedAreaStillProducesTwelveEdgesWithZeroLength() {
        double[][] edges = HotZoneAreaEdges.edgesOf(5, 5, 5, 5, 5, 5);
        assertEquals(12, edges.length);
        for (double[] e : edges) {
            assertEquals(e[0], e[3]);
            assertEquals(e[1], e[4]);
            assertEquals(e[2], e[5]);
        }
    }
}
