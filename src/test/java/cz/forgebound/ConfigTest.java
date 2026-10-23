package cz.forgebound;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled config.yml and paper-plugin.yml are complete and consistent. */
class ConfigTest {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    private final Map<String, Object> config = TestFiles.yaml(TestFiles.RESOURCES.resolve("config.yml"));

    private Map<String, Map<String, Object>> section(String name) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        TestFiles.map(config.get(name)).forEach((k, v) -> out.put(k, TestFiles.map(v)));
        return out;
    }

    private Set<String> refs() {
        Set<String> refs = new HashSet<>();
        section("weapons").keySet().forEach(id -> refs.add("weapon:" + id));
        section("relics").keySet().forEach(id -> refs.add("relic:" + id));
        section("materials").keySet().forEach(id -> refs.add("material:" + id));
        return refs;
    }

    @Test
    void hasAllItemSections() {
        assertFalse(section("weapons").isEmpty(), "no weapons");
        assertFalse(section("relics").isEmpty(), "no relics");
        assertFalse(section("materials").isEmpty(), "no materials");
    }

    @Test
    void everyItemHasIdNameMaterialAndModel() {
        for (String kind : List.of("weapons", "relics", "materials")) {
            section(kind).forEach((id, s) -> {
                String where = kind + "." + id;
                assertTrue(ID.matcher(id).matches(), where + ": id may only contain a-z, 0-9 and _");
                assertNotNull(s.get("name"), where + ": missing name");
                assertNotNull(s.get("material"), where + ": missing material");
                assertTrue(s.get("custom-model-data") instanceof Integer, where + ": missing custom-model-data");
            });
        }
    }

    @Test
    void customModelDataIsUniquePerBaseItem() {
        Map<String, String> seen = new HashMap<>();
        for (String kind : List.of("weapons", "relics", "materials")) {
            section(kind).forEach((id, s) -> {
                String key = String.valueOf(s.get("material")).toUpperCase(Locale.ROOT) + "#" + s.get("custom-model-data");
                String before = seen.put(key, kind + "." + id);
                assertEquals(null, before, key + " is used by both " + before + " and " + kind + "." + id);
            });
        }
    }

    @Test
    void forgeCostsUseKnownItems() {
        Set<String> refs = refs();
        for (String kind : List.of("weapons", "relics")) {
            section(kind).forEach((id, s) -> {
                if (Boolean.TRUE.equals(s.get("mythic"))) {
                    assertTrue(TestFiles.list(s.get("forge")).isEmpty(), kind + "." + id + ": mythic weapons aren't forged");
                    return;
                }
                List<Object> cost = TestFiles.list(s.get("forge"));
                assertFalse(cost.isEmpty(), kind + "." + id + ": no forge cost");
                assertTrue(cost.size() <= 5, kind + "." + id + ": more than 5 different forge materials");
                for (Object o : cost) {
                    Map<String, Object> entry = TestFiles.map(o);
                    String item = String.valueOf(entry.get("item")).toLowerCase(Locale.ROOT);
                    if (item.contains(":") && !item.startsWith("minecraft:")) {
                        assertTrue(refs.contains(item), kind + "." + id + ": unknown forge material " + item);
                    }
                    Object amount = entry.get("amount");
                    assertTrue(amount == null || amount instanceof Integer n && n > 0,
                            kind + "." + id + ": bad amount for " + item);
                }
            });
        }
    }

    @Test
    void relicsHaveARelicSection() {
        section("relics").forEach((id, s) -> assertFalse(TestFiles.map(s.get("relic")).isEmpty(),
                "relics." + id + ": missing the relic section"));
    }

    @Test
    void newSectionsArePresent() {
        for (String path : List.of("dialogs", "enchantments", "items", "teams", "recon", "season", "economy", "events")) {
            assertFalse(TestFiles.map(config.get(path)).isEmpty(), "config.yml is missing the " + path + " section");
        }
        Map<String, Object> enchantments = TestFiles.map(config.get("enchantments"));
        assertFalse(TestFiles.map(enchantments.get("forge-touch")).isEmpty());
        assertFalse(TestFiles.map(enchantments.get("bounty-hunter")).isEmpty());
    }

    @Test
    void pluginDescription() {
        Map<String, Object> meta = TestFiles.yaml(TestFiles.RESOURCES.resolve("paper-plugin.yml"));
        assertEquals("Forgebound", meta.get("name"));
        for (String key : List.of("main", "bootstrapper")) {
            String cls = String.valueOf(meta.get(key));
            assertNotNull(getClass().getClassLoader().getResource(cls.replace('.', '/') + ".class"),
                    key + " class " + cls + " doesn't exist");
        }
        Map<String, Object> permissions = TestFiles.map(meta.get("permissions"));
        for (String perm : List.of("forgebound.use", "forgebound.admin", "forgebound.nocooldown")) {
            assertTrue(permissions.containsKey(perm), "missing permission " + perm);
        }
        // PlaceholderAPI must be on our classpath for the expansion class
        Map<String, Object> papi = TestFiles.map(TestFiles.map(TestFiles.map(meta.get("dependencies")).get("server")).get("PlaceholderAPI"));
        assertEquals(Boolean.TRUE, papi.get("join-classpath"));
        assertEquals(Boolean.FALSE, papi.get("required"));
    }

    @Test
    void messagesAreStrings() {
        List<String> bad = new ArrayList<>();
        TestFiles.map(config.get("messages")).forEach((k, v) -> {
            if (!(v instanceof String)) {
                bad.add(k);
            }
        });
        assertTrue(bad.isEmpty(), "messages that aren't text: " + bad);
    }
}
