package com.scrimchic.seedchecker.gui.map.layer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.scrimchic.seedchecker.exploration.MarkerType;
import com.scrimchic.seedchecker.exploration.StructureStatus;
import com.scrimchic.seedchecker.gui.map.MapCanvas;

/**
 * The small pictures the map draws for custom markers and for a structure's exploration status,
 * built from filled rectangles only - no textures, the same on every Minecraft version.
 *
 * <p>Each picture is a pixel mask. {@code o} is a near-black outline, {@code #} the marker type's
 * colour, {@code w} a light accent, {@code .} nothing. A mask is turned once into horizontal runs,
 * and a run is one {@code fill}.
 *
 * <p>Every marker type has its own silhouette, not just its own colour: a house, a chest, a portal
 * frame, a striped field, a warning triangle, a gem, a person, a pin. A status badge is a dark
 * square with a light glyph - a dot for visited, a tick for looted, a hollow box for empty and a
 * cross for destroyed - so it reads on any biome colour and without telling colours apart.
 */
public final class MarkerSymbols {

    public static final int SYMBOL_SIZE = 11;

    public static final int BADGE_SIZE = 7;

    static final int OUTLINE = 0xFF0B0E11;

    private static final int ACCENT = 0xFFF4F1E8;

    private static final Map<MarkerType, String[]> SYMBOLS = new EnumMap<MarkerType, String[]>(MarkerType.class);
    private static final Map<MarkerType, int[]> SYMBOL_RUNS = new EnumMap<MarkerType, int[]>(MarkerType.class);
    private static final Map<StructureStatus, String[]> BADGES =
            new EnumMap<StructureStatus, String[]>(StructureStatus.class);
    private static final Map<StructureStatus, int[]> BADGE_RUNS =
            new EnumMap<StructureStatus, int[]>(StructureStatus.class);

    static {
        symbol(MarkerType.BASE,
                ".....o.....",
                "....o#o....",
                "...o###o...",
                "..o#####o..",
                ".o#######o.",
                "ooo#####ooo",
                "..o#####o..",
                "..o##w##o..",
                "..o##w##o..",
                "..ooooooo..",
                "...........");
        symbol(MarkerType.STASH,
                "...........",
                "...........",
                ".ooooooooo.",
                ".o#######o.",
                ".o#######o.",
                ".oooowoooo.",
                ".o###w###o.",
                ".o#######o.",
                ".o#######o.",
                ".ooooooooo.",
                "...........");
        symbol(MarkerType.PORTAL,
                "..ooooooo..",
                "..ooooooo..",
                "..oo###oo..",
                "..oo###oo..",
                "..oo#w#oo..",
                "..oo###oo..",
                "..oo###oo..",
                "..oo###oo..",
                "..oo###oo..",
                "..ooooooo..",
                "..ooooooo..");
        symbol(MarkerType.FARM,
                "...........",
                ".ooooooooo.",
                ".o#######o.",
                ".ooooooooo.",
                ".o#######o.",
                ".ooooooooo.",
                ".o#######o.",
                ".ooooooooo.",
                ".o#######o.",
                ".ooooooooo.",
                "...........");
        symbol(MarkerType.DANGER,
                ".....o.....",
                "....o#o....",
                "....o#o....",
                "...o#w#o...",
                "...o#w#o...",
                "..o##w##o..",
                "..o#####o..",
                ".o###w###o.",
                ".o#######o.",
                "ooooooooooo",
                "...........");
        symbol(MarkerType.RESOURCE,
                ".....o.....",
                "....o#o....",
                "...o###o...",
                "..o##w##o..",
                ".o###w###o.",
                "o#########o",
                ".o#######o.",
                "..o#####o..",
                "...o###o...",
                "....o#o....",
                ".....o.....");
        symbol(MarkerType.PLAYER_BASE,
                "...ooooo...",
                "...o###o...",
                "...o#w#o...",
                "...o###o...",
                "...ooooo...",
                ".ooooooooo.",
                ".o#######o.",
                "o#########o",
                "o#########o",
                "ooooooooooo",
                "...........");
        symbol(MarkerType.CUSTOM,
                "...ooooo...",
                "..o#####o..",
                ".o##www##o.",
                ".o##wow##o.",
                ".o##www##o.",
                "..o#####o..",
                "...o###o...",
                "....o#o....",
                "....o#o....",
                ".....o.....",
                "...........");

        badge(StructureStatus.VISITED,
                "ooooooo",
                "ooooooo",
                "oowwwoo",
                "oowwwoo",
                "oowwwoo",
                "ooooooo",
                "ooooooo");
        badge(StructureStatus.LOOTED,
                "ooooooo",
                "ooooooo",
                "ooooowo",
                "oooowoo",
                "owowooo",
                "oowoooo",
                "ooooooo");
        badge(StructureStatus.EMPTY,
                "ooooooo",
                "owwwwwo",
                "owooowo",
                "owooowo",
                "owooowo",
                "owwwwwo",
                "ooooooo");
        badge(StructureStatus.DESTROYED,
                "ooooooo",
                "owooowo",
                "oowowoo",
                "ooowooo",
                "oowowoo",
                "owooowo",
                "ooooooo");
    }

