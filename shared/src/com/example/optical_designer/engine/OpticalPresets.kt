package com.example.optical_designer.engine

import com.example.optical_designer.model.GlassMaterial
import com.example.optical_designer.model.LensOrientation
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

    val LED_COLLIMATOR_1 = OpticalPreset(
        name = "Led collimator 1",
        description = "Green 1x1 mm LED collimated by a 25 mm Plano-Convex lens at 14.5 mm (Flat face left).",
        source = LightSource(
            name = "520nm Green LED",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            opticalPowerW = 1.0,
            divergenceXDeg = 60.0,
            divergenceYDeg = 60.0,
            emitterWidthMm = 1.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        ),
        elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Plano-Convex Lens (25mm)",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 14.5,
                clearApertureMm = 25.0,
                focalLengthMm = 25.0,
                material = GlassMaterial.N_BK7,
                orientation = LensOrientation.FLAT_FACE_LEFT
            )
        )
    )

    val LED_COLLIMATOR_2 = OpticalPreset(
        name = "Led collimator 2",
        description = "Green 1x1 mm LED collimated by a 25 mm Plano-Convex lens at 25 mm.",
        source = LightSource(
            name = "520nm Green LED",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            opticalPowerW = 1.0,
            divergenceXDeg = 60.0,
            divergenceYDeg = 60.0,
            emitterWidthMm = 1.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        ),
        elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Plano-Convex Lens (25mm)",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 25.0,
                clearApertureMm = 25.0,
                focalLengthMm = 25.0,
                material = GlassMaterial.N_BK7,
                orientation = LensOrientation.FLAT_FACE_LEFT
            )
        )
    )

    val ALL_PRESETS = listOf(
        LED_COLLIMATOR_1,
        LED_COLLIMATOR_2
    )
}
