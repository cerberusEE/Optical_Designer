package com.example.optical_designer.engine

import com.example.optical_designer.model.GlassMaterial
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.SourceType

data class OpticalPreset(
    val name: String,
    val description: String,
    val source: LightSource,
    val elements: List<OpticalElement>
)

object OpticalPresets {

    val LED_COLLIMATOR = OpticalPreset(
        name = "LED Collimator (Plano-Convex)",
        description = "High divergence green LED collimated by a Plano-Convex glass lens.",
        source = LightSource(
            name = "520nm Green LED",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceXDeg = 60.0,
            divergenceYDeg = 60.0,
            emitterWidthMm = 1.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        ),
        elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Plano-Convex Lens",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 50.0,
                clearApertureMm = 30.0,
                focalLengthMm = 50.0,
                thicknessMm = 6.0,
                material = GlassMaterial.N_BK7
            )
        )
    )

    val ACHROMATIC_DOUBLET_PRESET = OpticalPreset(
        name = "Achromatic Doublet (Low Aberration)",
        description = "Green source with Crown-Flint doublet focusing optic.",
        source = LightSource(
            name = "532nm Green Source",
            sourceType = SourceType.LED,
            cwlNm = 532.0,
            divergenceXDeg = 45.0,
            divergenceYDeg = 45.0,
            emitterWidthMm = 1.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        ),
        elements = listOf(
            OpticalElement(
                id = "doublet_1",
                name = "Achromatic Doublet",
                type = OpticalElementType.ACHROMATIC_DOUBLET,
                positionZMm = 60.0,
                clearApertureMm = 35.0,
                focalLengthMm = 60.0,
                thicknessMm = 8.0,
                material = GlassMaterial.N_BK7
            )
        )
    )

    val LASER_DIODE_COLLIMATOR = OpticalPreset(
        name = "Laser Diode Collimation",
        description = "Red laser diode with fast divergence collimated into parallel beam.",
        source = LightSource(
            name = "650nm Red Laser Diode",
            sourceType = SourceType.LASER_DIODE,
            cwlNm = 650.0,
            divergenceXDeg = 30.0,
            divergenceYDeg = 12.0,
            emitterWidthMm = 0.1,
            emitterHeightMm = 0.1,
            positionZMm = 0.0
        ),
        elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Biconvex Collimator Lens",
                type = OpticalElementType.BICONVEX,
                positionZMm = 30.0,
                clearApertureMm = 20.0,
                focalLengthMm = 30.0,
                thicknessMm = 5.0,
                material = GlassMaterial.N_BK7
            )
        )
    )

    val PARABOLIC_MIRROR_PRESET = OpticalPreset(
        name = "Off-Axis Parabolic Mirror",
        description = "Reflective parabolic optic focusing monochromatic light.",
        source = LightSource(
            name = "520nm Laser",
            sourceType = SourceType.POINT_SOURCE,
            cwlNm = 520.0,
            divergenceXDeg = 40.0,
            divergenceYDeg = 40.0,
            emitterWidthMm = 0.2,
            emitterHeightMm = 0.2,
            positionZMm = 0.0
        ),
        elements = listOf(
            OpticalElement(
                id = "mirror_1",
                name = "Parabolic Mirror",
                type = OpticalElementType.PARABOLIC_MIRROR,
                positionZMm = 50.0,
                clearApertureMm = 40.0,
                focalLengthMm = 50.0
            )
        )
    )

    val BEAM_EXPANDER = OpticalPreset(
        name = "Keplerian Beam Expander",
        description = "Two lenses expanding a collimated laser beam 3x.",
        source = LightSource(
            name = "450nm Blue Laser",
            sourceType = SourceType.COLLIMATED,
            cwlNm = 450.0,
            divergenceXDeg = 2.0,
            divergenceYDeg = 2.0,
            emitterWidthMm = 4.0,
            emitterHeightMm = 4.0,
            positionZMm = 0.0
        ),
        elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Focusing Lens L1",
                type = OpticalElementType.BICONVEX,
                positionZMm = 20.0,
                clearApertureMm = 15.0,
                focalLengthMm = 25.0,
                thicknessMm = 4.0,
                material = GlassMaterial.N_BK7
            ),
            OpticalElement(
                id = "lens_2",
                name = "Collimating Lens L2",
                type = OpticalElementType.BICONVEX,
                positionZMm = 120.0,
                clearApertureMm = 40.0,
                focalLengthMm = 75.0,
                thicknessMm = 8.0,
                material = GlassMaterial.N_BK7
            )
        )
    )

    val ALL_PRESETS = listOf(
        LED_COLLIMATOR,
        ACHROMATIC_DOUBLET_PRESET,
        LASER_DIODE_COLLIMATOR,
        PARABOLIC_MIRROR_PRESET,
        BEAM_EXPANDER
    )
}
