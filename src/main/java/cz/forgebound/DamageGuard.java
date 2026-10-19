package cz.forgebound;

/**
 * Marks damage dealt by the plugin itself (abilities, Hydra splash, Thorns reflect),
 * so it doesn't trigger more on-hit effects and cause chain reactions.
 */
public final class DamageGuard {

    private static int depth;

    private DamageGuard() {
    }

    public static boolean active() {
        return depth > 0;
    }

    public static void run(Runnable action) {
        depth++;
        try {
            action.run();
        } finally {
            depth--;
        }
    }
}
