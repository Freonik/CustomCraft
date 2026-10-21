package cz.forgebound;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The ruins that generate in the Scorched Lands, built block by block with random decay so no two look the same.
 * Pure Java on purpose: blocks are written as strings ("minecraft:anvil[facing=north]") through {@link Canvas},
 * so the shapes can be tested and previewed without a server. {@link ScorchedLands} plugs it into world generation.
 */
final class ForgeRuins {

    /** Where the blocks go. Coordinates are absolute. */
    interface Canvas {
        void set(int x, int y, int z, String block);

        /** The block id there, e.g. "minecraft:air" (no states). */
        String get(int x, int y, int z);
    }

    enum Type {
        FORGE(11, 9), WORKSHOP(7, 7), SLAG_HEAP(9, 9), WATCHTOWER(5, 5),
        /** The intact Ancient Forge where legend weapons ascend (rare; never picked by {@link #pick}). */
        ANCIENT(13, 13), ANCIENT_NETHER(13, 13);

        final int width;
        final int depth;

        Type(int width, int depth) {
            this.width = width;
            this.depth = depth;
        }
    }

    /** What the builder placed that needs more than a block: chests (with a loot table) and guard spawn points. */
    static final class Result {
        final List<int[]> bigChests = new ArrayList<>();
        final List<int[]> smallChests = new ArrayList<>();
        final List<int[]> guards = new ArrayList<>();
        /** The Ancient Forge's anvil (where weapons ascend), or null. */
        int[] core;
    }

    private static final String[] FACINGS = {"north", "south", "east", "west"};

    /** Picks a type with weights: forge 25, workshop 35, slag heap 25, watchtower 15. */
    static Type pick(SplittableRandom r) {
        int roll = r.nextInt(100);
        return roll < 25 ? Type.FORGE : roll < 60 ? Type.WORKSHOP : roll < 85 ? Type.SLAG_HEAP : Type.WATCHTOWER;
    }

    static Result build(Type type, Canvas c, int x0, int y0, int z0, SplittableRandom r) {
        B b = new B(c, x0, y0, z0, r);
        Result result = new Result();
        b.foundation(type.width, type.depth);
        switch (type) {
            case FORGE -> forge(b, result);
            case WORKSHOP -> workshop(b, result);
            case SLAG_HEAP -> slagHeap(b, result);
            case WATCHTOWER -> watchtower(b, result);
            case ANCIENT -> ancient(b, result, false);
            case ANCIENT_NETHER -> ancient(b, result, true);
        }
        return result;
    }

    // ================================================================ the buildings

