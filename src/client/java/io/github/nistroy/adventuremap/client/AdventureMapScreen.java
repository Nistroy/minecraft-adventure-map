package io.github.nistroy.adventuremap.client;

import static io.github.nistroy.adventuremap.client.MapPainter.FOG;
import static io.github.nistroy.adventuremap.client.MapPainter.FRAME;
import static io.github.nistroy.adventuremap.client.MapPainter.FRAME_EDGE;
import static io.github.nistroy.adventuremap.client.MapPainter.GOLD;
import static io.github.nistroy.adventuremap.client.MapPainter.INK;
import static io.github.nistroy.adventuremap.client.MapPainter.INK_SOFT;
import static io.github.nistroy.adventuremap.client.MapPainter.PARCHMENT;
import static io.github.nistroy.adventuremap.client.MapPainter.PURPLE;
import static io.github.nistroy.adventuremap.client.MapPainter.RED;
import static io.github.nistroy.adventuremap.client.MapPainter.withAlpha;

import io.github.nistroy.adventuremap.Rewards;
import io.github.nistroy.adventuremap.geometry.Blob;
import io.github.nistroy.adventuremap.geometry.DoubleClick;
import io.github.nistroy.adventuremap.geometry.Point;
import io.github.nistroy.adventuremap.geometry.Polygon;
import io.github.nistroy.adventuremap.map.MapDefinition;
import io.github.nistroy.adventuremap.map.MapNode;
import io.github.nistroy.adventuremap.map.Progress;
import io.github.nistroy.adventuremap.map.Progress.RegionState;
import io.github.nistroy.adventuremap.map.Region;
import io.github.nistroy.adventuremap.map.Reward;
import io.github.nistroy.adventuremap.network.ClaimPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * La carte ouverte : vue du monde (régions) → double-clic → vue d'une région (routes d'objectifs).
 * Carte à gauche, fiche à droite : liste « À faire » cliquable, ou fiche de l'élément choisi.
 * N'a aucun état propre au joueur : tout vient du serveur ({@link #update}).
 */
