package com.scrimchic.seedchecker.gui.map.layer;

import com.scrimchic.seedchecker.world.ActiveWorld;
import com.scrimchic.seedchecker.worldgen.StructureType;
import com.scrimchic.seedchecker.worldgen.StructureValidation;

/**
 * A layer whose markers are structures the map screen can select, navigate to and measure.
 *
 * <p>Grid structures and strongholds are placed by entirely different algorithms, but once a
 * structure is on the map the screen needs the same four answers from either.
 */
public interface StructureMarkerLayer extends MapLayer {

    StructureType type();

    /** Whether this layer draws a marker for a structure starting in that chunk right now. */
    boolean isMarkerAt(ActiveWorld world, int chunkX, int chunkZ);

    /** @return what is known about the structure in that chunk, or {@code null} while unknown. */
    StructureValidation resultAt(ActiveWorld world, int chunkX, int chunkZ);

    /** One line for the debug readout under the cursor, or {@code null} for no structure there. */
    String describeAt(ActiveWorld world, int chunkX, int chunkZ);

    /** An extra line for the selection panel, such as a stronghold's number, or {@code null}. */
    String describeSelection(ActiveWorld world, int chunkX, int chunkZ);
}
