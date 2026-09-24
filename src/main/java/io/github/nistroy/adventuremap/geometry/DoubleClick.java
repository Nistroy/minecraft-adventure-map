package io.github.nistroy.adventuremap.geometry;

import java.util.Objects;

/** Double-clic sur une même cible (le GUI de Minecraft ne donne que des clics simples). */
public final class DoubleClick {
    private final long delayMillis;
    private Object lastTarget;
    private long lastTime;

    public DoubleClick(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    /** Vrai si ce clic complète un double-clic ; un troisième clic en recommence un nouveau. */
    public boolean click(Object target, long nowMillis) {
        boolean isDouble = lastTarget != null && Objects.equals(target, lastTarget) && nowMillis - lastTime <= delayMillis;
        lastTarget = isDouble ? null : target;
        lastTime = nowMillis;
        return isDouble;
    }
}
