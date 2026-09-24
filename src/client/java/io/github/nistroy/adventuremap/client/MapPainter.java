package io.github.nistroy.adventuremap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nistroy.adventuremap.AdventureMap;
import io.github.nistroy.adventuremap.geometry.Curve;
import io.github.nistroy.adventuremap.geometry.Point;
import io.github.nistroy.adventuremap.geometry.Polygon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Primitives de dessin de la carte : le GUI ne fait que des rectangles et des textures, on compose avec. */
final class MapPainter {
    static final int INK = 0xFF3A2A19;
    static final int INK_SOFT = 0xFF6B5536;
    static final int PARCHMENT = 0xFFE8D6AE;
    static final int FOG = 0xFFEFE6D0;
    static final int RED = 0xFFA3262B;
    static final int GOLD = 0xFFE2B54A;
    static final int PURPLE = 0xFF6B2A86;
    static final int FRAME = 0xFF1C1914;
    static final int FRAME_EDGE = 0xFF4A4032;

    private static final ResourceLocation PAPER = AdventureMap.id("textures/gui/parchment.png");
    private static final ResourceLocation CLOUD = AdventureMap.id("textures/gui/fog.png");
    private static final ResourceLocation SEAL = AdventureMap.id("textures/gui/seal.png");
    private static final int PAPER_SIZE = 256;

    private MapPainter() {}

    /** Parchemin en tuiles (les textures du GUI ne se répètent pas d'elles-mêmes). */
    static void parchment(GuiGraphics g, int x, int y, int width, int height) {
        for (int ty = 0; ty < height; ty += PAPER_SIZE) {
            for (int tx = 0; tx < width; tx += PAPER_SIZE) {
                int w = Math.min(PAPER_SIZE, width - tx), h = Math.min(PAPER_SIZE, height - ty);
                g.blit(PAPER, x + tx, y + ty, 0, 0, w, h, PAPER_SIZE, PAPER_SIZE);
            }
        }
    }

    static void frame(GuiGraphics g, int x, int y, int width, int height, int color, int thickness) {
        g.fill(x - thickness, y - thickness, x + width + thickness, y, color);
        g.fill(x - thickness, y + height, x + width + thickness, y + height + thickness, color);
        g.fill(x - thickness, y, x, y + height, color);
        g.fill(x + width, y, x + width + thickness, y + height, color);
    }

    /** Remplit un contour (repère carte) ligne par ligne de pixels. */
    static void fillShape(GuiGraphics g, Viewport view, List<Point> shape, int color) {
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Point p : shape) {
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        int top = Math.max(view.y(minY), view.top()), bottom = Math.min(view.y(maxY), view.top() + view.height());
        for (int y = top; y < bottom; y++) {
            double[] spans = Polygon.spans(shape, view.virtualY(y + 0.5));
            for (int i = 0; i + 1 < spans.length; i += 2) {
                int x0 = Math.max(view.x(spans[i]), view.left()), x1 = Math.min(view.x(spans[i + 1]), view.left() + view.width());
                if (x1 > x0) g.fill(x0, y, x1, y + 1, color);
            }
        }
    }

    /** Même contour agrandi autour de son centre : sert de trait d'encre sous la forme. */
    static List<Point> grown(List<Point> shape, double cx, double cy, double by) {
        List<Point> result = new ArrayList<>(shape.size());
        for (Point p : shape) {
            double dx = p.x() - cx, dy = p.y() - cy, length = Math.hypot(dx, dy);
            double factor = length == 0 ? 1 : (length + by) / length;
            result.add(new Point(cx + dx * factor, cy + dy * factor));
        }
        return result;
    }

    static void dottedPath(GuiGraphics g, Viewport view, Point from, Point to, double bend, int color, int size, double spacing) {
        Point control = new Point((from.x() + to.x()) / 2 + (to.y() - from.y()) * bend, (from.y() + to.y()) / 2 - (to.x() - from.x()) * bend);
        for (Point dot : Curve.dots(from, control, to, spacing)) {
            int x = view.x(dot.x()), y = view.y(dot.y());
            g.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size, color);
        }
    }

    /** ✕ tracé en marches d'escalier : lisible à toutes les échelles du GUI. */
    static void cross(GuiGraphics g, int cx, int cy, int half, int thickness, int color) {
        for (int i = -half; i <= half; i++) {
            g.fill(cx + i - thickness / 2, cy + i - thickness / 2, cx + i - thickness / 2 + thickness, cy + i - thickness / 2 + thickness, color);
            g.fill(cx + i - thickness / 2, cy - i - thickness / 2, cx + i - thickness / 2 + thickness, cy - i - thickness / 2 + thickness, color);
        }
    }

    static void ring(GuiGraphics g, int cx, int cy, int radius, int color) {
        int steps = Math.max(24, radius * 4);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int x = cx + (int) Math.round(Math.cos(a) * radius), y = cy + (int) Math.round(Math.sin(a) * radius);
            g.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    static void seal(GuiGraphics g, int cx, int cy, int size) {
        texture(g, SEAL, cx - size / 2, cy - size / 2, size, size, 32);
    }

    static void fog(GuiGraphics g, int cx, int cy, int width, int height) {
        texture(g, CLOUD, cx - width / 2, cy - height / 2, width, height, 64);
    }

    private static void texture(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height, int size) {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, width, height, 0, 0, size, size, size, size);
        RenderSystem.disableBlend();
    }

    static void compass(GuiGraphics g, Font font, int cx, int cy, int radius) {
        ring(g, cx, cy, radius, INK_SOFT);
        for (int i = -radius - 4; i <= radius + 4; i++) {
            int w = Math.max(1, (radius + 4 - Math.abs(i)) / 5);
            g.fill(cx - w, cy + i, cx + w, cy + i + 1, INK);
        }
        for (int i = -radius - 2; i <= radius + 2; i++) {
            int w = Math.max(1, (radius + 2 - Math.abs(i)) / 6);
            g.fill(cx + i, cy - w, cx + i + 1, cy + w, INK_SOFT);
        }
        g.drawString(font, "N", cx - font.width("N") / 2, cy - radius - 14, INK, false);
    }

    static void centeredText(GuiGraphics g, Font font, String text, int cx, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popPose();
    }

    static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