final class AdventureMapScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int BAR = 18;
    /** Rayon de clic autour d'un ✕, en unités de carte. */
    private static final double NODE_HIT = 34;
    /** Taille de dessin de base des ✕ et sceaux, en unités de carte. */
    private static final double NODE_SIZE = 22;
    /** Largeur max d'une étiquette de ✕, en unités de carte (les colonnes sont à ~170). */
    private static final double LABEL_WIDTH = 160;
    /** Pas de molette dans une région, en unités de carte. */
    private static final double SCROLL_STEP = 60;
    /** Pas de molette dans la fiche, en pixels. */
    private static final int PANEL_SCROLL_STEP = 12;

    private MapDefinition map;
    private Progress progress;
    private final Map<String, List<Point>> outlines = new HashMap<>();
    private final DoubleClick doubleClick = new DoubleClick(350);

    private String openRegion;
    private String selectedRegion;
    private String selectedNode;
    /** Défilement de la région ouverte, en unités de carte. */
    private double scroll;
    /** Défilement de la fiche, en pixels ; borné au dessin suivant. */
    private int panelScroll;

    private Viewport view;
    private int panelLeft;
    private int panelWidth;
    private Button backButton;
    private Button openButton;
    private Button claimButton;
    private final List<RewardSlot> rewardSlots = new ArrayList<>();
    private final List<TodoLink> todoLinks = new ArrayList<>();

    private record RewardSlot(ItemStack stack, int x, int y) {}

    private record TodoLink(String nodeId, int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    AdventureMapScreen(MapDefinition map, List<String> done, List<String> claimed) {
        super(Component.translatable("item.adventuremap.adventurer_map"));
        update(map, done, claimed);
    }

    void update(MapDefinition map, List<String> done, List<String> claimed) {
        if (this.map != map) {
            outlines.clear();
            for (Region region : map.regions()) {
                outlines.put(region.id(), Blob.outline(region.x(), region.y(), region.radius(), region.id().hashCode()));
            }
            if (openRegion != null && map.region(openRegion) == null) openRegion = null;
        }
        this.map = map;
        this.progress = Progress.of(map, new HashSet<>(done), new HashSet<>(claimed));
        refreshButtons();
    }

    @Override
    protected void init() {
        panelWidth = Math.max(130, Math.min(190, width * 28 / 100));
        panelLeft = width - MARGIN - panelWidth;
        int mapTop = MARGIN + BAR;
        view = Viewport.fit(MARGIN, mapTop, panelLeft - 2 * MARGIN, height - mapTop - MARGIN);

        backButton = addRenderableWidget(Button.builder(Component.literal("‹ Le monde"), b -> showWorld())
                .bounds(MARGIN, MARGIN - 2, 70, 16).build());
        int buttonWidth = panelWidth - 16;
        claimButton = addRenderableWidget(Button.builder(Component.literal("Réclamer la récompense"), b -> claim())
                .bounds(panelLeft + 8, height - MARGIN - 28, buttonWidth, 20).build());
        openButton = addRenderableWidget(Button.builder(Component.literal("Ouvrir la région"), b -> openRegion(selectedRegion))
                .bounds(panelLeft + 8, height - MARGIN - 28, buttonWidth, 20).build());
        refreshButtons();
    }

    private void refreshButtons() {
        if (backButton == null) return;
        backButton.visible = openRegion != null;
        openButton.visible = openRegion == null && selectedRegion != null
                && progress.regionState(selectedRegion) != RegionState.HIDDEN;
        claimButton.visible = openRegion != null && selectedNode != null && progress.claimable(selectedNode);
    }

    // ---- Actions ----

    private void openRegion(String regionId) {
        if (regionId == null || progress.regionState(regionId) == RegionState.HIDDEN) return;
        openRegion = regionId;
        selectedNode = null;
        scroll = 0;
        panelScroll = 0;
        refreshButtons();
    }

    /** Depuis la liste « À faire » : ouvre la région du ✕, le choisit et le fait défiler à l'écran. */
    private void goToNode(String nodeId) {
        Region region = map.regionOf(nodeId);
        if (region == null) return;
        if (!region.id().equals(openRegion)) openRegion(region.id());
        selectedNode = nodeId;
        scroll = clampScroll(map.node(nodeId).y() - MapDefinition.HEIGHT / 2.0);
        panelScroll = 0;
        refreshButtons();
    }

    private void showWorld() {
        selectedRegion = openRegion;
        openRegion = null;
        selectedNode = null;
        panelScroll = 0;
        refreshButtons();
    }

    private double clampScroll(double value) {
        if (openRegion == null) return 0;
        double max = Math.max(0, map.region(openRegion).height() - MapDefinition.HEIGHT);
        return Math.max(0, Math.min(max, value));
    }

    /** Vue de la région ouverte (défilée), ou du monde. */
    private Viewport mapView() {
        return openRegion == null ? view : view.scrolled(scroll);
    }

    private void claim() {
        if (selectedNode != null && progress.claimable(selectedNode)) {
            ClientPlayNetworking.send(new ClaimPayload(selectedNode));
            claimButton.active = false;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (TodoLink link : todoLinks) {
            if (link.contains(mouseX, mouseY)) {
                goToNode(link.nodeId());
                claimButton.active = true;
                return true;
            }
        }
        if (!view.contains(mouseX, mouseY)) return false;
        Viewport current = mapView();
        double vx = current.virtualX(mouseX), vy = current.virtualY(mouseY);
        if (openRegion == null) {
            String hit = regionAt(vx, vy);
            if (hit != null) {
                if (!hit.equals(selectedRegion)) panelScroll = 0;
                selectedRegion = hit;
                if (doubleClick.click(hit, Util.getMillis())) openRegion(hit);
            }
        } else {
            String hit = nodeAt(vx, vy);
            if (hit == null || !hit.equals(selectedNode)) panelScroll = 0;
            selectedNode = hit;
        }
        claimButton.active = true;
        refreshButtons();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeft) {
            panelScroll = Math.max(0, panelScroll - (int) Math.round(scrollY * PANEL_SCROLL_STEP));
            return true;
        }
        if (openRegion != null && view.contains(mouseX, mouseY)) {
            scroll = clampScroll(scroll - scrollY * SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Échap dans une région revient au monde, comme un « retour » ; au monde, il ferme.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && openRegion != null) {
            showWorld();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String regionAt(double vx, double vy) {
        // Dernière dessinée = au-dessus : on teste dans l'ordre inverse.
        List<Region> regions = map.regions();
        for (int i = regions.size() - 1; i >= 0; i--) {
            if (Polygon.contains(outlines.get(regions.get(i).id()), vx, vy)) return regions.get(i).id();
        }
        return null;
    }

    private String nodeAt(double vx, double vy) {
        String best = null;
        double bestDistance = NODE_HIT;
        for (MapNode node : map.region(openRegion).nodes()) {
            double distance = Math.hypot(node.x() - vx, node.y() - vy);
            if (distance < bestDistance) {
                best = node.id();
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---- Dessin ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, FRAME);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawBar(g);

        MapPainter.frame(g, view.left(), view.top(), view.width(), view.height(), FRAME_EDGE, 2);
        g.enableScissor(view.left(), view.top(), view.left() + view.width(), view.top() + view.height());
        MapPainter.parchment(g, view.left(), view.top(), view.width(), view.height());
        if (openRegion == null) drawWorld(g); else drawRegion(g, map.region(openRegion), mapView());
        MapPainter.compass(g, font, view.x(935), view.y(525), Math.max(6, (int) (26 * view.scale())));
        if (openRegion != null) drawScrollBar(g, map.region(openRegion));
        g.disableScissor();

        drawPanel(g);
        // Les boutons par-dessus la fiche : ils sont dessinés par super.render avant, on les redessine.
        for (Button button : List.of(backButton, openButton, claimButton)) {
            if (button.visible) button.render(g, mouseX, mouseY, partialTick);
        }
        for (RewardSlot slot : rewardSlots) {
            if (mouseX >= slot.x() && mouseX < slot.x() + 16 && mouseY >= slot.y() && mouseY < slot.y() + 16) {
                g.renderTooltip(font, slot.stack(), mouseX, mouseY);
            }
        }
    }

    private void drawBar(GuiGraphics g) {
        int x = openRegion == null ? MARGIN : MARGIN + 76;
        String title = openRegion == null ? "Carte de l'aventurier" : map.region(openRegion).name();
        g.drawString(font, title, x, MARGIN + 2, 0xFFEFE3C4, false);
        String count = "✦ " + progress.doneCount() + " / " + progress.totalCount() + " sceaux";
        if (progress.unclaimedCount() > 0) count += " · " + progress.unclaimedCount() + " à réclamer";
        g.drawString(font, count, width - MARGIN - font.width(count), MARGIN + 2, GOLD, false);
    }

    private void drawWorld(GuiGraphics g) {
        List<Region> regions = map.regions();
        Region home = regions.get(0);
        for (Region region : regions.subList(1, regions.size())) {
            MapPainter.dottedPath(g, view, new Point(home.x(), home.y()), new Point(region.x(), region.y()), 0.15,
                    withAlpha(INK_SOFT, 0xC0), dot(3), 11);
        }
        for (Region region : regions) {
            List<Point> outline = outlines.get(region.id());
            RegionState state = progress.regionState(region.id());
            int ink = region.id().equals(selectedRegion) ? RED : INK;
            MapPainter.fillShape(g, view, MapPainter.grown(outline, region.x(), region.y(), 4 / view.scale()), ink);
            int cx = view.x(region.x()), cy = view.y(region.y());
            if (state == RegionState.HIDDEN) {
                MapPainter.fillShape(g, view, outline, withAlpha(FOG, 0xFF));
                MapPainter.fog(g, cx, cy, view.x(region.x() + region.radius() * 1.5) - view.x(region.x() - region.radius() * 1.5),
                        view.y(region.y() + region.radius()) - view.y(region.y() - region.radius()));
                MapPainter.centeredText(g, font, "? ? ?", cx, cy - 4, INK_SOFT, 1.5F);
                continue;
            }
            MapPainter.fillShape(g, view, outline, withAlpha(region.color(), 0xE8));
            long done = region.nodes().stream().filter(n -> progress.isDone(n.id())).count();
            MapPainter.centeredText(g, font, region.name(), cx, cy - 10, INK, view.scale() > 0.55 ? 1.25F : 1F);
            MapPainter.centeredText(g, font, done + " / " + region.nodes().size() + " sceaux", cx, cy + 5, INK_SOFT, 1F);
            int markX = view.x(region.x() + region.radius() * 1.05), markY = view.y(region.y() - region.radius() * 0.55);
            if (state == RegionState.DONE) {
                MapPainter.seal(g, markX, markY, size(40));
            } else {
                MapPainter.cross(g, markX, markY, size(9), dot(4), RED);
            }
        }
        MapPainter.centeredText(g, font, "Double-clic sur une région pour l'ouvrir", view.left() + view.width() / 2,
                view.top() + view.height() - 12, INK_SOFT, 1F);
    }

    private void drawRegion(GuiGraphics g, Region region, Viewport view) {
        // Fond de région étiré sur toute sa hauteur : une région qui défile garde son contour.
        double centerY = region.height() / 2.0, stretch = region.height() / (double) MapDefinition.HEIGHT;
        List<Point> backdrop = Blob.outline(MapDefinition.WIDTH / 2.0, MapDefinition.HEIGHT / 2.0, 290, region.id().hashCode() * 31L)
                .stream().map(p -> new Point(p.x(), centerY + (p.y() - MapDefinition.HEIGHT / 2.0) * stretch)).toList();
        MapPainter.fillShape(g, view, MapPainter.grown(backdrop, 500, centerY, 3 / view.scale()), withAlpha(INK, 0x50));
        MapPainter.fillShape(g, view, backdrop, withAlpha(region.color(), 0x70));

        for (MapNode node : region.nodes()) {
            for (String parentId : node.after()) {
                MapNode parent = map.node(parentId);
                boolean lit = progress.isDone(parentId);
                MapPainter.dottedPath(g, view, new Point(parent.x(), parent.y()), new Point(node.x(), node.y()),
                        parent.y() == node.y() ? 0.04 : 0, lit ? INK : withAlpha(INK_SOFT, 0x90), dot(lit ? 4 : 3), lit ? 12 : 14);
            }
        }
        for (MapNode node : region.nodes()) drawNode(g, node, view);
    }

    private void drawScrollBar(GuiGraphics g, Region region) {
        if (region.height() <= MapDefinition.HEIGHT) return;
        int x = view.left() + view.width() - 4;
        int thumb = Math.max(8, view.height() * MapDefinition.HEIGHT / region.height());
        int y = view.top() + (int) Math.round((view.height() - thumb) * scroll / (region.height() - MapDefinition.HEIGHT));
        g.fill(x, view.top(), x + 3, view.top() + view.height(), withAlpha(INK_SOFT, 0x40));
        g.fill(x, y, x + 3, y + thumb, withAlpha(INK, 0xC0));
    }

    private void drawNode(GuiGraphics g, MapNode node, Viewport view) {
        int cx = view.x(node.x()), cy = view.y(node.y()), s = size(NODE_SIZE);
        if (node.id().equals(selectedNode)) MapPainter.ring(g, cx, cy, s + 4, RED);
        if (!progress.visible(node.id())) {
            MapPainter.fog(g, cx, cy, s * 3, s * 3);
            MapPainter.centeredText(g, font, "?", cx, cy - 6, INK_SOFT, 1.5F);
            return;
        }
        if (progress.isDone(node.id())) {
            MapPainter.seal(g, cx, cy, s * 2);
            if (progress.claimable(node.id())) {
                int bx = cx + s * 3 / 4, by = cy - s * 3 / 4, r = Math.max(3, s / 3);
                g.fill(bx - r, by - r, bx + r, by + r, RED);
                g.fill(bx - 1, by - r + 2, bx + 1, by + 1, PARCHMENT);
                g.fill(bx - 1, by + 2, bx + 1, by + r - 1, PARCHMENT);
            }
        } else {
            g.fill(cx - s, cy - s, cx + s, cy + s, withAlpha(PARCHMENT, 0x90));
            boolean available = progress.available(node.id());
            MapPainter.cross(g, cx, cy, s * 11 / 20, dot(available ? 5 : 3), available ? RED : withAlpha(INK_SOFT, 0xA0));
        }
        // Étiquette sous le ✕, coupée en lignes pour ne pas déborder sur la colonne voisine.
        int lineY = cy + s + 3;
        for (FormattedCharSequence line : font.split(Component.literal(node.label()), Math.max(40, size(LABEL_WIDTH)))) {
            g.drawString(font, line, cx - font.width(line) / 2, lineY, INK, false);
            lineY += font.lineHeight;
        }
    }

    private void drawPanel(GuiGraphics g) {
        rewardSlots.clear();
        todoLinks.clear();
        int x = panelLeft, top = MARGIN + BAR, bottom = height - MARGIN;
        MapPainter.frame(g, x, top, panelWidth, bottom - top, FRAME_EDGE, 2);
        g.fill(x, top, x + panelWidth, bottom, PARCHMENT);
        // Les boutons du bas recouvrent la fiche : on coupe le texte au-dessus d'eux.
        boolean button = openButton.visible || claimButton.visible;
        int clipBottom = bottom - (button ? 34 : 4);
        g.enableScissor(x, top + 2, x + panelWidth, clipBottom);
        PanelText text = new PanelText(g, x + 8, top + 8 - panelScroll, panelWidth - 16);
        drawPanelContent(text);
        g.disableScissor();
        int overflow = text.y + panelScroll - clipBottom;
        panelScroll = Math.max(0, Math.min(panelScroll, overflow));
        todoLinks.removeIf(link -> link.y() + link.height() <= top + 2 || link.y() >= clipBottom);
        rewardSlots.removeIf(slot -> slot.y() + 16 <= top + 2 || slot.y() >= clipBottom);
        if (overflow > 0) {
            int track = clipBottom - top - 4;
            int thumb = Math.max(8, track * track / (track + overflow));
            int y = top + 2 + (track - thumb) * panelScroll / overflow;
            g.fill(x + panelWidth - 4, y, x + panelWidth - 2, y + thumb, withAlpha(INK, 0xA0));
        }
    }

    private void drawPanelContent(PanelText text) {
        if (openRegion == null) {
            Region region = selectedRegion == null ? null : map.region(selectedRegion);
            if (region == null) {
                text.small("CARTE DU MONDE");
                text.title("Que faire ?");
                text.hint("Double-clic sur une région pour l'ouvrir, ou clic sur un objectif ci-dessous.");
                drawUnclaimed(text);
                for (Region each : map.regions()) drawTodo(text, each, true);
            } else if (progress.regionState(region.id()) == RegionState.HIDDEN) {
                text.small("RÉGION INCONNUE");
                text.title("? ? ?");
                text.body("Personne n'y est encore allé. La carte ne montre que du brouillard.");
                text.hint("La région se révèle quand tu y réussis un premier objectif.");
            } else {
                long done = region.nodes().stream().filter(n -> progress.isDone(n.id())).count();
                text.small(region.subtitle().toUpperCase());
                text.title(region.name());
                text.body(region.description());
                text.pill(progress.regionState(region.id()) == RegionState.DONE ? "TERMINÉE" : done + " / " + region.nodes().size() + " SCEAUX",
                        progress.regionState(region.id()) == RegionState.DONE ? 0xFF7A5310 : RED);
                drawTodo(text, region, false);
            }
            return;
        }

        Region region = map.region(openRegion);
        MapNode node = selectedNode == null ? null : map.node(selectedNode);
        if (node == null) {
            text.small(region.subtitle().toUpperCase());
            text.title(region.name());
            text.body(region.description());
            text.hint(region.height() > MapDefinition.HEIGHT
                    ? "Clique sur un ✕ pour voir l'objectif. Molette pour faire défiler la région."
                    : "Clique sur un ✕ pour voir l'objectif.");
            drawTodo(text, region, false);
            return;
        }
        if (!progress.visible(node.id())) {
            text.small("BROUILLARD");
            text.title("Objectif caché");
            text.hint("Réussis l'étape d'avant pour lever le brouillard.");
            return;
        }
        text.small(node.kind().toUpperCase());
        text.title(node.label());
        text.body(node.objective());
        if (node.hint() != null && !progress.isDone(node.id())) text.hint("Où chercher : " + node.hint());
        if (progress.isDone(node.id())) {
            text.pill("RÉUSSI", 0xFF7A5310);
        } else if (progress.available(node.id())) {
            text.pill("DISPONIBLE", RED);
        } else {
            text.pill("BLOQUÉ", INK_SOFT);
            List<String> missing = node.after().stream().filter(id -> !progress.isDone(id)).map(id -> map.node(id).label()).toList();
            text.hint("Il faut d'abord : " + String.join(", ", missing) + ".");
        }
        text.rule();
        for (Reward reward : node.rewards()) {
            ItemStack stack = minecraft.level == null ? ItemStack.EMPTY : Rewards.toStack(reward, minecraft.level.registryAccess());
            int y = text.y;
            if (!stack.isEmpty()) {
                text.g.renderItem(stack, text.x, y);
                text.g.renderItemDecorations(font, stack, text.x, y);
                rewardSlots.add(new RewardSlot(stack, text.x, y));
            }
            String name = reward.unique() ? reward.name() : stack.isEmpty() ? reward.item() : stack.getHoverName().getString();
            if (!reward.unique() && reward.count() > 1) name = reward.count() + " × " + name;
            text.itemLine(name, reward.unique() ? PURPLE : INK);
        }
        if (progress.claimed(node.id())) text.hint("Récompense déjà réclamée.");
    }

    /** Récompenses gagnées pas encore réclamées : en tête de liste, sinon on les oublie. */
    private void drawUnclaimed(PanelText text) {
        List<String> unclaimed = progress.unclaimed();
        if (unclaimed.isEmpty()) return;
        text.rule();
        text.small("À RÉCLAMER");
        for (String id : unclaimed) text.link(id, "★ " + map.node(id).label(), RED);
    }

    /** Objectifs faisables maintenant ; {@code withHeader} = nom de la région au-dessus (vue du monde). */
    private void drawTodo(PanelText text, Region region, boolean withHeader) {
        List<String> todo = progress.todo(region.id());
        if (todo.isEmpty()) return;
        text.rule();
        text.small(withHeader ? region.name().toUpperCase() : "À FAIRE ICI");
        for (String id : todo) text.link(id, "✕ " + map.node(id).label(), INK);
    }

    /** Taille en pixels d'un élément donné en unités de carte, jamais sous 2 px. */
    private int size(double virtual) {
        return Math.max(2, (int) Math.round(virtual * view.scale()));
    }

    private int dot(int pixelsAtFullSize) {
        return Math.max(1, (int) Math.round(pixelsAtFullSize * view.scale() * 1.6));
    }

    /** Écrit la fiche de haut en bas en retenant la ligne courante. */
    private final class PanelText {
        private final GuiGraphics g;
        private final int x;
        private final int width;
        private int y;

        PanelText(GuiGraphics g, int x, int y, int width) {
            this.g = g;
            this.x = x;
            this.y = y;
            this.width = width;
        }

        void small(String line) {
            g.drawString(font, line, x, y, INK_SOFT, false);
            y += font.lineHeight + 3;
        }

        void title(String line) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(1.4F, 1.4F, 1);
            List<FormattedCharSequence> lines = font.split(Component.literal(line), (int) (width / 1.4F));
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), 0, i * font.lineHeight, INK, false);
            g.pose().popPose();
            y += (int) (lines.size() * font.lineHeight * 1.4F) + 5;
        }

        void body(String paragraph) {
            wrapped(Component.literal(paragraph), INK);
            y += 4;
        }

        void hint(String paragraph) {
            wrapped(Component.literal(paragraph).withStyle(style -> style.withItalic(true)), INK_SOFT);
            y += 4;
        }

        void pill(String label, int color) {
            int w = font.width(label) + 8;
            MapPainter.frame(g, x + 1, y + 1, w - 2, font.lineHeight + 2, color, 1);
            g.drawString(font, label, x + 4, y + 3, color, false);
            y += font.lineHeight + 10;
        }

        void rule() {
            for (int i = 0; i < width; i += 4) g.fill(x + i, y, x + i + 2, y + 1, INK_SOFT);
            y += 6;
        }

        /** Ligne cliquable qui mène au ✕ ; soulignée au survol. */
        void link(String nodeId, String label, int color) {
            int start = y;
            List<FormattedCharSequence> lines = font.split(Component.literal(label), width - 4);
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 2, y, color, false);
                y += font.lineHeight;
            }
            todoLinks.add(new TodoLink(nodeId, x, start - 1, width, y - start + 2));
            y += 2;
        }

        void itemLine(String name, int color) {
            List<FormattedCharSequence> lines = font.split(Component.literal(name), width - 20);
            int lineY = y + (lines.size() == 1 ? 4 : 0);
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 20, lineY, color, false);
                lineY += font.lineHeight;
            }
            y = Math.max(y + 18, lineY + 2);
        }

        private void wrapped(Component text, int color) {
            for (FormattedCharSequence line : font.split(text, width)) {
                g.drawString(font, line, x, y, color, false);
                y += font.lineHeight;
            }
        }
    }
}
