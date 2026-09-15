package com.scrimchic.seedchecker.gui.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.scrimchic.seedchecker.exploration.CustomMarker;
import com.scrimchic.seedchecker.world.PlayerPosition;

/**
 * The marker list's order and wording, without a panel.
 *
 * <p>Given the markers already shown in a dimension - the same ones the map draws - and the player's
 * position when the player is in that dimension:
 *
 * <ul>
 *   <li>with a player: nearest first, by horizontal distance from the player to the middle of the
 *       marker's block, height ignored; then by label, type and id, so equal distances keep one
 *       order;</li>
 *   <li>without one: by type, then label, then id.</li>
 * </ul>
 */
public final class MarkerList {

    /** One listed marker. */
    public static final class Entry {

        private final CustomMarker marker;
        private final Double distance;

        Entry(CustomMarker marker, Double distance) {
            this.marker = marker;
            this.distance = distance;
        }

        public CustomMarker marker() {
            return marker;
        }

        /** Horizontal blocks to the player, or {@code null} without a player. */
        public Double distance() {
            return distance;
        }

        /** Where selecting the entry centres the map: the middle of the marker's block. */
        public double centerX() {
            return marker.x() + 0.5;
        }

        public double centerZ() {
            return marker.z() + 0.5;
        }

        /** {@code Type - label - distance}, leaving out what is not there. */
        public String text(int maxLabelChars) {
            StringBuilder text = new StringBuilder(marker.type().displayName());
            if (marker.label() != null) {
                text.append(" - ").append(NotePreview.of(marker.label(), 1, maxLabelChars).lines().get(0));
            }
            if (distance != null) {
                text.append(" - ").append(formatDistance(distance.doubleValue()));
            }
            return text.toString();
        }
    }

    private MarkerList() {
    }

    /**
     * @param player the player's position, or {@code null} when there is none in this dimension
     */
    public static List<Entry> of(List<CustomMarker> markers, PlayerPosition player) {
        List<Entry> entries = new ArrayList<Entry>(markers.size());
        for (int i = 0; i < markers.size(); i++) {
            CustomMarker marker = markers.get(i);
            entries.add(new Entry(marker, player == null ? null : Double.valueOf(distance(player, marker))));
        }
        Collections.sort(entries, player == null ? BY_TYPE : BY_DISTANCE);
        return entries;
    }

    /** Horizontal distance from the player to the middle of the marker's block. */
    public static double distance(PlayerPosition player, CustomMarker marker) {
        double dx = player.x() - (marker.x() + 0.5);
        double dz = player.z() - (marker.z() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** {@code 432 blocks} below a thousand, {@code 1.2k blocks} from there, rounded half up. */
    public static String formatDistance(double blocks) {
        long rounded = Math.round(blocks);
        if (rounded < 1000L) {
            return rounded + " blocks";
        }
        return String.format(Locale.ROOT, "%.1fk blocks", Math.round(blocks / 100.0) / 10.0);
    }

    private static final Comparator<Entry> BY_DISTANCE = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            int byDistance = Double.compare(a.distance.doubleValue(), b.distance.doubleValue());
            return byDistance != 0 ? byDistance : byLabelTypeId(a.marker, b.marker);
        }
    };

    private static final Comparator<Entry> BY_TYPE = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            int byType = a.marker.type().compareTo(b.marker.type());
            return byType != 0 ? byType : byLabelTypeId(a.marker, b.marker);
        }
    };

    private static int byLabelTypeId(CustomMarker a, CustomMarker b) {
        int byLabel = labelKey(a).compareTo(labelKey(b));
        if (byLabel != 0) {
            return byLabel;
        }
        int byType = a.type().compareTo(b.type());
        return byType != 0 ? byType : a.id().compareTo(b.id());
    }

    /** A marker without a label sorts after every labelled one. */
    private static String labelKey(CustomMarker marker) {
        return marker.label() == null ? "￿" : marker.label().toLowerCase(Locale.ROOT);
    }
}
