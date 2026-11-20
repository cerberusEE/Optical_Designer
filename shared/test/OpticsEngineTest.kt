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
    fun `test default LED collimator efficiency is 27 percent with physical lens thickness`() {
        val preset = OpticalPresets.LED_COLLIMATOR
        val result = AnalyticalOpticsEngine.calculateSystem(preset.source, preset.elements)

        // Lens front surface at z = 47mm (Z=50mm - thickness/2).
        // Beam radius at front surface = 0.5mm + 47mm * tan(30°) ≈ 27.64mm.
        // Area collection efficiency = (15mm / 27.64mm)^2 ≈ 29.46%
        // Fresnel transmission (N-BK7, R = 4% x 2 surfaces) = 92%
        // Net efficiency = 29.46% * 92% ≈ 27.1%
        val expectedEfficiencyPct = 27.1
        assertTrue(
            abs(result.opticalEfficiencyPct - expectedEfficiencyPct) < 1.0,
            "Default setup efficiency should be ~27.1%, but was ${result.opticalEfficiencyPct}%"
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
                focalLengthMm = 50.0
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
                focalLengthMm = 30.0
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
                focalLengthMm = 50.0
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

    @Test
    fun `test radiant intensity and irradiance at target distance`() {
        val source = LightSource(
            name = "1W LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            opticalPowerW = 1.0,
            divergenceYDeg = 60.0,
            emitterHeightMm = 1.0
        )
        val elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Collimating Lens",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 50.0,
                clearApertureMm = 30.0,
                focalLengthMm = 50.0
            )
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, elements)
        val irradianceAt1m = AnalyticalOpticsEngine.calculateIrradianceAtRange(result, 1000.0)

        assertTrue(result.outputPowerW > 0.20, "Output power should be ~0.24W")
        assertTrue(result.radiantIntensityWsr > 500.0, "Radiant intensity should be > 500 W/sr due to collimation")
        assertTrue(irradianceAt1m.irradianceWm2 > 100.0, "Irradiance at 1m should be > 100 W/m²")
    }

    @Test
    fun `test light propagation terminates at detector screen`() {
        val preset = OpticalPresets.LIGHT_COLLECTING_DETECTOR
        val result = AnalyticalOpticsEngine.calculateSystem(preset.source, preset.elements)

        val detectorZ = preset.elements.first { it.type == OpticalElementType.DETECTOR }.positionZMm
        val lastSegment = result.beamSegments.last()

        assertTrue(
            abs(lastSegment.endZ - detectorZ) < 1e-4,
            "Light propagation should terminate exactly at detector Z=$detectorZ mm, but ended at Z=${lastSegment.endZ} mm"
        )
        assertTrue(result.detectorResult != null, "Detector result must be present when a Detector Screen is enabled")
    }

    @Test
    fun `test detector screen optical efficiency and integrated irradiance`() {
        val preset = OpticalPresets.LIGHT_COLLECTING_DETECTOR
        val result = AnalyticalOpticsEngine.calculateSystem(preset.source, preset.elements)

        val detRes = result.detectorResult!!
        assertTrue(detRes.landedPowerW > 0.0, "Landed power on detector screen should be > 0")
        assertTrue(detRes.opticalEfficiencyPct > 0.0, "Detector optical efficiency should be > 0%")
        assertTrue(
            abs(result.opticalEfficiencyPct - detRes.opticalEfficiencyPct) < 1e-4,
            "System efficiency should equal detector optical efficiency when detector screen is present"
        )
        assertTrue(detRes.integratedIrradianceWm2 > 0.0, "Integrated irradiance over circular screen should be > 0 W/m²")
    }

    @Test
    fun `test detector aperture spillover loss when spot diameter exceeds detector size`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            opticalPowerW = 1.0,
            divergenceYDeg = 40.0,
            emitterHeightMm = 1.0
        )
        // Small detector screen (Ø 5mm) placed far from lens (z = 150mm where spot is large)
        val elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Lens",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 50.0,
                clearApertureMm = 40.0,
                focalLengthMm = 50.0
            ),
            OpticalElement(
                id = "detector_1",
                name = "Small Detector Screen",
                type = OpticalElementType.DETECTOR,
                positionZMm = 150.0,
                clearApertureMm = 5.0 // Small aperture -> spillover
            )
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, elements)
        val detRes = result.detectorResult!!

        assertTrue(detRes.spotDiameterMm > detRes.detectorApertureMm, "Spot diameter should exceed detector aperture")
        assertTrue(detRes.spilloverLossPct > 10.0, "Spillover loss should be > 10%, was ${detRes.spilloverLossPct}%")
    }

    @Test
    fun `test CIE 1931 color matching for visible spectrum wavelengths`() {
        val greenCol = com.example.optical_designer.model.Wavelength(520.0).toColor(isDarkTheme = true)
        val redCol = com.example.optical_designer.model.Wavelength(650.0).toColor(isDarkTheme = true)
        val blueCol = com.example.optical_designer.model.Wavelength(450.0).toColor(isDarkTheme = true)

        assertTrue(greenCol.green > greenCol.red && greenCol.green > greenCol.blue, "520nm should be predominantly green")
        assertTrue(redCol.red > redCol.green && redCol.red > redCol.blue, "650nm should be predominantly red")
        assertTrue(blueCol.blue > blueCol.red, "450nm should have strong blue component")
    }

    @Test
    fun `test non-visible wavelengths produce neutral gray in dark and light themes`() {
        val uvDarkCol = com.example.optical_designer.model.Wavelength(250.0).toColor(isDarkTheme = true)
        val lwirDarkCol = com.example.optical_designer.model.Wavelength(10600.0).toColor(isDarkTheme = true)

        val uvLightCol = com.example.optical_designer.model.Wavelength(250.0).toColor(isDarkTheme = false)
        val lwirLightCol = com.example.optical_designer.model.Wavelength(10600.0).toColor(isDarkTheme = false)

        // UV and LWIR in dark theme produce light neutral gray (0xFFB0BEC5)
        assertTrue(uvDarkCol == androidx.compose.ui.graphics.Color(0xFFB0BEC5), "UV in dark theme should be light gray")
        assertTrue(lwirDarkCol == androidx.compose.ui.graphics.Color(0xFFB0BEC5), "LWIR in dark theme should be light gray")

        // UV and LWIR in light theme produce medium-dark neutral gray (0xFF546E7A)
        assertTrue(uvLightCol == androidx.compose.ui.graphics.Color(0xFF546E7A), "UV in light theme should be medium-dark gray")
        assertTrue(lwirLightCol == androidx.compose.ui.graphics.Color(0xFF546E7A), "LWIR in light theme should be medium-dark gray")
    }

    @Test
    fun `test optical materials refractive indices`() {
        assertTrue(abs(GlassMaterial.FUSED_SILICA.nd - 1.4585) < 1e-4, "Fused Silica nd should be 1.4585")
        assertTrue(abs(GlassMaterial.GERMANIUM.nd - 4.0028) < 1e-4, "Germanium nd should be 4.0028")
        assertTrue(abs(GlassMaterial.SILICON.nd - 3.4223) < 1e-4, "Silicon nd should be 3.4223")
        assertTrue(abs(GlassMaterial.ZNSE.nd - 2.4028) < 1e-4, "ZnSe nd should be 2.4028")
    }

    @Test
    fun `test custom AR coating increases optical throughput efficiency`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 20.0,
            emitterHeightMm = 1.0
        )
        val uncoatedLens = OpticalElement(
            id = "lens_uncoated",
            name = "Uncoated Lens",
            type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 50.0,
            clearApertureMm = 40.0,
            focalLengthMm = 50.0,
            material = GlassMaterial.N_BK7,
            isCoated = false
        )
        val coatedLens = uncoatedLens.copy(
            id = "lens_coated",
            name = "AR Coated Lens",
            isCoated = true,
            customTransmissionPct = 99.5
        )

        val uncoatedResult = AnalyticalOpticsEngine.calculateSystem(source, listOf(uncoatedLens))
        val coatedResult = AnalyticalOpticsEngine.calculateSystem(source, listOf(coatedLens))

        assertTrue(
            coatedResult.opticalEfficiencyPct > uncoatedResult.opticalEfficiencyPct,
            "AR coated lens efficiency (${coatedResult.opticalEfficiencyPct}%) should be higher than uncoated (${uncoatedResult.opticalEfficiencyPct}%)"
        )
    }

    @Test
    fun `test compound optics with parallel rays into 100mm lens focuses to 200mm`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 60.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        val lens1 = OpticalElement(
            id = "lens_1",
            name = "25mm Lens L1",
            type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 25.0,
            clearApertureMm = 20.0,
            focalLengthMm = 25.0
        )
        val lens2 = OpticalElement(
            id = "lens_2",
            name = "100mm Lens L2",
            type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 100.0,
            clearApertureMm = 40.0,
            focalLengthMm = 100.0
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens1, lens2))

        // Lens 1 at Z=25mm (f1=25mm) collimates the light into parallel rays.
        // Lens 2 at Z=100mm (f2=100mm) receives parallel rays and focuses them to Z = 100 + 100 = 200mm.
        assertTrue(
            abs(result.focalDistanceMm - 200.0) < 1.0,
            "Compound lens L2 should focus parallel rays from L1 to Z = 200mm, but focal distance was ${result.focalDistanceMm}mm"
        )
    }

    @Test
    fun `test 3-lens relay system with arbitrary distances`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 40.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        val lens1 = OpticalElement(
            id = "l1", name = "L1 Collimator", type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 50.0, clearApertureMm = 40.0, focalLengthMm = 50.0
        )
        val lens2 = OpticalElement(
            id = "l2", name = "L2 Focus Lens", type = OpticalElementType.BICONVEX,
            positionZMm = 150.0, clearApertureMm = 40.0, focalLengthMm = 50.0
        )
        val lens3 = OpticalElement(
            id = "l3", name = "L3 Relay Lens", type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 250.0, clearApertureMm = 40.0, focalLengthMm = 50.0
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens1, lens2, lens3))

        // L1 at Z=50 (f=50) collimates. L2 at Z=150 (f=50) focuses to Z=200. L3 at Z=250 (f=50) re-collimates from Z=200.
        assertTrue(result.outputDivergenceDeg < 3.0, "Relay system output divergence should be small (<3°), was ${result.outputDivergenceDeg}°")
        assertTrue(result.beamSegments.size >= 3, "Beam segments should cover propagation through all 3 relay lenses")
    }

    @Test
    fun `test Galilean beam expander with negative and positive lenses`() {
        val source = LightSource(
            name = "Collimated Laser",
            sourceType = SourceType.COLLIMATED,
            cwlNm = 632.8,
            divergenceYDeg = 0.3,
            emitterHeightMm = 3.0,
            positionZMm = 0.0
        )
        val negLens = OpticalElement(
            id = "l1_neg", name = "Diverging Lens L1", type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 20.0, clearApertureMm = 20.0, focalLengthMm = -25.0
        )
        val posLens = OpticalElement(
            id = "l2_pos", name = "Collimating Lens L2", type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 70.0, clearApertureMm = 40.0, focalLengthMm = 75.0
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, listOf(negLens, posLens))

        // Galilean expander magnification M = f2 / |f1| = 75 / 25 = 3x.
        // Output divergence should be reduced by 3x (~0.1°).
        assertTrue(result.outputDivergenceDeg < 0.2, "Galilean expander should reduce divergence to <0.2°, was ${result.outputDivergenceDeg}°")
        assertTrue(result.opticalEfficiencyPct > 80.0, "Galilean expander should maintain high efficiency (>80%)")
    }

    @Test
    fun `test 4-lens complex optical system execution and metrics`() {
        val source = LightSource(
            name = "Point Source",
            sourceType = SourceType.POINT_SOURCE,
            cwlNm = 520.0,
            divergenceYDeg = 30.0,
            emitterHeightMm = 0.2,
            positionZMm = 0.0
        )
        val elements = listOf(
            OpticalElement(id = "e1", name = "L1", type = OpticalElementType.PLANO_CONVEX, positionZMm = 30.0, clearApertureMm = 30.0, focalLengthMm = 30.0),
            OpticalElement(id = "e2", name = "Iris", type = OpticalElementType.APERTURE_STOP, positionZMm = 70.0, clearApertureMm = 15.0),
            OpticalElement(id = "e3", name = "L2", type = OpticalElementType.ACHROMATIC_DOUBLET, positionZMm = 120.0, clearApertureMm = 35.0, focalLengthMm = 60.0),
            OpticalElement(id = "e4", name = "L3", type = OpticalElementType.BICONVEX, positionZMm = 200.0, clearApertureMm = 40.0, focalLengthMm = 80.0)
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, elements)

        assertTrue(result.opticalEfficiencyPct > 0.0, "Complex 4-element system should yield positive efficiency")
        assertTrue(result.outputDivergenceDeg in 0.0..90.0, "Output divergence should be physically valid")
        assertTrue(result.sphericalAberrationMm > 0.0, "Spherical aberration should accumulate across elements")
    }

    @Test
    fun `test multi-aperture cascading clipping efficiency`() {
        val source = LightSource(
            name = "Wide LED",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 60.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        val lens1 = OpticalElement(id = "l1", name = "L1", type = OpticalElementType.PLANO_CONVEX, positionZMm = 50.0, clearApertureMm = 40.0, focalLengthMm = 50.0)
        val iris = OpticalElement(id = "iris", name = "Small Iris Stop", type = OpticalElementType.APERTURE_STOP, positionZMm = 100.0, clearApertureMm = 10.0)
        val lens2 = OpticalElement(id = "l2", name = "L2", type = OpticalElementType.BICONVEX, positionZMm = 150.0, clearApertureMm = 40.0, focalLengthMm = 50.0)

        val resultWithoutIris = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens1, lens2))
        val resultWithIris = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens1, iris, lens2))

        assertTrue(
            resultWithIris.opticalEfficiencyPct < resultWithoutIris.opticalEfficiencyPct,
            "Cascading iris stop should clip light and reduce net efficiency (${resultWithIris.opticalEfficiencyPct}% < ${resultWithoutIris.opticalEfficiencyPct}%)"
        )
    }
}
