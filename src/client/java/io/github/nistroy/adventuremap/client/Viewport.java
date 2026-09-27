package io.github.nistroy.adventuremap.client;

import io.github.nistroy.adventuremap.map.MapDefinition;

/**
 * Passe du repère de la carte (1000 × 600 à l'écran) aux pixels du GUI, et inversement.
 * {@code scrollY} = ligne du repère affichée en haut : une région plus haute que 600 défile.
 */
record Viewport(int left, int top, int width, int height, double scrollY) {
    static Viewport fit(int left, int top, int maxWidth, int maxHeight) {
        int width = Math.min(maxWidth, maxHeight * MapDefinition.WIDTH / MapDefinition.HEIGHT);
        int height = width * MapDefinition.HEIGHT / MapDefinition.WIDTH;
        return new Viewport(left + (maxWidth - width) / 2, top + (maxHeight - height) / 2, width, height, 0);
    }

    Viewport scrolled(double scrollY) {
        return new Viewport(left, top, width, height, scrollY);
    }

    double scale() {
        return (double) width / MapDefinition.WIDTH;
    }

    int x(double virtualX) {
        return left + (int) Math.round(virtualX * scale());
    }

    int y(double virtualY) {
        return top + (int) Math.round((virtualY - scrollY) * scale());
    }

    double virtualX(double screenX) {
        return (screenX - left) / scale();
    }

    double virtualY(double screenY) {
        return (screenY - top) / scale() + scrollY;
    }

    boolean contains(double screenX, double screenY) {
        return screenX >= left && screenX < left + width && screenY >= top && screenY < top + height;
    }
}
