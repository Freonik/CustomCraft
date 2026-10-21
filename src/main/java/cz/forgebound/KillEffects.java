package cz.forgebound;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/** A little finisher when a legendary weapon gets a kill - each weapon has its own. */
final class KillEffects {

    private KillEffects() {
    }

    private static Particle.DustOptions dust(int r, int g, int b, float size) {
        return new Particle.DustOptions(Color.fromRGB(r, g, b), size);
    }

    static void play(String weaponId, LivingEntity victim) {
        World world = victim.getWorld();
        Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
        Location feet = victim.getLocation();
        switch (weaponId) {
            case "echo_blade" -> {          // the body leaves an echo that fades upward
                world.spawnParticle(Particle.SCULK_SOUL, at, 30, 0.3, 0.6, 0.3, 0.06);
                world.spawnParticle(Particle.SONIC_BOOM, at, 1, 0, 0, 0, 0);
                world.playSound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.8f, 1.6f);
            }
            case "graviton_hammer" -> {     // implosion into a tiny black hole
                world.spawnParticle(Particle.REVERSE_PORTAL, at, 120, 0.8, 0.8, 0.8, 0.6);
                world.spawnParticle(Particle.DUST, at, 30, 0.2, 0.2, 0.2, 0, dust(20, 10, 50, 2f));
                world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 0.5f);
            }
            case "hex_bow" -> {             // a hex spiral
                for (int i = 0; i < 40; i++) {
                    double a = i * 0.45;
                    world.spawnParticle(Particle.DUST, feet.clone().add(Math.cos(a) * 0.8, i * 0.06, Math.sin(a) * 0.8),
                            1, 0, 0, 0, 0, dust(80, 255, 140, 1.2f));
                }
                world.spawnParticle(Particle.WITCH, at, 40, 0.4, 0.6, 0.4, 0.2);
                world.playSound(at, Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.8f);
            }
            case "soulbinder" -> {          // the soul is pulled out
                world.spawnParticle(Particle.SOUL, at, 25, 0.3, 0.5, 0.3, 0.08);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, feet, 30, 0.5, 0.1, 0.5, 0.02);
                world.playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 2f, 0.7f);
            }
            case "hydra_edge" -> {          // venom burst
                world.spawnParticle(Particle.DUST, at, 60, 0.6, 0.6, 0.6, 0, dust(90, 200, 40, 1.4f));
                world.spawnParticle(Particle.ITEM_SLIME, at, 30, 0.4, 0.4, 0.4, 0.15);
                world.playSound(at, Sound.ENTITY_SLIME_SQUISH, 1f, 0.6f);
            }
            case "butcher_cleaver" -> {     // blood and bones
                world.spawnParticle(Particle.DUST, at, 70, 0.5, 0.6, 0.5, 0, dust(150, 10, 24, 1.6f));
                world.spawnParticle(Particle.BLOCK, at, 30, 0.3, 0.4, 0.3, 0, Material.BONE_BLOCK.createBlockData());
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f);
            }
            case "abyssal_maw" -> {         // dragged into the deep
                world.spawnParticle(Particle.BUBBLE_POP, at, 50, 0.5, 0.6, 0.5, 0.05);
                world.spawnParticle(Particle.SQUID_INK, at, 25, 0.4, 0.5, 0.4, 0.05);
                world.playSound(at, Sound.ENTITY_FROG_EAT, 1.5f, 0.5f);
            }
            case "clawline_pick" -> {       // shattered like stone
                world.spawnParticle(Particle.BLOCK, at, 60, 0.4, 0.6, 0.4, 0, Material.DEEPSLATE.createBlockData());
                world.playSound(at, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.6f);
            }
            case "silk_needle" -> {         // wrapped in a cocoon of silk
                for (int k = 0; k < 4; k++) {
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8;
                        world.spawnParticle(Particle.DUST, feet.clone().add(Math.cos(a) * 0.5, 0.3 + k * 0.45, Math.sin(a) * 0.5),
                                1, 0, 0, 0, 0, dust(245, 240, 230, 1f));
                    }
                }
                world.playSound(at, Sound.BLOCK_WOOL_PLACE, 1.4f, 0.6f);
            }
            case "rimebreaker" -> {         // frozen solid, then shattered
                world.spawnParticle(Particle.BLOCK, at, 60, 0.4, 0.6, 0.4, 0, Material.BLUE_ICE.createBlockData());
                world.spawnParticle(Particle.SNOWFLAKE, at, 50, 0.5, 0.7, 0.5, 0.08);
                world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.4f, 0.7f);
            }
            case "pinning_repeater" -> {    // chains snap
                world.spawnParticle(Particle.BLOCK, at, 30, 0.3, 0.5, 0.3, 0, Material.IRON_CHAIN.createBlockData());
                world.spawnParticle(Particle.CRIT, at, 30, 0.4, 0.5, 0.4, 0.4);
                world.playSound(at, Sound.BLOCK_CHAIN_BREAK, 1.4f, 0.7f);
            }
            case "the_last_act" -> {        // the curtain falls: gold confetti and a bow of applause
                world.spawnParticle(Particle.DUST, at, 50, 0.6, 0.8, 0.6, 0, dust(255, 205, 90, 1.2f));
                world.spawnParticle(Particle.DUST, at, 20, 0.5, 0.7, 0.5, 0, dust(220, 30, 50, 1.4f));
                world.spawnParticle(Particle.WAX_ON, at, 30, 0.6, 0.8, 0.6, 0.5);
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f, 0.6f);
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f, 0.9f);
            }
            case "valkyries_lance" -> {     // carried up to the sky
                for (int i = 0; i < 30; i++) {
                    world.spawnParticle(Particle.END_ROD, feet.clone().add(0, i * 0.35, 0), 1, 0.05, 0, 0.05, 0);
                }
                world.spawnParticle(Particle.DUST, at, 40, 0.5, 0.8, 0.5, 0, dust(255, 236, 170, 1.2f));
                world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
            }
            case "great_sea_trident" -> {   // a whirlpool
                for (int i = 0; i < 48; i++) {
                    double a = i * 0.4;
                    double r = 1.2 - i * 0.02;
                    world.spawnParticle(Particle.SPLASH, feet.clone().add(Math.cos(a) * r, i * 0.04, Math.sin(a) * r), 3, 0, 0, 0, 0);
                }
                world.spawnParticle(Particle.DUST, at, 30, 0.5, 0.5, 0.5, 0, dust(40, 140, 220, 1.2f));
                world.playSound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.8f);
            }
            case "knightfall" -> {          // a golden crown of light
                for (int i = 0; i < 20; i++) {
                    double a = i * Math.PI / 10;
                    world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 0.6, 1.2 + (i % 2) * 0.2, Math.sin(a) * 0.6),
                            1, 0, 0, 0, 0, dust(255, 200, 70, 1.3f));
                }
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 30, 0.4, 0.6, 0.4, 0.3);
                world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1.4f);
            }
            default -> {                    // any other custom weapon
                world.spawnParticle(Particle.ENCHANT, at, 60, 0.4, 0.6, 0.4, 1);
                world.spawnParticle(Particle.CRIT, at, 20, 0.3, 0.5, 0.3, 0.3);
                world.playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
            }
        }
    }
}
