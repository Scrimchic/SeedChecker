package com.scrimchic.seedchecker.gui.map.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.scrimchic.seedchecker.client.exploration.ExplorationManager;
import com.scrimchic.seedchecker.core.map.ChunkRange;
import com.scrimchic.seedchecker.core.map.MapViewport;
import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.gui.map.MapCanvas;
import com.scrimchic.seedchecker.gui.map.MapHitTest;
import com.scrimchic.seedchecker.world.ActiveWorld;

/**
 * The player's own markers, in the dimension being looked at.
 *
 * <p>Needs no seed and nothing predicted: only a world profile to read markers from. It asks the
 * active exploration for this dimension's markers - a list kept per dimension, so other
 * dimensions are never walked - and draws the ones on screen at a fixed pixel size, whatever the
 * zoom. Player markers are never hidden for being far out; with thousands of them, culling to the
 * viewport is all that happens.
 */
public final class CustomMarkerLayer implements MapLayer {

    /** Labels are drawn next to markers only while this few are on screen. */
    static final int LABEL_LIMIT = 48;

    /** Culling slack, so a marker half off the edge is still drawn whole. */
    static final int CULL_MARGIN = MarkerSymbols.SYMBOL_SIZE;

    /** Half a symbol plus slack: a click this near a marker's centre picks it. */
    public static final double HIT_RADIUS = MarkerSymbols.SYMBOL_SIZE / 2 + 3;

    private static final int LABEL_COLOR = 0xFFF4F1E8;

    /** Fixed in tests; the client's manager otherwise. */
    private final ExplorationManager fixedExploration;

    private boolean enabled = true;

    public CustomMarkerLayer() {
        this(null);
    }

    CustomMarkerLayer(ExplorationManager exploration) {
        this.fixedExploration = exploration;
    }

    private ExplorationManager exploration() {
        return fixedExploration != null ? fixedExploration : ExplorationManager.getIfInitialized();
    }

    @Override
    public String displayName() {
        return "Custom Markers";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** In every dimension a player can be in, vanilla or not. */
    @Override
    public boolean appliesTo(String dimensionId) {
        return dimensionId != null;
    }

    /** Deliberately not about the seed: markers are the player's, not the map's. */
    @Override
    public String unavailableReason(ActiveWorld world, MapViewport viewport, ChunkRange visible) {
        ExplorationManager exploration = exploration();
        if (world.context().dimensionId() == null) {
            return "not in a world";
        }
        return exploration == null || !exploration.isActive() ? "no world profile" : null;
    }

    @Override
    public void render(MapCanvas canvas, MapViewport viewport, ChunkRange visible, ActiveWorld world) {
        List<CustomMarker> onScreen = visibleMarkers(world, viewport);
        if (onScreen.isEmpty()) {
            return;
        }
        // A symbol is some twenty fills. One batch for all of them: on 1.16.5 an unbatched fill is
        // its own draw call, and a zoomed-out view of many markers would be thousands of them.
        canvas.beginBatch();
        try {
            for (int i = 0; i < onScreen.size(); i++) {
                CustomMarker marker = onScreen.get(i);
                MarkerSymbols.drawSymbol(canvas, marker.type(), (int) Math.round(screenX(viewport, marker)),
                        (int) Math.round(screenY(viewport, marker)));
            }
        } finally {
            canvas.endBatch();
        }
        // Text may not be drawn inside a batch, so labels follow it.
        if (onScreen.size() > LABEL_LIMIT) {
            return;
        }
        for (int i = 0; i < onScreen.size(); i++) {
            CustomMarker marker = onScreen.get(i);
            if (marker.label() != null) {
                canvas.text(marker.label(), (int) Math.round(screenX(viewport, marker)) + MarkerSymbols.SYMBOL_SIZE / 2 + 3,
                        (int) Math.round(screenY(viewport, marker)) - 4, LABEL_COLOR);
            }
        }
    }

    /** The markers of the world's current dimension that are on screen, in creation order. */
    public List<CustomMarker> visibleMarkers(ActiveWorld world, MapViewport viewport) {
        ExplorationManager exploration = exploration();
        String dimensionId = world.context().dimensionId();
        if (exploration == null || dimensionId == null) {
            return Collections.emptyList();
        }
        return cull(exploration.markersIn(dimensionId), viewport, CULL_MARGIN);
    }

    /** Where a marker's symbol is centred: the middle of its block. */
    public static double screenX(MapViewport viewport, CustomMarker marker) {
        return viewport.blockToScreenX(marker.x() + 0.5);
    }

    public static double screenY(MapViewport viewport, CustomMarker marker) {
        return viewport.blockToScreenY(marker.z() + 0.5);
    }

    /** The markers whose centre is within the screen widened by a margin. */
    static List<CustomMarker> cull(List<CustomMarker> markers, MapViewport viewport, int margin) {
        List<CustomMarker> onScreen = new ArrayList<CustomMarker>();
        double right = viewport.getWidth() + margin;
        double bottom = viewport.getHeight() + margin;
        for (int i = 0; i < markers.size(); i++) {
            CustomMarker marker = markers.get(i);
            double x = screenX(viewport, marker);
            double y = screenY(viewport, marker);
            if (x >= -margin && y >= -margin && x <= right && y <= bottom) {
                onScreen.add(marker);
            }
        }
        return onScreen;
    }

    /** Every drawn marker as something a click can pick; nothing while the layer is switched off. */
    public List<MapHitTest.Candidate<Object>> hitCandidates(ActiveWorld world, MapViewport viewport) {
        if (!enabled || !appliesTo(world.context().dimensionId())) {
            return Collections.emptyList();
        }
        List<CustomMarker> onScreen = visibleMarkers(world, viewport);
        List<MapHitTest.Candidate<Object>> candidates =
                new ArrayList<MapHitTest.Candidate<Object>>(onScreen.size());
        for (CustomMarker marker : onScreen) {
            candidates.add(new MapHitTest.Candidate<Object>(marker, screenX(viewport, marker),
                    screenY(viewport, marker), HIT_RADIUS, MapHitTest.PRIORITY_CUSTOM_MARKER, marker.id()));
        }
        return candidates;
    }
}