    private MarkerSymbols() {
    }

    private static void symbol(MarkerType type, String... rows) {
        SYMBOLS.put(type, check(rows, SYMBOL_SIZE));
        SYMBOL_RUNS.put(type, runs(rows));
    }

    private static void badge(StructureStatus status, String... rows) {
        BADGES.put(status, check(rows, BADGE_SIZE));
        BADGE_RUNS.put(status, runs(rows));
    }

    private static String[] check(String[] rows, int size) {
        if (rows.length != size) {
            throw new IllegalStateException("a mask must be " + size + " rows");
        }
        for (String row : rows) {
            if (row.length() != size) {
                throw new IllegalStateException("a mask row must be " + size + " wide: " + row);
            }
        }
        return rows;
    }

    /** A marker type's colour, its silhouette's fill. */
    public static int colorOf(MarkerType type) {
        switch (type) {
            case BASE:
                return 0xFFE9C46A;
            case STASH:
                return 0xFFB5793D;
            case PORTAL:
                return 0xFF9B5DE5;
            case FARM:
                return 0xFF7BC96F;
            case DANGER:
                return 0xFFE5484D;
            case RESOURCE:
                return 0xFF4FD1D9;
            case PLAYER_BASE:
                return 0xFF6FA8FF;
            default:
                return 0xFFF28CB1;
        }
    }

    /** The colour a status badge's glyph is drawn in; the glyph's shape is what tells them apart. */
    public static int glyphColorOf(StructureStatus status) {
        switch (status) {
            case LOOTED:
                return 0xFFFFD166;
            case EMPTY:
                return 0xFFC7CED6;
            case DESTROYED:
                return 0xFFFF6B6B;
            default:
                return 0xFFFFFFFF;
        }
    }

    /** The mask of a marker type, row by row. */
    static String[] symbolMask(MarkerType type) {
        return SYMBOLS.get(type).clone();
    }

    /** The mask of a status badge, or {@code null} for {@link StructureStatus#UNVISITED}, which has none. */
    static String[] badgeMask(StructureStatus status) {
        String[] mask = BADGES.get(status);
        return mask == null ? null : mask.clone();
    }

    /** How many fills a marker symbol costs. */
    public static int symbolFills(MarkerType type) {
        return SYMBOL_RUNS.get(type).length / 4;
    }

    /** How many fills a status badge costs; none for unvisited. */
    public static int badgeFills(StructureStatus status) {
        int[] runs = BADGE_RUNS.get(status);
        return runs == null ? 0 : runs.length / 4;
    }

    /**
     * Draws a marker type's symbol centred on a pixel.
     *
     * @return the fills used
     */
    public static int drawSymbol(MapCanvas canvas, MarkerType type, int centerX, int centerY) {
        return draw(canvas, SYMBOL_RUNS.get(type), centerX - SYMBOL_SIZE / 2, centerY - SYMBOL_SIZE / 2,
                colorOf(type), ACCENT);
    }

    /**
     * Draws a status badge centred on a pixel; nothing for unvisited.
     *
     * @return the fills used
     */
    public static int drawBadge(MapCanvas canvas, StructureStatus status, int centerX, int centerY) {
        int[] runs = BADGE_RUNS.get(status);
        if (runs == null) {
            return 0;
        }
        return draw(canvas, runs, centerX - BADGE_SIZE / 2, centerY - BADGE_SIZE / 2, OUTLINE,
                glyphColorOf(status));
    }

    private static int draw(MapCanvas canvas, int[] runs, int left, int top, int fill, int accent) {
        for (int i = 0; i < runs.length; i += 4) {
            int x = left + runs[i];
            int y = top + runs[i + 1];
            canvas.fill(x, y, x + runs[i + 2], y + 1, colorFor(runs[i + 3], fill, accent));
        }
        return runs.length / 4;
    }

    private static int colorFor(int symbol, int fill, int accent) {
        if (symbol == 'o') {
            return OUTLINE;
        }
        return symbol == 'w' ? accent : fill;
    }

    /**
     * A mask as runs: for every maximal horizontal stretch of one drawn character, its x, y, width
     * and character, four ints each.
     */
    static int[] runs(String[] rows) {
        List<int[]> runs = new ArrayList<int[]>();
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            int x = 0;
            while (x < row.length()) {
                char c = row.charAt(x);
                int start = x;
                while (x < row.length() && row.charAt(x) == c) {
                    x++;
                }
                if (c != '.') {
                    runs.add(new int[] {start, y, x - start, c});
                }
            }
        }
        int[] packed = new int[runs.size() * 4];
        for (int i = 0; i < runs.size(); i++) {
            System.arraycopy(runs.get(i), 0, packed, i * 4, 4);
        }
        return packed;
    }
}
