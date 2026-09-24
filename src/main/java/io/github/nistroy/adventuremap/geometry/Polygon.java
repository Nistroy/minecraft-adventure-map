package io.github.nistroy.adventuremap.geometry;

import java.util.Arrays;
import java.util.List;

/** Remplissage par lignes : le GUI de Minecraft ne sait tracer que des rectangles. */
public final class Polygon {
    private Polygon() {}

    /** Intervalles [x0, x1, x2, x3…] couverts par le polygone sur la ligne {@code y} (règle pair-impair). */
    public static double[] spans(List<Point> polygon, double y) {
        double[] xs = new double[polygon.size()];
        int count = 0;
        for (int i = 0, n = polygon.size(); i < n; i++) {
            Point a = polygon.get(i), b = polygon.get((i + 1) % n);
            // Demi-ouvert en haut : un sommet partagé par deux arêtes n'est compté qu'une fois.
            if ((a.y() <= y && b.y() > y) || (b.y() <= y && a.y() > y)) {
                xs[count++] = a.x() + (y - a.y()) / (b.y() - a.y()) * (b.x() - a.x());
            }
        }
        double[] result = Arrays.copyOf(xs, count);
        Arrays.sort(result);
        return result;
    }

    public static boolean contains(List<Point> polygon, double x, double y) {
        double[] spans = spans(polygon, y);
        for (int i = 0; i + 1 < spans.length; i += 2) {
            if (x >= spans[i] && x <= spans[i + 1]) return true;
        }
        return false;
    }
}
