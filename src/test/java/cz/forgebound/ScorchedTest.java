package cz.forgebound;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Scorched Lands: where they are, the ruins, and the bundled datapack. */
class ScorchedTest {

    @Test
    void aboutThreePercentOfTheWorld() {
        for (long seed : new long[] {1, 42, -9_000_000_000L}) {
            ScorchedNoise n = new ScorchedNoise(seed, 800, 0.03);
            int in = 0;
            int total = 0;
            for (int x = -12_000; x < 12_000; x += 48) {
                for (int z = -12_000; z < 12_000; z += 48) {
                    total++;
                    if (n.inside(x, z)) {
                        in++;
                    }
                }
            }
            double share = (double) in / total;
            assertTrue(share > 0.015 && share < 0.05, "seed " + seed + ": " + share);
        }
    }

    @Test
    void sameSeedSameLand() {
        ScorchedNoise a = new ScorchedNoise(77, 800, 0.03);
        ScorchedNoise b = new ScorchedNoise(77, 800, 0.03);
        assertEquals(a.value(1234, -5678), b.value(1234, -5678), 0);
    }

    @Test
    void nearestIsReallyInside() {
        ScorchedNoise n = new ScorchedNoise(5, 800, 0.03);
        int[] p = n.nearest(0, 0, 30_000);
        assertNotNull(p);
        assertEquals(1.0, n.strength(p[0], p[1]), 1e-9, "/forge scorched must point deep inside, not on the edge");
    }

    @Test
    void everyRuinBuildsWithLootAndValidIds() {
        for (ForgeRuins.Type type : ForgeRuins.Type.values()) {
            for (int seed = 0; seed < 20; seed++) {
                Map<String, String> blocks = new HashMap<>();
                ForgeRuins.Canvas c = new ForgeRuins.Canvas() {
                    @Override
                    public void set(int x, int y, int z, String block) {
                        blocks.put(x + "," + y + "," + z, block);
                    }

                    @Override
                    public String get(int x, int y, int z) {
                        return y < 64 ? "minecraft:stone" : blocks.getOrDefault(x + "," + y + "," + z, "minecraft:air");
                    }
                };
                ForgeRuins.Result r = ForgeRuins.build(type, c, 100, 64, 200, new SplittableRandom(seed));
                assertEquals(1, r.bigChests.size() + r.smallChests.size(), type + " should have one chest");
                for (int[] chest : r.bigChests.isEmpty() ? r.smallChests : r.bigChests) {
                    assertTrue(blocks.get(chest[0] + "," + chest[1] + "," + chest[2]).startsWith("minecraft:chest"));
                }
                for (Map.Entry<String, String> e : blocks.entrySet()) {
                    String id = e.getValue();
                    assertTrue(id.matches("minecraft:[a-z_]+(\\[[a-z_]+=[a-z_]+(,[a-z_]+=[a-z_]+)*])?"), type + ": bad block " + id);
                    String[] xyz = e.getKey().split(",");
                    int x = Integer.parseInt(xyz[0]) - 100;
                    int z = Integer.parseInt(xyz[2]) - 200;
                    assertTrue(x >= 0 && x < type.width && z >= 0 && z < type.depth, type + " builds outside its footprint");
                }
            }
        }
    }

    @Test
    void datapackFiles() {
        Path dp = TestFiles.RESOURCES.resolve("datapack");
        Map<String, Object> pack = TestFiles.map(TestFiles.yaml(dp.resolve("pack.mcmeta")).get("pack"));
        assertNotNull(pack.get("min_format"));
        Map<String, Object> biome = TestFiles.yaml(dp.resolve("data/forgebound/worldgen/biome/scorched_lands.json"));
        for (String key : List.of("attributes", "carvers", "effects", "features", "spawners", "temperature", "downfall")) {
            assertTrue(biome.containsKey(key), "biome is missing " + key);
        }
        assertEquals(11, TestFiles.list(biome.get("features")).size(), "a biome has 11 feature steps");
        for (String table : List.of("forge_ruin", "forge_ruin_small")) {
            Map<String, Object> loot = TestFiles.yaml(dp.resolve("data/forgebound/loot_table/chests/" + table + ".json"));
            assertFalse(TestFiles.list(loot.get("pools")).isEmpty(), table + " has no pools");
        }
    }
}
