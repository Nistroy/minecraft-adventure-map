package io.github.nistroy.adventuremap.geometry;

import java.util.ArrayList;
import java.util.List;

/** Contour de région « dessiné à la main » : ellipse bosselée lissée, même forme pour une même graine. */
public final class Blob {
    private static final int CORNERS = 14;
    private static final int STEPS = 4;

    private Blob() {}

    public static List<Point> outline(double cx, double cy, double radius, long seed) {
        java.util.Random random = new java.util.Random(seed);
        Point[] corners = new Point[CORNERS];
        for (int i = 0; i < CORNERS; i++) {
            double angle = i * Math.PI * 2 / CORNERS;
            double r = radius * (0.8 + random.nextDouble() * 0.35);
            corners[i] = new Point(cx + Math.cos(angle) * r * 1.35, cy + Math.sin(angle) * r * 0.85);
        }
        // Catmull-Rom fermée : passe par chaque coin, sans angle vif.
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < CORNERS; i++) {
            Point p0 = corners[(i - 1 + CORNERS) % CORNERS], p1 = corners[i];
            Point p2 = corners[(i + 1) % CORNERS], p3 = corners[(i + 2) % CORNERS];
            for (int s = 0; s < STEPS; s++) {
                double t = (double) s / STEPS, t2 = t * t, t3 = t2 * t;
                points.add(new Point(
                        catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), t, t2, t3),
                        catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), t, t2, t3)));
            }
        }
        return List.copyOf(points);
    }

    private static double catmullRom(double a, double b, double c, double d, double t, double t2, double t3) {
        return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
    }
}
