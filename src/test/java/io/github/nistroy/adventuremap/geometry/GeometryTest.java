package io.github.nistroy.adventuremap.geometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryTest {
    @Test
    void blobIsDeterministicAndStaysAroundItsCentre() {
        List<Point> a = Blob.outline(500, 300, 100, 7);
        assertEquals(a, Blob.outline(500, 300, 100, 7));
        assertTrue(a.size() >= 40);
        for (Point p : a) {
            double dx = (p.x() - 500) / 1.35, dy = (p.y() - 300) / 0.85;
            double r = Math.sqrt(dx * dx + dy * dy);
            assertTrue(r > 70 && r < 125, "rayon " + r);
        }
        assertFalse(a.equals(Blob.outline(500, 300, 100, 8)), "graine différente, forme différente");
    }

    @Test
    void spansOfASquare() {
        List<Point> square = List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10));
        assertArrayEquals(new double[] {0, 10}, Polygon.spans(square, 5));
        assertArrayEquals(new double[0], Polygon.spans(square, 11));
    }

    @Test
    void spansOfAConcaveShapeSplit() {
        // U : deux branches au-dessus de y = 5
        List<Point> u = List.of(new Point(0, 0), new Point(3, 0), new Point(3, 5), new Point(7, 5),
                new Point(7, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10));
        assertArrayEquals(new double[] {0, 3, 7, 10}, Polygon.spans(u, 2));
        assertArrayEquals(new double[] {0, 10}, Polygon.spans(u, 8));
    }

    @Test
    void containsFollowsSpans() {
        List<Point> blob = Blob.outline(100, 100, 50, 3);
        assertTrue(Polygon.contains(blob, 100, 100));
        assertFalse(Polygon.contains(blob, 400, 100));
    }

    @Test
    void dottedCurveHasEvenlySpacedDotsFromStartToEnd() {
        List<Point> dots = Curve.dots(new Point(0, 0), new Point(100, 0), new Point(200, 0), 10);
        assertEquals(new Point(0, 0), dots.get(0));
        assertEquals(21, dots.size());
        for (int i = 1; i < dots.size(); i++) {
            assertEquals(10, dots.get(i).x() - dots.get(i - 1).x(), 0.5);
        }
    }

    @Test
    void doubleClickOnlyOnTheSameTargetAndInTime() {
        DoubleClick clicks = new DoubleClick(300);
        assertFalse(clicks.click("a", 1000));
        assertTrue(clicks.click("a", 1200));
        assertFalse(clicks.click("a", 1300), "un triple clic ne rouvre pas");
        assertFalse(clicks.click("b", 1350));
        assertFalse(clicks.click("a", 1400));
        assertFalse(clicks.click("a", 1800));
        assertTrue(clicks.click("a", 1900));
    }
}
