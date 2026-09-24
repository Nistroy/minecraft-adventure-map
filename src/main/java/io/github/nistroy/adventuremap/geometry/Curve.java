package io.github.nistroy.adventuremap.geometry;

import java.util.ArrayList;
import java.util.List;

/** Chemin en pointillés d'une carte au trésor : points régulièrement espacés sur une courbe de Bézier quadratique. */
public final class Curve {
    private static final int SAMPLES = 200;

    private Curve() {}

    public static List<Point> dots(Point from, Point control, Point to, double spacing) {
        List<Point> dots = new ArrayList<>();
        dots.add(from);
        Point previous = from;
        double travelled = 0;
        for (int i = 1; i <= SAMPLES; i++) {
            Point next = at(from, control, to, (double) i / SAMPLES);
            double step = Math.hypot(next.x() - previous.x(), next.y() - previous.y());
            while (travelled + step >= spacing && step > 0) {
                double f = (spacing - travelled) / step;
                previous = new Point(previous.x() + (next.x() - previous.x()) * f, previous.y() + (next.y() - previous.y()) * f);
                dots.add(previous);
                step = Math.hypot(next.x() - previous.x(), next.y() - previous.y());
                travelled = 0;
            }
            travelled += step;
            previous = next;
        }
        Point last = dots.get(dots.size() - 1);
        if (Math.hypot(to.x() - last.x(), to.y() - last.y()) > spacing / 2) dots.add(to);
        return dots;
    }

    private static Point at(Point a, Point c, Point b, double t) {
        double u = 1 - t;
        return new Point(u * u * a.x() + 2 * u * t * c.x() + t * t * b.x(), u * u * a.y() + 2 * u * t * c.y() + t * t * b.y());
    }
}