    /** A big ruined forge: broken walls, a tall chimney with a lava hearth, anvils, a blast furnace, chains. */
    private static void forge(B b, Result res) {
        int w = 11;
        int d = 9;
        b.clear(w, d, 12);
        b.floor(w, d, new String[] {"polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "blackstone",
                "polished_blackstone", "gravel"});
        // outer walls, decayed; a doorway in the middle of the front wall
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (!edge || (z == 0 && (x == 5 || x == 4 && b.r.nextBoolean()))) {
                    continue;
                }
                boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                int h = corner ? 4 + b.r.nextInt(3) : 1 + b.r.nextInt(5);
                for (int y = 1; y <= h; y++) {
                    if (y == 3 && !corner && (x % 3 == 2 || z % 3 == 2) && b.r.nextInt(3) == 0) {
                        b.set(x, y, z, "iron_bars");                      // window
                    } else if (b.r.nextInt(12) != 0) {
                        b.set(x, y, z, corner ? "polished_blackstone_bricks" : b.pick(
                                "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "blackstone",
                                "cracked_polished_blackstone_bricks"));
                    }
                }
            }
        }
        // chimney in the back right corner (3x3, hollow), lava cauldron as the hearth
        int cx = 7;
        int cz = 5;
        int top = 9 + b.r.nextInt(4);
        for (int y = 1; y <= top; y++) {
            for (int x = cx; x < cx + 3; x++) {
                for (int z = cz; z < cz + 3; z++) {
                    boolean inner = x == cx + 1 && z == cz + 1;
                    boolean opening = z == cz && x == cx + 1 && y <= 2;      // the hearth opening faces the room
                    if (inner || opening) {
                        b.set(x, y, z, "air");
                        continue;
                    }
                    if (y > top - 3 && b.r.nextInt(3) == 0) {
                        continue;                                           // broken top
                    }
                    b.set(x, y, z, y % 4 == 0 ? "chiseled_polished_blackstone" : b.pick("bricks", "bricks", "polished_blackstone_bricks"));
                }
            }
        }
        b.set(cx + 1, 0, cz + 1, "magma_block");
        b.set(cx + 1, 1, cz + 1, "lava_cauldron");
        b.set(cx + 1, 0, cz - 1, "magma_block");
        // work floor: anvils, blast furnace, grindstone, smithing table, barrel, coal
        b.set(3, 1, 4, "damaged_anvil[facing=" + b.facing() + "]");
        b.set(5, 1, 6, "chipped_anvil[facing=" + b.facing() + "]");
        b.set(6, 1, 4, "blast_furnace[facing=west]");
        b.set(2, 1, 7, "grindstone[face=floor,facing=" + b.facing() + "]");
        if (b.r.nextBoolean()) {
            b.set(4, 1, 7, "smithing_table");
        }
        b.set(1, 1, 7, "barrel[facing=up]");
        b.set(1, 1, 1, "coal_block");
        if (b.r.nextBoolean()) {
            b.set(1, 2, 1, "coal_block");
        }
        // a charred roof beam with hanging chains
        int beamZ = 3 + b.r.nextInt(2);
        for (int x = 1; x < w - 1; x++) {
            if (x > 2 && x < 7 && b.r.nextInt(4) == 0) {
                continue;                                                   // burnt through
            }
            b.set(x, 5, beamZ, "stripped_dark_oak_log[axis=x]");
        }
        for (int x : new int[] {2, 5}) {
            if (b.get(x, 5, beamZ).endsWith("log")) {
                int len = 1 + b.r.nextInt(3);
                for (int y = 4; y > 4 - len; y--) {
                    b.set(x, y, beamZ, "iron_chain[axis=y]");
                }
            }
        }
        // cobwebs in the corners
        b.set(1, 3, d - 2, "cobweb");
        if (b.r.nextBoolean()) {
            b.set(w - 2, 2, 1, "cobweb");
        }
        // loot
        b.set(2, 1, 1, "chest[facing=south]");
        res.bigChests.add(b.abs(2, 1, 1));
        res.guards.add(b.abs(4, 1, 2));
        res.guards.add(b.abs(7, 1, 2));
        if (b.r.nextBoolean()) {
            res.guards.add(b.abs(3, 1, 6));
        }
    }

    /**
     * The Ancient Forge: an intact round hall of dark stone and gold with four pillars, a crown ring, hanging soul
     * lanterns and a crying-obsidian altar with the anvil in the middle. In the Nether it's built of nether bricks.
     */
    private static void ancient(B b, Result res, boolean nether) {
        int n = 13;
        int c = 6;
        String brick = nether ? "nether_bricks" : "polished_blackstone_bricks";
        String cracked = nether ? "cracked_nether_bricks" : "cracked_polished_blackstone_bricks";
        String accent = nether ? "red_nether_bricks" : "gilded_blackstone";
        String pillar = nether ? "red_nether_bricks" : "polished_blackstone";
        String stairs = nether ? "nether_brick_stairs" : "polished_blackstone_brick_stairs";
        String slab = nether ? "nether_brick_slab" : "polished_blackstone_brick_slab";
        b.clear(n, n, 10);
        // round floor: rings of brick, an accent ring, gold toward the altar
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                double d = Math.hypot(x - c, z - c);
                if (d > 6.6) {
                    continue;
                }
                String block = d > 5.6 ? brick : d > 4.6 ? accent : d > 2.6 ? b.pick(brick, brick, cracked) : "chiseled_" + (nether ? "nether_bricks" : "polished_blackstone");
                b.set(x, 0, z, block);
            }
        }
        // four pillars on the diagonals, gold on top, with arches (upside-down stairs) between them
        int[][] pillars = {{2, 2}, {10, 2}, {2, 10}, {10, 10}};
        for (int[] p : pillars) {
            for (int y = 1; y <= 6; y++) {
                b.set(p[0], y, p[1], y == 3 ? "chiseled_" + (nether ? "nether_bricks" : "polished_blackstone") : pillar);
            }
            b.set(p[0], 7, p[1], "gold_block");
        }
        for (int i = 3; i <= 9; i++) {
            b.set(i, 7, 2, i == 6 ? "gold_block" : slab + "[type=bottom]");
            b.set(i, 7, 10, i == 6 ? "gold_block" : slab + "[type=bottom]");
            b.set(2, 7, i, i == 6 ? "gold_block" : slab + "[type=bottom]");
            b.set(10, 7, i, i == 6 ? "gold_block" : slab + "[type=bottom]");
            if (i == 3 || i == 9) {
                b.set(i, 6, 2, stairs + "[facing=" + (i == 3 ? "east" : "west") + ",half=top]");
                b.set(i, 6, 10, stairs + "[facing=" + (i == 3 ? "east" : "west") + ",half=top]");
                b.set(2, 6, i, stairs + "[facing=" + (i == 3 ? "south" : "north") + ",half=top]");
                b.set(10, 6, i, stairs + "[facing=" + (i == 3 ? "south" : "north") + ",half=top]");
            }
        }
        // soul lanterns hanging from the crown ring over the altar
        for (int[] p : new int[][] {{6, 2}, {6, 10}, {2, 6}, {10, 6}}) {
            int x = p[0] == 6 ? 6 : p[0] == 2 ? 3 : 9;
            int z = p[1] == 6 ? 6 : p[1] == 2 ? 3 : 9;
            b.set(x, 7, z, slab + "[type=bottom]");
            b.set(x, 6, z, "iron_chain[axis=y]");
            b.set(x, 5, z, "soul_lantern[hanging=true]");
        }
        // the altar: crying obsidian, gold corners, lava hearths on both sides, the anvil on top
        for (int x = c - 1; x <= c + 1; x++) {
            for (int z = c - 1; z <= c + 1; z++) {
                boolean corner = x != c && z != c;
                b.set(x, 1, z, corner ? "gold_block" : "crying_obsidian");
            }
        }
        b.set(c, 2, c, "anvil[facing=north]");
        res.core = b.abs(c, 2, c);
        for (int[] h : new int[][] {{c - 3, c}, {c + 3, c}}) {
            b.set(h[0], 0, h[1], "magma_block");
            b.set(h[0], 1, h[1], "lava_cauldron");
        }
        b.set(c, 1, c + 3, "smithing_table");
        b.set(c - 2, 1, c + 4, "blast_furnace[facing=north]");
        b.set(c + 2, 1, c + 4, "grindstone[face=floor,facing=north]");
        // the forge keeper's chest behind the altar
        b.set(c, 1, c - 4, "chest[facing=south]");
        res.bigChests.add(b.abs(c, 1, c - 4));
        for (int[] g : new int[][] {{c - 3, c - 3}, {c + 3, c + 3}, {c + 3, c - 3}}) {
            res.guards.add(b.abs(g[0], 1, g[1]));
        }
    }

    /** A small burnt workshop: low walls, roof debris, a furnace and a crafting table. */
    private static void workshop(B b, Result res) {
        int w = 7;
        int d = 7;
        b.clear(w, d, 6);
        b.floor(w, d, new String[] {"stone_bricks", "cracked_stone_bricks", "cobblestone", "coarse_dirt"});
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (!edge || (z == 0 && x == 3)) {
                    continue;
                }
                int h = b.r.nextInt(4);
                for (int y = 1; y <= h; y++) {
                    b.set(x, y, z, b.pick("stone_bricks", "cracked_stone_bricks", "cobblestone", "stone_bricks"));
                }
                if (h > 0 && b.r.nextInt(5) == 0) {
                    b.set(x, h, z, "dark_oak_slab[type=bottom]");           // charred roof edge
                }
            }
        }
        // the collapsed roof on the floor
        for (int i = 0; i < 6; i++) {
            int x = 1 + b.r.nextInt(w - 2);
            int z = 1 + b.r.nextInt(d - 2);
            b.set(x, 1, z, b.r.nextBoolean() ? "dark_oak_slab[type=bottom]"
                    : "dark_oak_stairs[facing=" + b.facing() + ",half=bottom]");
        }
        b.set(5, 1, 5, "furnace[facing=north]");
        b.set(4, 1, 5, "crafting_table");
        b.set(1, 1, 5, "barrel[facing=up]");
        if (b.r.nextInt(3) == 0) {
            b.set(5, 1, 2, "chipped_anvil[facing=" + b.facing() + "]");
        }
        b.set(1, 1, 1, "chest[facing=east]");
        res.smallChests.add(b.abs(1, 1, 1));
        if (b.r.nextBoolean()) {
            res.guards.add(b.abs(3, 1, 3));
        }
    }

    /** Heaps of slag and ore around a broken smelter. */
    private static void slagHeap(B b, Result res) {
        int w = 9;
        int d = 9;
        b.clear(w, d, 7);
        double c = 4;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                double dist = Math.hypot(x - c, z - c);
                int h = (int) Math.round(3.2 - dist * 0.7 + b.r.nextDouble() * 1.2);
                for (int y = 0; y <= h; y++) {
                    int roll = b.r.nextInt(100);
                    String block = roll < 30 ? "gravel" : roll < 55 ? "blackstone" : roll < 70 ? "tuff" : roll < 80 ? "coal_ore"
                            : roll < 86 ? "iron_ore" : roll < 92 ? "magma_block" : "cobbled_deepslate";
                    b.set(x, y, z, block);
                }
            }
        }
        // the smelter: a blast furnace under a short brick chimney
        b.set(4, 4, 4, "blast_furnace[facing=south]");
        int top = 3 + b.r.nextInt(3);
        for (int y = 5; y < 5 + top; y++) {
            if (y == 5 + top - 1 && b.r.nextBoolean()) {
                b.set(4, y, 4, "iron_bars");
            } else {
                b.set(4, y, 4, "bricks");
            }
        }
        // a chest half buried at the edge of the heap
        b.set(1, 1, 4, "chest[facing=west]");
        b.set(1, 2, 4, "air");
        res.smallChests.add(b.abs(1, 1, 4));
    }

    /** A half-collapsed watchtower with a chest on the upper floor. */
    private static void watchtower(B b, Result res) {
        int w = 5;
        int d = 5;
        int top = 9 + b.r.nextInt(4);
        b.clear(w, d, top + 2);
        b.floor(w, d, new String[] {"stone_bricks", "cracked_stone_bricks", "cobblestone"});
        int floorY = 6;
        for (int y = 1; y <= top; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    if (!edge) {
                        continue;
                    }
                    if (z == 0 && x == 2 && y <= 2) {
                        continue;                                           // doorway
                    }
                    if (y > top - 4 && b.r.nextInt(3) == 0) {
                        continue;                                           // crumbling top
                    }
                    if (y % 4 == 2 && x == 2 && z == d - 1) {
                        b.set(x, y, z, "iron_bars");                        // arrow slit
                        continue;
                    }
                    b.set(x, y, z, b.pick("stone_bricks", "cracked_stone_bricks", "stone_bricks", "cobblestone"));
                }
            }
        }
        // upper floor with a hole for the ladder
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < d - 1; z++) {
                if (!(x == 3 && z == 3)) {
                    b.set(x, floorY, z, b.r.nextInt(5) == 0 ? "air" : "spruce_planks");
                }
            }
        }
        for (int y = 1; y <= floorY; y++) {
            if (y < 3 || b.r.nextInt(6) != 0) {                             // a few rungs burnt away
                b.set(3, y, 3, "ladder[facing=north]");
            }
        }
        b.set(1, floorY + 1, 1, "chest[facing=south]");
        res.smallChests.add(b.abs(1, floorY + 1, 1));
        b.set(1, floorY + 1, 3, "barrel[facing=up]");
        res.guards.add(b.abs(2, 1, -2));
    }

    // ================================================================ helpers

    /** Builder with local coordinates (0,0,0 = the floor corner). */
    private static final class B {
        final Canvas c;
        final int x0;
        final int y0;
        final int z0;
        final SplittableRandom r;

        B(Canvas c, int x0, int y0, int z0, SplittableRandom r) {
            this.c = c;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.r = r;
        }

        void set(int x, int y, int z, String block) {
            c.set(x0 + x, y0 + y, z0 + z, block.contains(":") ? block : "minecraft:" + block);
        }

        String get(int x, int y, int z) {
            return c.get(x0 + x, y0 + y, z0 + z);
        }

        int[] abs(int x, int y, int z) {
            return new int[] {x0 + x, y0 + y, z0 + z};
        }

        String pick(String... options) {
            return options[r.nextInt(options.length)];
        }

        String facing() {
            return FACINGS[r.nextInt(4)];
        }

        /** Air above the footprint (removes the burnt trees and the terrain bump inside). */
        void clear(int w, int d, int h) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    for (int y = 1; y <= h; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
        }

        void floor(int w, int d, String[] palette) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    set(x, 0, z, palette[r.nextInt(palette.length)]);
                }
            }
        }

        /** Fills the gap under the floor down to the ground, so nothing floats on a slope. */
        void foundation(int w, int d) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    for (int y = -1; y >= -8; y--) {
                        if (!fillable(get(x, y, z))) {
                            break;
                        }
                        set(x, y, z, r.nextInt(3) == 0 ? "cobbled_deepslate" : "cobblestone");
                    }
                }
            }
        }
    }

    static boolean fillable(String id) {
        String s = id.substring(id.indexOf(':') + 1);
        return s.endsWith("air") || s.equals("water") || s.contains("grass") || s.contains("flower") || s.contains("fern")
                || s.contains("bush") || s.equals("snow") || s.endsWith("leaves") || s.equals("vine") || s.contains("petals");
    }
}
