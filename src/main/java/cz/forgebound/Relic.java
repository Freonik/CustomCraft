package cz.forgebound;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.List;
import java.util.Locale;

/**
 * A relic: armor or an off-hand item with a special effect (section "relic" of an entry under "relics").
 *
 * <pre>
 * relic:
 *   type: STASIS        # STASIS, REVIVE, THORNS, HEARTSTEEL, THICK_HIDE, SOUL_VESSEL, STITCHGUARD
 *   slot: OFFHAND       # OFFHAND, HEAD, CHEST, LEGS, FEET - the relic only works in this slot
 *   cooldown: 120
 *   set: heartsteel     # optional: one piece of a 4-piece set (each piece 1/4 of the effect, all 4 = 5/4)
 *   description: [...]
 *   ...type specific numbers
 * </pre>
 */
public record Relic(Type type, Slot slot, int cooldown, List<String> description, WeaponAbilities.Params params,
                    String set) {

    public enum Type { STASIS, REVIVE, THORNS, HEARTSTEEL, THICK_HIDE, SOUL_VESSEL, STITCHGUARD }

    public enum Slot {
        OFFHAND(EquipmentSlot.OFF_HAND, EquipmentSlotGroup.OFFHAND, "Off-hand"),
        HEAD(EquipmentSlot.HEAD, EquipmentSlotGroup.HEAD, "Helmet"),
        CHEST(EquipmentSlot.CHEST, EquipmentSlotGroup.CHEST, "Chestplate"),
        LEGS(EquipmentSlot.LEGS, EquipmentSlotGroup.LEGS, "Leggings"),
        FEET(EquipmentSlot.FEET, EquipmentSlotGroup.FEET, "Boots");

        public final EquipmentSlot equipment;
        public final EquipmentSlotGroup group;
        public final String label;

        Slot(EquipmentSlot equipment, EquipmentSlotGroup group, String label) {
            this.equipment = equipment;
            this.group = group;
            this.label = label;
        }
    }

    public static Relic parse(ConfigurationSection s) {
        return parse(s, null);
    }

    /** defaultSet: the set from the bundled config, for configs written before sets existed. */
    public static Relic parse(ConfigurationSection s, String defaultSet) {
        if (s == null) {
            throw new IllegalArgumentException("missing 'relic' section");
        }
        Type type;
        Slot slot;
        try {
            type = Type.valueOf(s.getString("type", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown relic type '" + s.getString("type") + "'");
        }
        try {
            slot = Slot.valueOf(s.getString("slot", "OFFHAND").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown relic slot '" + s.getString("slot") + "'");
        }
        Object desc = s.get("description");
        List<String> description = desc instanceof List<?> list ? list.stream().map(String::valueOf).toList()
                : desc == null ? List.of() : List.of(String.valueOf(desc));
        String set = s.getString("set", defaultSet);
        return new Relic(type, slot, Math.max(0, s.getInt("cooldown", 0)), description,
                new WeaponAbilities.Params(s.getValues(false)), set == null || set.isBlank() ? null : set.toLowerCase(Locale.ROOT));
    }

    /** Lore lines describing the relic (added to the item automatically). */
    public List<String> loreLines() {
        java.util.ArrayList<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("&8» &d&lRELIC &8(" + slot.label + ")");
        for (int i = 0; i < description.size(); i++) {
            lore.add((i == 0 ? "&7" : "  &7") + description.get(i));
        }
        if (cooldown > 0) {
            lore.add("&8Cooldown: " + cooldown + "s");
        }
        if (set != null) {
            String name = Character.toUpperCase(set.charAt(0)) + set.substring(1).replace('_', ' ');
            lore.add("&6Set: &f" + name + " &8(each piece 1/4, all 4 pieces 5/4)");
        }
        return lore;
    }
}
