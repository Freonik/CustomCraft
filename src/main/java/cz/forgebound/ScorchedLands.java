package cz.forgebound;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Scorched Lands: a rare burnt biome (from the bundled datapack) painted onto NEW chunks of the overworld,
 * with ruined forges, workshops, slag heaps and watchtowers in it. Already generated chunks are never touched.
 * Runs as a block populator, so it can run on the server's world generation threads: no Bukkit main-thread calls
 * in there, only the LimitedRegion it's given.
 */
public final class ScorchedLands implements Listener {

    static final Key BIOME = Key.key("forgebound", "scorched_lands");
    static final NamespacedKey LOOT_BIG = new NamespacedKey("forgebound", "chests/forge_ruin");
    static final NamespacedKey LOOT_SMALL = new NamespacedKey("forgebound", "chests/forge_ruin_small");

    private final Forgebound plugin;
    private final NamespacedKey guardKey;
    private final Populator populator = new Populator();
    private final NetherPopulator netherPopulator = new NetherPopulator();
    private final Map<Long, ScorchedNoise> noises = new ConcurrentHashMap<>();
    private final Map<String, BlockData> blockCache = new ConcurrentHashMap<>();
    private volatile Settings settings;
    private volatile Biome biome;
    private volatile LootTable lootBig;
    private volatile LootTable lootSmall;

    record Settings(boolean enabled, List<String> worlds, double rarity, double zoneSize, boolean ruins,
                    double ruinChance, boolean guards, double ancientChance, boolean nether, int netherCell,
                    double netherChance) { }

    public ScorchedLands(Forgebound plugin) {
        this.plugin = plugin;
        this.guardKey = Forgebound.key("ruin_guard");
        load();
    }

    /** (Re)reads the config. Rarity / size changes only affect chunks generated afterwards. */
    public void load() {
        var c = plugin.getConfig();
        settings = new Settings(c.getBoolean("scorched-lands.enabled", true), c.getStringList("scorched-lands.worlds"),
                c.getDouble("scorched-lands.rarity", 0.03), c.getDouble("scorched-lands.zone-size", 800),
                c.getBoolean("scorched-lands.ruins", true), c.getDouble("scorched-lands.ruin-chance", 0.35),
                c.getBoolean("scorched-lands.guards", true),
                c.getDouble("ancient-forge.scorched-chance", 0.12), c.getBoolean("ancient-forge.nether", true),
                Math.max(4, c.getInt("ancient-forge.nether-cell-chunks", 16)), c.getDouble("ancient-forge.nether-chance", 0.3));
        noises.clear();
        biome = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(BIOME);
        if (biome == null) {
            plugin.getLogger().warning("Scorched Lands: the biome from the Forgebound datapack is missing, nothing will generate.");
        }
        lootBig = Bukkit.getLootTable(LOOT_BIG);
        lootSmall = Bukkit.getLootTable(LOOT_SMALL);
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            attach(world);
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attach(event.getWorld());
    }

    private boolean wanted(World world) {
        Settings s = settings;
        return s.worlds().isEmpty() ? world.getEnvironment() == World.Environment.NORMAL : s.worlds().contains(world.getName());
    }

    private void attach(World world) {
        if (wanted(world) && !world.getPopulators().contains(populator)) {
            world.getPopulators().add(populator);
        }
        if (world.getEnvironment() == World.Environment.NETHER && !world.getPopulators().contains(netherPopulator)) {
            world.getPopulators().add(netherPopulator);
        }
    }

    ScorchedNoise noise(long seed) {
        Settings s = settings;
        return noises.computeIfAbsent(seed, k -> new ScorchedNoise(k, s.zoneSize(), s.rarity()));
    }

    private BlockData data(String block) {
        return blockCache.computeIfAbsent(block, Bukkit::createBlockData);
    }

    // ================================================================ generation

