package cz.forgebound;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small pieces of plugin logic that don't need a running server. */
class LogicTest {

    @Test
    void bountyHunterBonus() {
        assertEquals(1.0, EnchantManager.bonus(0, 0.08), 1e-9);
        assertEquals(1.08, EnchantManager.bonus(1, 0.08), 1e-9);
        assertEquals(1.24, EnchantManager.bonus(3, 0.08), 1e-9);
        assertEquals(1.0, EnchantManager.bonus(3, -1), 1e-9, "negative config values must not lower damage");
    }

    @Test
    void itemRefsFromCommandArguments() {
        assertEquals("weapon:echo_blade", ForgeCommands.ref(Key.key("weapon", "echo_blade")));
        assertEquals("echo_blade", ForgeCommands.ref(Key.key("echo_blade")));
    }

    @Test
    void leaderboardNumbers() {
        assertEquals("12", TopManager.format(12.0));
        assertEquals("13", TopManager.format(12.6));
        assertTrue(TopManager.CATEGORIES.containsKey("bounty"), "/top without arguments shows the bounty board");
    }
}
