package dev.santavibes.tv

import dev.santavibes.core.Profile

/**
 * A change from the control page or the remote (3.2), applied on the GL thread at the next frame:
 * scene (crossfading when it changes), its base values packed as [SceneSlot.values], the palette
 * leading the rotation (null = the scene's own), palette rotation and the output grade.
 */
class SceneControl(
    val sceneIndex: Int, val values: FloatArray, val palette: List<FloatArray>?, val paletteRotate: Boolean, val grade: Profile,
    /** Bumped when the palettes must be rebuilt (lead palette or image sources changed). */
    val paletteEpoch: Int,
)