    private final class Populator extends BlockPopulator {
        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            Settings s = settings;
            Biome b = biome;
            if (!s.enabled() || b == null) {
                return;
            }
            try {
                scorch(info, chunkX, chunkZ, region, s, b);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Scorched Lands at chunk " + chunkX + "," + chunkZ + ": " + ex);
            }
        }
    }

    private void scorch(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region, Settings s, Biome b) {
        ScorchedNoise noise = noise(info.getSeed());
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        // quick skip: nothing near this chunk
        boolean near = false;
        for (int[] p : new int[][] {{0, 0}, {15, 0}, {0, 15}, {15, 15}, {8, 8}}) {
            if (noise.value(bx + p[0], bz + p[1]) > noise.threshold - 0.02) {
                near = true;
                break;
            }
        }
        if (!near) {
            return;
        }
        SplittableRandom rnd = new SplittableRandom(info.getSeed() ^ (chunkX * 341873128712L + chunkZ * 132897987541L));
        ScorchedNoise patches = noise(info.getSeed() + 7);         // small patches of gravel / blackstone / tuff
        int[][] surface = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double strength = noise.strength(bx + x, bz + z);
                surface[x][z] = Integer.MIN_VALUE;
                if (strength <= 0 || strength < 1 && rnd.nextDouble() > strength) {
                    continue;
                }
                surface[x][z] = column(info, region, bx + x, bz + z, rnd, patches, b);
            }
        }
        if (s.ruins()) {
            ruins(info, chunkX, chunkZ, region, s, noise, surface);
        }
    }

    /** Burns one block column. Returns the surface height (MIN_VALUE when it's water or nothing was found). */
    private int column(WorldInfo info, LimitedRegion r, int x, int z, SplittableRandom rnd, ScorchedNoise patches, Biome b) {
        int min = info.getMinHeight();
        int surface = Integer.MIN_VALUE;
        Material top = Material.AIR;
        for (int y = info.getMaxHeight() - 1; y > min; y--) {
            if (!r.isInRegion(x, y, z)) {
                return Integer.MIN_VALUE;
            }
            Material m = r.getType(x, y, z);
            if (m.isAir() || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || !m.isSolid() && m != Material.WATER && m != Material.LAVA) {
                continue;
            }
            surface = y;
            top = m;
            break;
        }
        if (surface == Integer.MIN_VALUE || top == Material.WATER || top == Material.LAVA || top == Material.ICE) {
            return Integer.MIN_VALUE;
        }
        // above the ground: no leaves, no plants, trees burnt down to stumps
        int stump = 1 + rnd.nextInt(4);
        for (int y = surface + 1; y < Math.min(info.getMaxHeight(), surface + 40); y++) {
            Material m = r.getType(x, y, z);
            if (m.isAir()) {
                continue;
            }
            if (Tag.LOGS.isTagged(m)) {
                if (y - surface <= stump) {
                    BlockData basalt = Material.BASALT.createBlockData();
                    if (r.getBlockData(x, y, z) instanceof Orientable o && basalt instanceof Orientable ob) {
                        ob.setAxis(o.getAxis());
                    }
                    r.setBlockData(x, y, z, basalt);
                } else {
                    r.setType(x, y, z, Material.AIR);
                }
            } else if (Tag.LEAVES.isTagged(m) || !m.isSolid() && m != Material.WATER && m != Material.LAVA) {
                r.setType(x, y, z, Material.AIR);
            }
        }
        // the ground itself
        if (Tag.DIRT.isTagged(top) || Tag.SAND.isTagged(top) || top == Material.SNOW_BLOCK || top == Material.MUD
                || top == Material.MOSS_BLOCK || top == Material.CLAY || top == Material.GRAVEL) {
            double v = patches.value(x * 18.0, z * 18.0);
            Material ground = v > 0.45 ? Material.BLACKSTONE : v > 0.22 ? Material.GRAVEL : v < -0.45 ? Material.TUFF : Material.COARSE_DIRT;
            if (rnd.nextInt(80) == 0) {
                ground = Material.MAGMA_BLOCK;
            }
            r.setType(x, surface, z, ground);
            for (int y = surface - 1; y >= surface - 3 && y > min; y--) {
                if (Tag.DIRT.isTagged(r.getType(x, y, z))) {
                    r.setType(x, y, z, Material.COARSE_DIRT);
                }
            }
            if (ground == Material.COARSE_DIRT && rnd.nextInt(30) == 0 && r.getType(x, surface + 1, z).isAir()) {
                r.setType(x, surface + 1, z, Material.DEAD_BUSH);
            }
        } else if (Tag.BASE_STONE_OVERWORLD.isTagged(top) && patches.value(x * 18.0, z * 18.0) > 0.4) {
            r.setType(x, surface, z, Material.BLACKSTONE);
        }
        // the biome, from a little underground up into the sky (biomes are stored per 4x4x4 cell)
        for (int y = Math.max(min, surface - 12); y < Math.min(info.getMaxHeight(), surface + 48); y += 4) {
            r.setBiome(x, y, z, b);
        }
        return surface;
    }

    /** One possible ruin per 4x4-chunk cell, in a random chunk of the cell, only deep inside the biome. */
    private void ruins(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region, Settings s, ScorchedNoise noise,
                       int[][] surface) {
        int cellX = Math.floorDiv(chunkX, 4);
        int cellZ = Math.floorDiv(chunkZ, 4);
        SplittableRandom cell = new SplittableRandom(info.getSeed() * 31 + cellX * 73428767L + cellZ * 912931L);
        int pickX = cell.nextInt(4);
        int pickZ = cell.nextInt(4);
        if (Math.floorMod(chunkX, 4) != pickX || Math.floorMod(chunkZ, 4) != pickZ || cell.nextDouble() >= s.ruinChance()) {
            return;
        }
        if (noise.strength((chunkX << 4) + 8, (chunkZ << 4) + 8) < 1) {
            return;
        }
        boolean ancient = cell.nextDouble() < s.ancientChance();
        ForgeRuins.Type type = ancient ? ForgeRuins.Type.ANCIENT : ForgeRuins.pick(cell);
        int ox = 1 + cell.nextInt(Math.max(1, 15 - type.width));
        int oz = 1 + cell.nextInt(Math.max(1, 15 - type.depth));
        // flat enough, and all of it burnt (no water)?
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        long sum = 0;
        for (int x = ox; x < ox + type.width; x++) {
            for (int z = oz; z < oz + type.depth; z++) {
                int h = surface[x][z];
                if (h == Integer.MIN_VALUE) {
                    return;
                }
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                sum += h;
            }
        }
        if (hi - lo > 4) {
            return;
        }
        int y0 = (int) Math.round((double) sum / (type.width * type.depth));
        int bx = (chunkX << 4) + ox;
        int bz = (chunkZ << 4) + oz;
        ForgeRuins.Result result = ForgeRuins.build(type, canvas(region), bx, y0, bz, cell);
        finish(info, region, result, s, cell, false);
    }

    private ForgeRuins.Canvas canvas(LimitedRegion region) {
        return new ForgeRuins.Canvas() {
            @Override
            public void set(int x, int y, int z, String block) {
                if (region.isInRegion(x, y, z)) {
                    region.setBlockData(x, y, z, data(block));
                }
            }

            @Override
            public String get(int x, int y, int z) {
                return region.isInRegion(x, y, z) ? region.getType(x, y, z).getKey().toString() : "minecraft:stone";
            }
        };
    }

    /** Loot, guards and (for an Ancient Forge) registering the forge. */
    private void finish(WorldInfo info, LimitedRegion region, ForgeRuins.Result result, Settings s, SplittableRandom cell,
                        boolean nether) {
        long seed = cell.nextLong();
        for (int[] p : result.bigChests) {
            loot(region, p, lootBig, seed++);
        }
        for (int[] p : result.smallChests) {
            loot(region, p, lootSmall, seed++);
        }
        if (s.guards()) {
            for (int[] p : result.guards) {
                guard(region, p, nether);
            }
        }
        if (result.core != null && plugin.stars() != null) {
            plugin.stars().found(info.getName(), result.core[0], result.core[1], result.core[2]);
        }
    }

    // ================================================================ Ancient Forges in the Nether

    private final class NetherPopulator extends BlockPopulator {
        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            Settings s = settings;
            if (!s.nether() || s.netherChance() <= 0) {
                return;
            }
            try {
                netherForge(info, chunkX, chunkZ, region, s);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Ancient Forge at nether chunk " + chunkX + "," + chunkZ + ": " + ex);
            }
        }
    }

    /** One possible Ancient Forge per cell of chunks, on an open floor between 32 and 100. */
    private void netherForge(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region, Settings s) {
        int size = s.netherCell();
        int cellX = Math.floorDiv(chunkX, size);
        int cellZ = Math.floorDiv(chunkZ, size);
        SplittableRandom cell = new SplittableRandom(info.getSeed() * 17 + cellX * 50331653L + cellZ * 1610612741L + 99);
        int pickX = 1 + cell.nextInt(size - 2);
        int pickZ = 1 + cell.nextInt(size - 2);
        if (Math.floorMod(chunkX, size) != pickX || Math.floorMod(chunkZ, size) != pickZ || cell.nextDouble() >= s.netherChance()) {
            return;
        }
        int bx = (chunkX << 4) + 1;
        int bz = (chunkZ << 4) + 1;
        int n = ForgeRuins.Type.ANCIENT_NETHER.width;
        // the lowest open floor (solid, with 8 free blocks above) in the middle of the footprint
        int cx = bx + n / 2;
        int cz = bz + n / 2;
        int floor = Integer.MIN_VALUE;
        for (int y = 32; y <= 100; y++) {
            if (!region.isInRegion(cx, y, cz) || !region.getType(cx, y, cz).isSolid()) {
                continue;
            }
            boolean open = true;
            for (int up = 1; up <= 8 && open; up++) {
                open = region.isInRegion(cx, y + up, cz) && region.getType(cx, y + up, cz).isAir();
            }
            if (open) {
                floor = y;
                break;
            }
        }
        if (floor == Integer.MIN_VALUE) {
            return;
        }
        // most of the footprint needs ground close below the floor (no forge hanging over a lava sea)
        int grounded = 0;
        int total = 0;
        for (int x = bx; x < bx + n; x += 2) {
            for (int z = bz; z < bz + n; z += 2) {
                total++;
                for (int y = floor; y >= floor - 4; y--) {
                    if (region.isInRegion(x, y, z) && region.getType(x, y, z).isSolid()) {
                        grounded++;
                        break;
                    }
                }
            }
        }
        if (grounded * 10 < total * 7) {
            return;
        }
        ForgeRuins.Result result = ForgeRuins.build(ForgeRuins.Type.ANCIENT_NETHER, canvas(region), bx, floor, bz, cell);
        finish(info, region, result, s, cell, true);
    }

    private void loot(LimitedRegion region, int[] p, LootTable table, long seed) {
        if (table == null || !region.isInRegion(p[0], p[1], p[2])) {
            return;
        }
        BlockState state = region.getBlockState(p[0], p[1], p[2]);
        if (state instanceof Chest chest) {
            chest.setLootTable(table, seed);
            region.setBlockState(p[0], p[1], p[2], chest);
        }
    }

    /** A ruin guard: a husk in battered armor that doesn't despawn (a wither skeleton in the Nether). */
    private void guard(LimitedRegion region, int[] p, boolean nether) {
        if (!region.isInRegion(p[0], p[1], p[2]) || !region.getType(p[0], p[1], p[2]).isAir()) {
            return;
        }
        Location at = new Location(region.getWorld(), p[0] + 0.5, p[1], p[2] + 0.5);
        if (nether) {
            region.spawn(at, org.bukkit.entity.WitherSkeleton.class, ws -> {
                ws.setRemoveWhenFarAway(false);
                ws.setPersistent(true);
                ws.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);
            });
            return;
        }
        region.spawn(at, Husk.class, husk -> {
            husk.setRemoveWhenFarAway(false);
            husk.setPersistent(true);
            husk.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);
            var eq = husk.getEquipment();
            eq.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            eq.setItemInMainHand(new ItemStack(Material.IRON_AXE));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                eq.setDropChance(slot, 0.05f);
            }
        });
    }

    // ================================================================ extra loot

    /** Ruin chests can also hold Bounty Tokens and, rarely, a legendary material. */
    @EventHandler
    public void onLoot(LootGenerateEvent event) {
        if (event.getLootTable() == null || !"forgebound".equals(event.getLootTable().getKey().getNamespace())
                || plugin.bounty() == null) {
            return;
        }
        boolean big = event.getLootTable().getKey().equals(LOOT_BIG);
        var c = plugin.getConfig();
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        if (plugin.shop() != null && plugin.shop().enabled()
                && r.nextDouble() * 100 < c.getDouble("scorched-lands.loot.token-chance", big ? 35 : 15)) {
            int min = c.getInt("scorched-lands.loot.tokens-min", 1);
            int max = Math.max(min, c.getInt("scorched-lands.loot.tokens-max", big ? 3 : 2));
            event.getLoot().add(plugin.bounty().token(min + r.nextInt(max - min + 1)));
        }
        if (big && r.nextDouble() * 100 < c.getDouble("scorched-lands.loot.material-chance", 3)) {
            List<ItemManager.Entry> mats = plugin.bounty().enabledMaterials();
            if (!mats.isEmpty()) {
                event.getLoot().add(mats.get(r.nextInt(mats.size())).item.clone());
            }
        }
    }

    // ================================================================ /forge scorched

    /** Tells an admin where the nearest Scorched Lands are (also in chunks nobody has generated yet). */
    public void locate(CommandSender sender) {
        // from the console: searched from 0,0 of the main world
        World w = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
        if (!wanted(w) || !settings.enabled()) {
            sender.sendMessage(ItemManager.color("&7The Scorched Lands don't generate in this world."));
            return;
        }
        Location at = sender instanceof Player p ? p.getLocation() : new Location(w, 0, 0, 0);
        int[] found = noise(w.getSeed()).nearest(at.getBlockX(), at.getBlockZ(), 30_000);
        if (found == null) {
            sender.sendMessage(ItemManager.color("&7No Scorched Lands within 30 000 blocks."));
            return;
        }
        int dist = (int) Math.hypot(found[0] - at.getBlockX(), found[1] - at.getBlockZ());
        sender.sendMessage(ItemManager.color("&6☀ Scorched Lands &7at &f" + found[0] + " ~ " + found[1]
                        + " &8(" + dist + " blocks) &e[tp]")
                .clickEvent(ClickEvent.suggestCommand("/tp @s " + found[0] + " 200 " + found[1])));
        sender.sendMessage(ItemManager.color("&8Only chunks generated from now on are scorched; explored land stays as it is."));
    }
}
