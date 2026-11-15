package com.example.optical_designer

import com.example.optical_designer.engine.AnalyticalOpticsEngine
import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.model.GlassMaterial
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.SourceType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OpticsEngineTest {

    @Test
    fun `test default LED collimator efficiency is exactly 24 percent`() {
        val preset = OpticalPresets.LED_COLLIMATOR
        val result = AnalyticalOpticsEngine.calculateSystem(preset.source, preset.elements)

        // Area collection efficiency = (15mm / 29.37mm)^2 ≈ 26.09%
        // Fresnel transmission (N-BK7, R = 4% x 2 surfaces) = 92%
        // Net efficiency = 26.09% * 92% = 24.0%
        val expectedEfficiencyPct = 24.0
        assertTrue(
            abs(result.opticalEfficiencyPct - expectedEfficiencyPct) < 0.5,
            "Default setup efficiency should be ~24.0%, but was ${result.opticalEfficiencyPct}%"
        )
    }

    @Test
    fun `test LED collimation divergence is small when lens is at focal plane`() {
        val source = LightSource(
            name = "520nm Green LED",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceXDeg = 60.0,
            divergenceYDeg = 60.0,
            emitterWidthMm = 1.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        val elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Plano-Convex Lens",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 50.0,
                clearApertureMm = 30.0,
                focalLengthMm = 50.0,
                thicknessMm = 6.0
            )
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, elements)

        // Expected output divergence: 2 * arctan(0.5mm / 50mm) = 2 * arctan(0.01) ≈ 1.146°
        val expectedDivergenceDeg = 1.146
        assertTrue(
            abs(result.outputDivergenceDeg - expectedDivergenceDeg) < 0.1,
            "Output divergence should be ~1.15°, but was ${result.outputDivergenceDeg}°"
        )
    }

    @Test
    fun `test laser diode collimation produces sub-degree divergence`() {
        val source = LightSource(
            name = "650nm Red Laser Diode",
            sourceType = SourceType.LASER_DIODE,
            cwlNm = 650.0,
            divergenceXDeg = 30.0,
            divergenceYDeg = 12.0,
            emitterWidthMm = 0.1,
            emitterHeightMm = 0.1,
            positionZMm = 0.0
        )
        val elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Biconvex Lens",
                type = OpticalElementType.BICONVEX,
                positionZMm = 30.0,
                clearApertureMm = 20.0,
                focalLengthMm = 30.0,
                thicknessMm = 5.0
            )
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, elements)

        // Expected output divergence: 2 * arctan(0.05mm / 30mm) = 2 * arctan(0.001667) ≈ 0.191°
        val expectedDivergenceDeg = 0.191
        assertTrue(
            abs(result.outputDivergenceDeg - expectedDivergenceDeg) < 0.05,
            "Output divergence should be ~0.19°, but was ${result.outputDivergenceDeg}°"
        )
    }

    @Test
    fun `test defocused lens increases output divergence`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 60.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        // Defocused lens: placed at z = 30mm instead of focal length f = 50mm
        val elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Plano-Convex Lens",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 30.0,
                clearApertureMm = 40.0,
                focalLengthMm = 50.0,
                thicknessMm = 6.0
            )
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, elements)

        // Defocused lens output divergence should be significantly higher than collimated (~20.9°)
        assertTrue(
            result.outputDivergenceDeg > 15.0,
            "Defocused lens output divergence should be > 15°, was ${result.outputDivergenceDeg}°"
        )
    }

    @Test
    fun `test aperture clipping reduces optical efficiency`() {
        val source = LightSource(
            name = "Wide LED",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 60.0, // beam radius at z=50mm is ~29.37mm
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        // Small lens aperture 15mm radius (30mm diameter)
        val elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Small Aperture Lens",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 50.0,
                clearApertureMm = 30.0,
                focalLengthMm = 50.0,
                material = GlassMaterial(name = "Ideal", nd = 1.5, vd = 60.0, surfaceReflectance = 0.0)
            )
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, elements)

        // Expected efficiency: (15.0 / 29.37)^2 ≈ 26.1%
        assertTrue(
            result.opticalEfficiencyPct in 20.0..30.0,
            "Efficiency should be clipped to ~26%, but was ${result.opticalEfficiencyPct}%"
        )
    }
}
