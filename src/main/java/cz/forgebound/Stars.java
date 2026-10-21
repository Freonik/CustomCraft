package cz.forgebound;

/** Pure star math and text (no server needed, so it can be tested). Stars are kept in halves: 2 = ★, 10 = ★★★★★. */
final class Stars {

    private Stars() {
    }

    /** Damage multiplier: ★★★ = 1, every star above or below changes it by percentPerStar. */
    static double damageMultiplier(int halves, double percentPerStar) {
        return Math.max(0.1, 1 + percentPerStar / 100.0 * (halves / 2.0 - 3));
    }

    /** Cooldown multiplier: more stars = shorter cooldowns, by the same percentage. */
    static double cooldownMultiplier(int halves, double percentPerStar) {
        return Math.max(0.1, 1 - percentPerStar / 100.0 * (halves / 2.0 - 3));
    }

    /** "★★★✫☆" with colors: full stars gold, a half star yellow, missing stars grey. */
    static String starText(int halves) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            sb.append(halves >= 2 * i ? "&6★" : halves == 2 * i - 1 ? "&e✫" : "&8☆");
        }
        return sb.toString();
    }

    /** "3", "3½" */
    static String starCount(int halves) {
        return (halves / 2) + (halves % 2 == 1 ? "½" : "");
    }

    static int clamp(int halves) {
        return Math.max(2, Math.min(10, halves));
    }
}
