package cz.forgebound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Version numbers and the server.properties rewrite of the auto-updater. */
class UpdaterTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://github.com/Freonik/Forgebound/releases/download/v2.7.0-build.70/Forgebound-pack.zip";

    @Test
    void versions() {
        assertEquals("2.7.0", Updater.version("v2.7.0-build.70"));
        assertTrue(Updater.compare("v2.7.0-build.1", "2.6.0") > 0);
        assertTrue(Updater.compare("2.6.0", "2.10.0") < 0, "numbers, not text");
        assertEquals(0, Updater.compare("v2.6.0-build.64", "2.6.0"));
    }

    @Test
    void pinsOurPack() {
        String props = "motd=Hi\nresource-pack=https\\://github.com/Freonik/Forgebound/releases/latest/download/Forgebound-pack.zip\n"
                + "resource-pack-sha1=ffffffffffffffffffffffffffffffffffffffff\nmax-players=20\n";
        String out = Updater.rewrite(props, "Freonik/Forgebound", URL, SHA);
        assertTrue(out.contains("resource-pack=" + URL.replace(":", "\\:")), out);
        assertTrue(out.contains("resource-pack-sha1=" + SHA), out);
        assertTrue(out.contains("motd=Hi") && out.contains("max-players=20"), "other lines stay");
        assertEquals(null, Updater.rewrite(out, "Freonik/Forgebound", URL, SHA), "nothing to change the second time");
    }

    @Test
    void addsMissingSha() {
        String out = Updater.rewrite("resource-pack=" + URL.replace(":", "\\:") + "\n", "Freonik/Forgebound", URL, SHA);
        assertTrue(out.contains("resource-pack-sha1=" + SHA), out);
    }

    @Test
    void leavesOtherPacksAlone() {
        assertEquals(null, Updater.rewrite("resource-pack=https\\://example.com/my.zip\nresource-pack-sha1=\n",
                "Freonik/Forgebound", URL, SHA));
        assertEquals(null, Updater.rewrite("motd=no pack\n", "Freonik/Forgebound", URL, SHA));
    }
}
