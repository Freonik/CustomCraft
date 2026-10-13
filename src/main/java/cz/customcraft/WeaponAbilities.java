package cz.customcraft;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Abilities of one weapon, loaded from its "abilities" section in config.yml.
 *
 * <pre>
 * abilities:
 *   passive:            # potion effects while the weapon is held in the main hand
 *     effects: { resistance: 0 }
 *     description: "Resistance I"
 *   arrow-trail: WITCH  # bows / crossbows only
 *   on-hit:             # triggered by a charged melee hit (or an arrow from this bow)
 *     - { type: ECHO, chance: 100, ... }
 *   active:             # abilities with a cooldown
 *     rewind: { type: REWIND, trigger: RIGHT_CLICK, cooldown: 30, ... }
 * </pre>
 */
public final class WeaponAbilities {

    /** What activates an ability. SWAP = the F key (swap hands). */
    public enum Trigger {
        RIGHT_CLICK("Right-click"),
        SHIFT_RIGHT_CLICK("Shift + Right-click"),
        SWAP("Press F"),
        SHIFT_SWAP("Shift + F");

        public final String label;

        Trigger(String label) {
            this.label = label;
        }
    }

    /** Active ability types (logic lives in AbilityManager). */
    public enum ActiveType {
        REWIND, RESONANCE, GRAVITY_ANCHOR, UPHEAVAL, MIRROR_SHOT, HEX_MARK, SOUL_LINK, HARVEST, CRESCENT, FRENZY
    }

    /** On-hit ability types. */
    public enum HitType {
        ECHO, CRUSH, RICOCHET, SOUL, LIFESTEAL, CLEAVE
    }

    /** Numeric ability parameters (damage, radius, ...) with defaults. */
    public static final class Params {
        private final Map<String, Object> values;

        Params(Map<String, Object> values) {
            this.values = values;
        }

        public double d(String key, double def) {
            Object value = values.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // fall back to the default
                }
            }
            return def;
        }

        public int i(String key, int def) {
            return (int) Math.round(d(key, def));
        }
    }

    public record Active(String key, ActiveType type, Trigger trigger, String name,
                         int cooldown, List<String> description, Params params) { }

    public record OnHit(HitType type, double chance, List<String> description, Params params) { }

    public record Passive(PotionEffectType type, int amplifier) { }

    public static final WeaponAbilities NONE = new WeaponAbilities();

    public final List<Active> actives = new ArrayList<>();
    public final List<OnHit> onHit = new ArrayList<>();
    public final List<Passive> passives = new ArrayList<>();
    public List<String> passiveDescription = List.of();
    public Particle arrowTrail;

    public boolean isEmpty() {
        return actives.isEmpty() && onHit.isEmpty() && passives.isEmpty() && arrowTrail == null;
    }

    public Active active(Trigger trigger) {
        for (Active active : actives) {
            if (active.trigger() == trigger) {
                return active;
            }
        }
        return null;
    }

    public OnHit onHit(HitType type) {
        for (OnHit hit : onHit) {
            if (hit.type() == type) {
                return hit;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- loading

    public static WeaponAbilities parse(ConfigurationSection s) {
        WeaponAbilities result = new WeaponAbilities();
        if (s == null) {
            return result;
        }

        ConfigurationSection passive = s.getConfigurationSection("passive");
        if (passive != null) {
            ConfigurationSection effects = passive.getConfigurationSection("effects");
            if (effects != null) {
                for (String name : effects.getKeys(false)) {
                    PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
                    if (type == null) {
                        throw new IllegalArgumentException("unknown effect '" + name + "'");
                    }
                    result.passives.add(new Passive(type, Math.max(0, effects.getInt(name))));
                }
            }
            result.passiveDescription = lines(passive.get("description"));
        }

        String trail = s.getString("arrow-trail");
        if (trail != null && !trail.isBlank()) {
            Particle particle;
            try {
                particle = Particle.valueOf(trail.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("unknown arrow-trail particle '" + trail + "'");
            }
            if (particle.getDataType() != Void.class) {
                throw new IllegalArgumentException("arrow-trail particle '" + trail + "' needs extra data, pick another one");
            }
            result.arrowTrail = particle;
        }

        for (Map<?, ?> raw : s.getMapList("on-hit")) {
            Map<String, Object> map = stringMap(raw);
            HitType type = parseEnum(HitType.class, map.get("type"), "on-hit type");
            Params params = new Params(map);
            result.onHit.add(new OnHit(type, params.d("chance", 100), lines(map.get("description")), params));
        }

        ConfigurationSection actives = s.getConfigurationSection("active");
        if (actives != null) {
            for (String key : actives.getKeys(false)) {
                ConfigurationSection a = actives.getConfigurationSection(key);
                if (a == null) {
                    continue;
                }
                ActiveType type = parseEnum(ActiveType.class, a.getString("type"), "active." + key + ".type");
                Trigger trigger = parseEnum(Trigger.class, a.getString("trigger", "SWAP"), "active." + key + ".trigger");
                if (result.active(trigger) != null) {
                    throw new IllegalArgumentException("two active abilities share the trigger " + trigger);
                }
                result.actives.add(new Active(key, type, trigger, a.getString("name", key),
                        Math.max(0, a.getInt("cooldown", 30)), lines(a.get("description")),
                        new Params(a.getValues(false))));
            }
        }
        return result;
    }

    /** Lore lines listing the abilities (added to the item automatically). */
    public List<String> loreLines() {
        List<String> lore = new ArrayList<>();
        if (isEmpty()) {
            return lore;
        }
        lore.add("");
        lore.add("&8» &6&lABILITIES");
        for (Active active : actives) {
            lore.add("&e" + active.trigger().label + " &8• " + active.name() + " &8(" + active.cooldown() + "s)");
            for (String line : active.description()) {
                lore.add("  &7" + line);
            }
        }
        for (OnHit hit : onHit) {
            String chance = hit.chance() >= 100 ? "" : " &8(" + trim(hit.chance()) + "%)";
            List<String> desc = hit.description();
            lore.add("&cOn hit" + chance + " &8• &7" + (desc.isEmpty() ? hit.type().name() : desc.get(0)));
            for (int i = 1; i < desc.size(); i++) {
                lore.add("  &7" + desc.get(i));
            }
        }
        if (!passives.isEmpty() || !passiveDescription.isEmpty()) {
            List<String> desc = passiveDescription;
            lore.add("&dPassive &8• &7" + (desc.isEmpty() ? "effects while held" : desc.get(0)));
            for (int i = 1; i < desc.size(); i++) {
                lore.add("  &7" + desc.get(i));
            }
        }
        return lore;
    }

    // ---------------------------------------------------------------- helpers

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static List<String> lines(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        raw.forEach((k, v) -> map.put(String.valueOf(k), v));
        return map;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, Object value, String what) {
        if (value == null) {
            throw new IllegalArgumentException("missing " + what);
        }
        try {
            return Enum.valueOf(type, String.valueOf(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown value '" + value + "' for " + what);
        }
    }
}
