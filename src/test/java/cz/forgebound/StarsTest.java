package cz.forgebound;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Weapon stars: the math, the text and the bundled costs. */
class StarsTest {

    @Test
    void threeStarsIsTheOldStrength() {
        assertEquals(1.0, Stars.damageMultiplier(6, 7.5), 1e-9);
        assertEquals(0.85, Stars.damageMultiplier(2, 7.5), 1e-9);
        assertEquals(1.15, Stars.damageMultiplier(10, 7.5), 1e-9);
        assertEquals(1.15, Stars.cooldownMultiplier(2, 7.5), 1e-9);
        assertEquals(0.85, Stars.cooldownMultiplier(10, 7.5), 1e-9);
    }

    @Test
    void halfStarsShowAndClamp() {
        assertEquals("&6★&6★&6★&e✫&8☆", Stars.starText(7));
        assertEquals("&6★&8☆&8☆&8☆&8☆", Stars.starText(2));
        assertEquals("3½", Stars.starCount(7));
        assertEquals(2, Stars.clamp(10 - 4 - 4 - 4), "never below one star");
        assertEquals(10, Stars.clamp(11));
    }

    @Test
    void everyStarHasACost() {
        Map<String, Object> config = TestFiles.yaml(TestFiles.RESOURCES.resolve("config.yml"));
        Map<String, Object> costs = TestFiles.map(TestFiles.map(config.get("stars")).get("costs"));
        for (int star = 2; star <= 5; star++) {
            Object raw = costs.containsKey(String.valueOf(star)) ? costs.get(String.valueOf(star)) : ((Map<?, ?>) costs).get(star);
            Map<String, Object> c = TestFiles.map(raw);
            assertNotNull(c, "stars.costs." + star + " is missing");
            List<Object> items = TestFiles.list(c.get("items"));
            assertFalse(items.isEmpty());
            assertTrue(items.size() <= 5, "at most 5 items per star");
            assertTrue(((Number) c.get("minutes")).doubleValue() > 0);
        }
    }
}
