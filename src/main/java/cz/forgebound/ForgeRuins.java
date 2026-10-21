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
        ANCIENT(25, 25), ANCIENT_NETHER(25, 25),
        /** The End's Ancient Forge on its own floating island (raised when the dragon dies, not generated). */
        ANCIENT_END(25, 25);

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
        if (type != Type.ANCIENT_END) {
            b.foundation(type.width, type.depth);
        }
        switch (type) {
            case FORGE -> forge(b, result);
            case WORKSHOP -> workshop(b, result);
            case SLAG_HEAP -> slagHeap(b, result);
            case WATCHTOWER -> watchtower(b, result);
            case ANCIENT -> ancient(b, result, 0);
            case ANCIENT_NETHER -> ancient(b, result, 1);
            case ANCIENT_END -> {
                island(b);
                ancient(b, result, 2);
            }
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
     * The Ancient Forge, a landmark: a round stepped platform (three tiers, stairs on all four sides), a hall of
     * eight pillars under an open-work dome with an oculus, four corner towers with signal fires (their smoke rises
     * high into the sky) and, in the middle, a crying-obsidian altar with the anvil. In the Nether it's built of
     * nether bricks.
     */
    private static void ancient(B b, Result res, int style) {
        int n = 25;
        int c = 12;
        // 0 = Scorched Lands (blackstone and gold), 1 = Nether (nether bricks), 2 = End (end stone, purpur, obsidian)
        String brick = new String[] {"polished_blackstone_bricks", "nether_bricks", "end_stone_bricks"}[style];
        String cracked = new String[] {"cracked_polished_blackstone_bricks", "cracked_nether_bricks", "end_stone_bricks"}[style];
        String accent = new String[] {"gilded_blackstone", "red_nether_bricks", "purpur_block"}[style];
        String chiseled = new String[] {"chiseled_polished_blackstone", "chiseled_nether_bricks", "purpur_pillar[axis=y]"}[style];
        String pillar = new String[] {"polished_blackstone", "red_nether_bricks", "obsidian"}[style];
        String stairs = new String[] {"polished_blackstone_brick_stairs", "nether_brick_stairs", "purpur_stairs"}[style];
        String slab = new String[] {"polished_blackstone_brick_slab", "nether_brick_slab", "purpur_slab"}[style];
        String fire = new String[] {"campfire", "soul_campfire", "soul_campfire"}[style];
        b.clear(n, n, 22);
        // ---- three round tiers: y 0 (r 12), y 1 (r 10), y 2 (r 8); stairs up on the four sides
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                double d = Math.hypot(x - c, z - c);
                if (d > 12.4) {
                    continue;
                }
                b.set(x, 0, z, d > 11.5 ? accent : b.pick(brick, brick, brick, cracked));
                if (d <= 10.4) {
                    b.set(x, 1, z, d > 9.5 ? chiseled : b.pick(brick, brick, cracked));
                }
                if (d <= 8.4) {
                    b.set(x, 2, z, d > 7.5 ? accent : d < 3.6 && d > 2.5 ? "gold_block" : b.pick(brick, brick, brick, cracked));
                }
            }
        }
        for (int side = 0; side < 4; side++) {
            String facing = side == 0 ? "south" : side == 1 ? "north" : side == 2 ? "east" : "west";
            for (int w = -1; w <= 1; w++) {
                for (int[] step : new int[][] {{12, 0}, {10, 1}, {8, 2}}) {
                    int r = step[0];
                    int x = side == 0 || side == 1 ? c + w : side == 2 ? c - r : c + r;
                    int z = side == 0 ? c - r : side == 1 ? c + r : c + w;
                    b.set(x, step[1], z, stairs + "[facing=" + facing + "]");
                }
            }
        }
        // ---- eight pillars on a circle (r 7), a ring on top, gold caps
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int px = c + (int) Math.round(Math.cos(a) * 7);
            int pz = c + (int) Math.round(Math.sin(a) * 7);
            for (int y = 3; y <= 11; y++) {
                b.set(px, y, pz, y == 3 || y == 7 ? chiseled : pillar);
            }
            b.set(px, 12, pz, "gold_block");
        }
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                double d = Math.hypot(x - c, z - c);
                if (d > 6.4 && d < 7.6 && !b.get(x, 12, z).endsWith("gold_block")) {
                    b.set(x, 12, z, (x + z) % 4 == 0 ? accent : brick);
                }
            }
        }
        // ---- the dome: open lattice, a solid band at the bottom, an oculus at the top
        for (int y = 13; y <= 18; y++) {
            double r = 7.2 * Math.sqrt(Math.max(0, 1 - Math.pow((y - 12) / 6.6, 2)));
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) {
                    double d = Math.hypot(x - c, z - c);
                    if (Math.abs(d - r) > 0.75 || d < 1.6) {
                        continue;
                    }
                    if (y > 13 && (x + z + y) % 3 == 0) {
                        continue;                                            // openings
                    }
                    b.set(x, y, z, y == 13 ? accent : brick);
                }
            }
        }
        // ---- soul lanterns on chains from the dome
        for (int[] p : new int[][] {{c - 4, c}, {c + 4, c}, {c, c - 4}, {c, c + 4}}) {
            b.set(p[0], 17, p[1], brick);
            for (int y = 16; y >= 13; y--) {
                b.set(p[0], y, p[1], "iron_chain[axis=y]");
            }
            b.set(p[0], 12, p[1], "soul_lantern[hanging=true]");
        }
        // ---- four corner towers with signal fires
        for (int[] t : new int[][] {{c - 9, c - 9}, {c + 9, c - 9}, {c - 9, c + 9}, {c + 9, c + 9}}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 0; y <= 14; y++) {
                        boolean corner = dx != 0 && dz != 0;
                        b.set(t[0] + dx, y, t[1] + dz, y % 5 == 4 ? chiseled : corner ? pillar : brick);
                    }
                }
            }
            b.set(t[0], 14, t[1], "hay_block");
            b.set(t[0], 15, t[1], fire + "[lit=true,signal_fire=true]");
            for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                b.set(t[0] + o[0], 15, t[1] + o[1], slab + "[type=bottom]");
            }
        }
        // ---- the altar in the middle
        for (int x = c - 1; x <= c + 1; x++) {
            for (int z = c - 1; z <= c + 1; z++) {
                b.set(x, 3, z, x != c && z != c ? "gold_block" : "crying_obsidian");
            }
        }
        for (int[] s : new int[][] {{c, c - 2, 0}, {c, c + 2, 1}, {c - 2, c, 2}, {c + 2, c, 3}}) {
            String facing = s[2] == 0 ? "south" : s[2] == 1 ? "north" : s[2] == 2 ? "east" : "west";
            b.set(s[0], 3, s[1], stairs + "[facing=" + facing + "]");
        }
        b.set(c, 4, c, "anvil[facing=north]");
        res.core = b.abs(c, 4, c);
        // ---- lava hearths and the forge tools
        for (int[] h : new int[][] {{c - 4, c - 4}, {c + 4, c - 4}, {c - 4, c + 4}, {c + 4, c + 4}}) {
            b.set(h[0], 2, h[1], "magma_block");
            b.set(h[0], 3, h[1], "lava_cauldron");
        }
        b.set(c - 2, 3, c - 5, "blast_furnace[facing=south]");
        b.set(c + 2, 3, c - 5, "smithing_table");
        b.set(c + 5, 3, c - 2, "grindstone[face=floor,facing=west]");
        // ---- the forge keeper's chest and the guards
        b.set(c, 3, c - 5, "chest[facing=south]");
        res.bigChests.add(b.abs(c, 3, c - 5));
        for (int[] g : new int[][] {{c - 5, c + 5}, {c + 5, c + 5}, {c - 5, c - 5}, {c + 5, c - 5}}) {
            res.guards.add(b.abs(g[0], 3, g[1]));
        }
    }

    /** A floating island of end stone under the End forge: wide on top, tapering down into a rough point. */
    private static void island(B b) {
        int c = 12;
        for (int k = 1; k <= 14; k++) {
            double r = 15.4 * Math.pow(1 - k / 15.0, 1.15) + (b.r.nextDouble() - 0.5) * 1.2;
            for (int x = 0; x < 25; x++) {
                for (int z = 0; z < 25; z++) {
                    double d = Math.hypot(x - c, z - c) + ((x * 7 + z * 13 + k * 3) % 5) * 0.25;
                    if (d <= r) {
                        b.set(x, -k, z, k > 10 && b.r.nextInt(4) == 0 ? "obsidian" : "end_stone");
                    }
                }
            }
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
