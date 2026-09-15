package com.scrimchic.seedchecker.gui.map;

import com.scrimchic.seedchecker.gui.map.layer.StructureMarkerLayer;
import com.scrimchic.seedchecker.worldgen.biome.BiomeMapKey;

/**
 * What is selected on the map: a predicted structure - grid placed or a stronghold, through its
 * layer - or a custom marker. Immutable; the screen replaces it.
 *
 * <p>A structure is identified the way its layer draws it: layer, start chunk, and the map it was
 * predicted on, so a seed or world change can tell it is gone. A marker is identified by its id
 * alone, and its data is looked up fresh every time, so an edit shows at once and a deleted marker
 * is noticed.
 */
public final class MapSelection {

    public enum Kind {
        STRUCTURE,
        MARKER
    }

    private final Kind kind;
    private final StructureMarkerLayer layer;
    private final int chunkX;
    private final int chunkZ;
    private final BiomeMapKey map;
    private final String markerId;

    private MapSelection(Kind kind, StructureMarkerLayer layer, int chunkX, int chunkZ, BiomeMapKey map,
                         String markerId) {
        this.kind = kind;
        this.layer = layer;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.map = map;
        this.markerId = markerId;
    }

    public static MapSelection structure(StructureMarkerLayer layer, int chunkX, int chunkZ, BiomeMapKey map) {
        return new MapSelection(Kind.STRUCTURE, layer, chunkX, chunkZ, map, null);
    }

    public static MapSelection marker(String markerId) {
        return new MapSelection(Kind.MARKER, null, 0, 0, null, markerId);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isStructure() {
        return kind == Kind.STRUCTURE;
    }

    public boolean isMarker() {
        return kind == Kind.MARKER;
    }

    /** The structure's layer; {@code null} for a marker. */
    public StructureMarkerLayer layer() {
        return layer;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** The map the structure was predicted on; {@code null} for a marker. */
    public BiomeMapKey map() {
        return map;
    }

    /** The marker's id; {@code null} for a structure. */
    public String markerId() {
        return markerId;
    }
}
