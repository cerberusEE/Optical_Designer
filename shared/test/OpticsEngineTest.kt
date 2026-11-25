import androidx.compose.ui.graphics.Color
import com.example.optical_designer.engine.AnalyticalOpticsEngine
import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.model.GlassMaterial
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.SourceType
import com.example.optical_designer.model.Wavelength
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpticsEngineTest {

    @Test
    fun `test default LED collimator efficiency with planar front surface at Z=50mm`() {
        val preset = OpticalPresets.LED_COLLIMATOR
        val result = AnalyticalOpticsEngine.calculateSystem(preset.source, preset.elements)

        // Plano-convex lens flat front surface at z = 50mm.
        // Beam radius at flat surface = 0.5mm + 50mm * tan(30°) ≈ 29.37mm.
        // Radiometric Lambertian collection efficiency ≈ 31.77%
        // Fresnel & internal transmission (N-BK7) ≈ 91.26%
        // Net efficiency = 31.77% * 91.26% ≈ 29.0%
        val expectedEfficiencyPct = 29.0
        assertTrue(
            abs(result.opticalEfficiencyPct - expectedEfficiencyPct) < 2.0,
            "Default setup efficiency should be ~29.0%, but was ${result.opticalEfficiencyPct}%"
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

        // Expected output divergence: FWHM for LED die emitter = ~0.81°
        assertTrue(
            result.outputDivergenceDeg in 0.5..1.5,
            "Output divergence should be sub-degree (~0.81°), but was ${result.outputDivergenceDeg}°"
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

        // FWHM output divergence for Gaussian laser diode: ~0.11° (1/e² full angle ≈ 0.191°)
        assertTrue(
            result.outputDivergenceDeg in 0.05..0.25,
            "FWHM output divergence should be sub-degree (~0.11°), but was ${result.outputDivergenceDeg}°"
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

        // Expected efficiency: ~31.8%
        assertTrue(
            result.opticalEfficiencyPct in 20.0..35.0,
            "Efficiency should be clipped to ~31.8%, but was ${result.opticalEfficiencyPct}%"
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
            detRes.opticalEfficiencyPct <= result.opticalEfficiencyPct + 1e-4,
            "Detector optical efficiency should be <= system throughput efficiency (${detRes.opticalEfficiencyPct}% <= ${result.opticalEfficiencyPct}%)"
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
        val greenCol = Wavelength(520.0).toColor(isDarkTheme = true)
        val redCol = Wavelength(650.0).toColor(isDarkTheme = true)
        val blueCol = Wavelength(450.0).toColor(isDarkTheme = true)

        assertTrue(greenCol.green > greenCol.red && greenCol.green > greenCol.blue, "520nm should be predominantly green")
        assertTrue(redCol.red > redCol.green && redCol.red > redCol.blue, "650nm should be predominantly red")
        assertTrue(blueCol.blue > blueCol.red, "450nm should have strong blue component")
    }

    @Test
    fun `test non-visible wavelengths produce neutral gray in dark and light themes`() {
        val uvDarkCol = Wavelength(250.0).toColor(isDarkTheme = true)
        val lwirDarkCol = Wavelength(10600.0).toColor(isDarkTheme = true)

        val uvLightCol = Wavelength(250.0).toColor(isDarkTheme = false)
        val lwirLightCol = Wavelength(10600.0).toColor(isDarkTheme = false)

        // UV and LWIR in dark theme produce light neutral gray (0xFFB0BEC5)
        assertEquals(uvDarkCol, Color(0xFFB0BEC5), "UV in dark theme should be light gray")
        assertEquals(lwirDarkCol, Color(0xFFB0BEC5), "LWIR in dark theme should be light gray")

        // UV and LWIR in light theme produce medium-dark neutral gray (0xFF546E7A)
        assertEquals(uvLightCol, Color(0xFF546E7A), "UV in light theme should be medium-dark gray")
        assertEquals(
            lwirLightCol,
            Color(0xFF546E7A),
            "LWIR in light theme should be medium-dark gray"
        )
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
        // Lens 2 at Z=100mm (f2=100mm) receives parallel rays and focuses them with thick lens BFL to Z ≈ 201.88mm.
        assertTrue(
            abs(result.focalDistanceMm - 201.88) < 1.0,
            "Compound lens L2 should focus parallel rays from L1 to Z ≈ 201.88mm (thick lens BFL), but focal distance was ${result.focalDistanceMm}mm"
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

    @Test
    fun `test planoconvex lens planar side is at specified Z position and focal length measured from flat side`() {
        val source = LightSource(
            name = "Point Source",
            sourceType = SourceType.POINT_SOURCE,
            cwlNm = 520.0,
            divergenceYDeg = 0.0, // Parallel beam
            emitterHeightMm = 2.0,
            positionZMm = 0.0
        )
        val lensPositionZ = 60.0
        val focalLength = 50.0
        val pcLens = OpticalElement(
            id = "pc_lens",
            name = "Plano-Convex Lens",
            type = OpticalElementType.PLANO_CONVEX,
            positionZMm = lensPositionZ,
            clearApertureMm = 30.0,
            focalLengthMm = focalLength,
            isFlipped = false // Normal: Curved side facing incoming parallel light (left)
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, listOf(pcLens))

        // First segment is free-space propagation from source (Z=0) to curved front face (Z=lensPositionZ)
        val firstSegment = result.beamSegments.first()
        assertTrue(
            abs(firstSegment.endZ - lensPositionZ) < 1e-4,
            "Plano-convex front face must be at specified position Z=$lensPositionZ mm"
        )

        // For parallel light entering the curved side at Z=lensPositionZ, BFL = f - t/n. Focal point is at Z = lensPositionZ + thickness + BFL
        val expectedFocusZ = lensPositionZ + pcLens.thicknessMm + (focalLength - pcLens.thicknessMm / pcLens.material.nd)
        assertTrue(
            abs(result.focalDistanceMm - expectedFocusZ) < 1e-4,
            "Focal point for normal (curved-first) plano-convex lens must be at Z=$expectedFocusZ mm"
        )
    }

    @Test
    fun `test planoconvex lens orientation flip changes back focal length and focal point position`() {
        val source = LightSource(
            name = "Point Source",
            sourceType = SourceType.POINT_SOURCE,
            cwlNm = 520.0,
            divergenceYDeg = 0.0, // Parallel beam
            emitterHeightMm = 2.0,
            positionZMm = 0.0
        )
        val lensPositionZ = 60.0
        val focalLength = 50.0
        val pcLensNormal = OpticalElement(
            id = "pc_lens_norm",
            name = "Plano-Convex Lens Normal",
            type = OpticalElementType.PLANO_CONVEX,
            positionZMm = lensPositionZ,
            clearApertureMm = 30.0,
            focalLengthMm = focalLength,
            isFlipped = false // Curved side facing left (Normal)
        )
        val pcLensFlipped = pcLensNormal.copy(
            id = "pc_lens_flip",
            isFlipped = true // Flat side facing left (Flipped)
        )

        val resultNormal = AnalyticalOpticsEngine.calculateSystem(source, listOf(pcLensNormal))
        val resultFlipped = AnalyticalOpticsEngine.calculateSystem(source, listOf(pcLensFlipped))

        // Curved side facing left (Normal): BFL = f - t/n. Focal Z = zFront + t + (f - t/n)
        // Flat side facing left (Flipped): BFL = f. Focal Z = zFront + t + f
        val shiftMm = pcLensNormal.thicknessMm / pcLensNormal.material.nd
        assertTrue(
            abs((resultFlipped.focalDistanceMm - resultNormal.focalDistanceMm) - shiftMm) < 1e-3,
            "Flipping PCX lens orientation must shift Z focus position by t/n = $shiftMm mm, but shifted by ${resultFlipped.focalDistanceMm - resultNormal.focalDistanceMm} mm"
        )
    }

    @Test
    fun `test free space beam segment has straight sides without intermediate slope splits outside lenses`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 40.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        val lens1 = OpticalElement(
            id = "l1", name = "Lens L1", type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 10.0, clearApertureMm = 30.0, focalLengthMm = 15.0
        )
        val lens2 = OpticalElement(
            id = "l2", name = "Lens L2", type = OpticalElementType.BICONVEX,
            positionZMm = 60.0, clearApertureMm = 40.0, focalLengthMm = 50.0
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens1, lens2))

        val l1RearZ = lens1.positionZMm + lens1.thicknessMm
        val l2FrontZ = lens2.positionZMm - lens2.thicknessMm / 2.0

        val interLensSegments = result.beamSegments.filter {
            it.startZ >= (l1RearZ - 1e-3) && it.endZ <= (l2FrontZ + 1e-3)
        }

        assertTrue(
            interLensSegments.isNotEmpty() &&
            abs(interLensSegments.first().startZ - l1RearZ) < 1e-3 &&
            abs(interLensSegments.last().endZ - l2FrontZ) < 1e-3,
            "Free-space between Lens 1 and Lens 2 must span continuously from Z=$l1RearZ to Z=$l2FrontZ"
        )
    }

    @Test
    fun `test spillover light clipping starts new polygon at lens clear aperture`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 60.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        val lens = OpticalElement(
            id = "l1", name = "Small Lens", type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 50.0, clearApertureMm = 20.0, focalLengthMm = 50.0
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens))

        val preLensSeg = result.beamSegments.first { abs(it.endZ - 50.0) < 1e-3 }
        assertTrue(preLensSeg.endTopY > 10.0, "Arriving beam radius before Lens 1 should exceed aperture radius (10mm)")

        val lensSeg = result.beamSegments.first { abs(it.startZ - 50.0) < 1e-3 }
        assertTrue(
            abs(lensSeg.startTopY - 10.0) < 1e-3,
            "Polygon inside Lens 1 must start at clipped aperture radius (10mm), omitting spillover light"
        )
    }

    @Test
    fun `test moving downstream lens does not alter divergence or beam profile of upstream lens`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            divergenceYDeg = 40.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        val lens1 = OpticalElement(
            id = "l1", name = "Lens 1", type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 50.0, clearApertureMm = 30.0, focalLengthMm = 50.0
        )
        val lens2Near = OpticalElement(
            id = "l2", name = "Lens 2", type = OpticalElementType.BICONVEX,
            positionZMm = 120.0, clearApertureMm = 40.0, focalLengthMm = 50.0
        )
        val lens2Far = lens2Near.copy(positionZMm = 200.0)

        val resultNear = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens1, lens2Near))
        val resultFar = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens1, lens2Far))

        // First segment (source to Lens 1) must be identical
        val firstSegNear = resultNear.beamSegments.first()
        val firstSegFar = resultFar.beamSegments.first()

        assertEquals(firstSegNear.startTopY, firstSegFar.startTopY, 1e-4)
        assertEquals(firstSegNear.endTopY, firstSegFar.endTopY, 1e-4)

        // Segment inside Lens 1 must be identical
        val lens1SegNear = resultNear.beamSegments.first { abs(it.startZ - 50.0) < 1e-3 }
        val lens1SegFar = resultFar.beamSegments.first { abs(it.startZ - 50.0) < 1e-3 }

        assertEquals(lens1SegNear.startTopY, lens1SegFar.startTopY, 1e-4)
        assertEquals(lens1SegNear.endTopY, lens1SegFar.endTopY, 1e-4)

        // Slope of beam leaving Lens 1 must be identical
        val l1ExitSegNear = resultNear.beamSegments.first { abs(it.startZ - (50.0 + lens1.thicknessMm)) < 1e-3 }
        val l1ExitSegFar = resultFar.beamSegments.first { abs(it.startZ - (50.0 + lens1.thicknessMm)) < 1e-3 }

        val slopeNear = (l1ExitSegNear.endTopY - l1ExitSegNear.startTopY) / (l1ExitSegNear.endZ - l1ExitSegNear.startZ)
        val slopeFar = (l1ExitSegFar.endTopY - l1ExitSegFar.startTopY) / (l1ExitSegFar.endZ - l1ExitSegFar.startZ)

        assertTrue(
            abs(slopeNear - slopeFar) < 1e-4,
            "Moving Lens 2 must NOT alter the divergence slope leaving Lens 1 (near slope $slopeNear == far slope $slopeFar)"
        )
    }

    @Test
    fun `test detector power uses radiometric ray weighting rather than uniform radius squared scaling`() {
        val source = LightSource(
            name = "Gaussian Laser",
            sourceType = SourceType.LASER_DIODE,
            cwlNm = 650.0,
            opticalPowerW = 1.0,
            divergenceYDeg = 20.0,
            emitterHeightMm = 0.5
        )
        val elements = listOf(
            OpticalElement(
                id = "det_1",
                name = "Half-Size Detector",
                type = OpticalElementType.DETECTOR,
                positionZMm = 50.0,
                clearApertureMm = 10.0 // Smaller than beam diameter
            )
        )

        val result = AnalyticalOpticsEngine.calculateSystem(source, elements)
        val detRes = result.detectorResult!!

        // For a Gaussian profile, power encircled within half-radius is significantly higher
        // than simple (0.5)^2 = 25% uniform area scaling.
        assertTrue(
            detRes.encircledPowerPct > 30.0,
            "Gaussian encircled power fraction (${detRes.encircledPowerPct}%) should exceed uniform 25% area scaling"
        )
    }

    @Test
    fun `test aperture collection efficiency uses radiometric phase space ray weights`() {
        val ledSource = LightSource(
            name = "Lambertian LED",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            opticalPowerW = 1.0,
            divergenceYDeg = 60.0,
            emitterHeightMm = 1.0
        )
        // Lens collecting inner angle
        val elements = listOf(
            OpticalElement(
                id = "lens_1",
                name = "Aperture Lens",
                type = OpticalElementType.PLANO_CONVEX,
                positionZMm = 50.0,
                clearApertureMm = 30.0,
                focalLengthMm = 50.0,
                material = GlassMaterial(name = "Ideal", nd = 1.5, vd = 60.0, surfaceReflectance = 0.0)
            )
        )

        val result = AnalyticalOpticsEngine.calculateSystem(ledSource, elements)

        // Radiometric Lambertian collection efficiency for 30mm aperture at 50mm from 60° LED
        // is ~29.85%, differing from crude uniform area ratio (15/29.37)^2 ≈ 26.09%.
        assertTrue(
            result.geometricCollectionPct in 28.0..32.0,
            "Radiometric collection efficiency should be ~29.85%, was ${result.geometricCollectionPct}%"
        )
    }

    @Test
    fun `test output divergence is energy weighted and robust for Gaussian laser beams`() {
        val laserSource = LightSource(
            name = "Laser Diode",
            sourceType = SourceType.LASER_DIODE,
            cwlNm = 650.0,
            divergenceYDeg = 12.0,
            emitterHeightMm = 0.1,
            positionZMm = 0.0
        )
        val collimatingLens = OpticalElement(
            id = "lens_1",
            name = "Collimator",
            type = OpticalElementType.BICONVEX,
            positionZMm = 30.0,
            clearApertureMm = 20.0,
            focalLengthMm = 30.0
        )

        val result = AnalyticalOpticsEngine.calculateSystem(laserSource, listOf(collimatingLens))

        // Industry FWHM output divergence for Gaussian laser diode with thick lens principal plane shift: ~0.21°
        assertTrue(
            result.outputDivergenceDeg in 0.05..0.30,
            "FWHM output divergence should be sub-degree (~0.21°), was ${result.outputDivergenceDeg}°"
        )
    }

    @Test
    fun `test central rays for LED and point sources receive peak radiometric weights without central collapse`() {
        val ledSource = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            opticalPowerW = 1.0,
            divergenceYDeg = 60.0,
            emitterHeightMm = 1.0
        )
        val iris = OpticalElement(
            id = "iris",
            name = "Small Axis Iris",
            type = OpticalElementType.APERTURE_STOP,
            positionZMm = 50.0,
            clearApertureMm = 4.0
        )

        val result = AnalyticalOpticsEngine.calculateSystem(ledSource, listOf(iris))

        assertTrue(
            result.geometricCollectionPct > 0.0,
            "Central on-axis iris must collect non-zero power for LED source (${result.geometricCollectionPct}%)"
        )
    }

    @Test
    fun `test adding a detector screen at far range does not alter system output power efficiency or radiant intensity`() {
        val source = LightSource(
            name = "LED Source",
            sourceType = SourceType.LED,
            cwlNm = 520.0,
            opticalPowerW = 1.0,
            divergenceYDeg = 40.0,
            emitterHeightMm = 1.0,
            positionZMm = 0.0
        )
        val lens = OpticalElement(
            id = "lens_1",
            name = "Collimating Lens",
            type = OpticalElementType.PLANO_CONVEX,
            positionZMm = 50.0,
            clearApertureMm = 40.0,
            focalLengthMm = 50.0
        )
        val detectorAt1km = OpticalElement(
            id = "det_1km",
            name = "Detector Screen at 1km",
            type = OpticalElementType.DETECTOR,
            positionZMm = 1_000_000.0, // 1 km
            clearApertureMm = 10.0 // Small detector screen at 1 km
        )

        val systemWithoutDetector = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens))
        val systemWithDetector = AnalyticalOpticsEngine.calculateSystem(source, listOf(lens, detectorAt1km))

        // System output power, optical efficiency, and radiant intensity must remain identical
        assertEquals(systemWithoutDetector.outputPowerW, systemWithDetector.outputPowerW, 1e-6)
        assertEquals(systemWithoutDetector.opticalEfficiencyPct, systemWithDetector.opticalEfficiencyPct, 1e-6)
        assertEquals(systemWithoutDetector.radiantIntensityWsr, systemWithDetector.radiantIntensityWsr, 1e-4)

        // Detector screen evaluation panel statistics are populated separately in detectorResult
        val detRes = systemWithDetector.detectorResult
        assertTrue(detRes != null, "Detector result must be populated")
        assertTrue(detRes.landedPowerW < systemWithDetector.outputPowerW, "Landed power at 1km on 10mm aperture is small subset of output power")
    }
}

