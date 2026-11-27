package com.example.optical_designer.engine

import androidx.compose.ui.graphics.Color
import com.example.optical_designer.model.GlassMaterial
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.Wavelength
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Segment representing the monochromatic beam envelope between two Z coordinates.
 */
data class BeamSegment(
    val startZ: Double,
    val endZ: Double,
    val startTopY: Double,
    val startBottomY: Double,
    val endTopY: Double,
    val endBottomY: Double,
    val isClipped: Boolean = false,
    val color: Color = Color.Green
)

data class DetectorAnalysisResult(
    val detectorName: String,
    val detectorZMm: Double,
    val detectorApertureMm: Double,
    val spotDiameterMm: Double, // Reported EE90 spot diameter
    val landedPowerW: Double,
    val arrivingPowerW: Double,
    val opticalEfficiencyPct: Double,
    val encircledPowerPct: Double,
    val spilloverLossPct: Double,
    val apertureFillPct: Double,
    val integratedIrradianceWm2: Double,
    val integratedIrradiancemWcm2: Double,
    val peakIrradianceWm2: Double,
    val bestFocusZMm: Double? = null,
    val ee50DiameterMm: Double = 0.0,
    val ee80DiameterMm: Double = 0.0,
    val ee90DiameterMm: Double = 0.0,
    val ee95DiameterMm: Double = 0.0
)

data class AnalyticalOpticsResult(
    val beamSegments: List<BeamSegment>,
    val opticalEfficiencyPct: Double,
    val geometricCollectionPct: Double,
    val croppedLossPct: Double,
    val fresnelTransmissionPct: Double,
    val reflectionLossPct: Double,
    val inputDivergenceDeg: Double,
    val outputDivergenceDeg: Double,
    val spotDiameterAtDetectorMm: Double, // Reported EE90 spot diameter
    val focalDistanceMm: Double,
    val sphericalAberrationMm: Double,
    val sourcePowerW: Double = 1.0,
    val outputPowerW: Double = 0.24,
    val outputSolidAngleSr: Double = 0.0,
    val radiantIntensityWsr: Double = 0.0,
    val detectorResult: DetectorAnalysisResult? = null,
    val ee50DiameterMm: Double = 0.0,
    val ee80DiameterMm: Double = 0.0,
    val ee90DiameterMm: Double = 0.0,
    val ee95DiameterMm: Double = 0.0,
    val exitZMm: Double = 0.0,
    val finalRays: List<OpticalRay> = emptyList()
)

data class IrradianceAtRangeResult(
    val targetZMm: Double,
    val spotDiameterMm: Double,
    val spotAreaM2: Double,
    val irradianceWm2: Double,
    val irradiancemWcm2: Double,
    val ee50DiameterMm: Double = 0.0,
    val ee80DiameterMm: Double = 0.0,
    val ee90DiameterMm: Double = 0.0
)

/**
 * Paraxial Ray representation for matrix optics and ray bundle tracing.
 * @param y Transverse height from optical axis in mm
 * @param u Ray slope dy/dz = tan(theta)
 * @param y0 Initial source origin height in mm
 * @param isBlocked Whether this ray has been clipped/blocked by an aperture stop
 */
data class OpticalRay(
    var y: Double,
    var u: Double,
    val y0: Double,
    val weight: Double = 1.0,
    var isBlocked: Boolean = false
)

private data class LensPrescription(
    val r1: Double,             // Radius of surface 1 in mm (Double.POSITIVE_INFINITY for flat)
    val r2: Double,             // Radius of surface 2 in mm (Double.POSITIVE_INFINITY for flat)
    val n1: Double = 1.0,       // Medium index before surface 1
    val nGlass: Double,         // Glass index of lens
    val n3: Double = 1.0,       // Medium index after surface 2
    val isDoublet: Boolean = false,
    val rInt: Double = Double.POSITIVE_INFINITY, // Cemented surface radius
    val nFlint: Double = 1.62,   // Flint glass index
    val crownThickness: Double = 0.0,
    val flintThickness: Double = 0.0
)

private data class EncircledEnergyResult(
    val ee50RadiusMm: Double,
    val ee80RadiusMm: Double,
    val ee90RadiusMm: Double,
    val ee95RadiusMm: Double,
    val ee90DiameterMm: Double,
    val maxRadiusMm: Double
)

object AnalyticalOpticsEngine {

    private fun calculateEncircledEnergy(bundle: List<OpticalRay>): EncircledEnergyResult {
        val unblocked = bundle.filter { !it.isBlocked }
        if (unblocked.isEmpty()) {
            return EncircledEnergyResult(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val sorted = unblocked.sortedBy { abs(it.y) }
        val totalWeight = sorted.sumOf { it.weight }.coerceAtLeast(1e-12)

        fun radiusAtFraction(fraction: Double): Double {
            val targetWeight = totalWeight * fraction
            var accum = 0.0
            for ((y, _, _, weight) in sorted) {
                accum += weight
                if (accum >= targetWeight) {
                    return abs(y)
                }
            }
            return abs(sorted.last().y)
        }

        val r50 = radiusAtFraction(0.50)
        val r80 = radiusAtFraction(0.80)
        val r90 = radiusAtFraction(0.90)
        val r95 = radiusAtFraction(0.95)
        val rMax = abs(sorted.last().y)

        return EncircledEnergyResult(
            ee50RadiusMm = r50,
            ee80RadiusMm = r80,
            ee90RadiusMm = r90,
            ee95RadiusMm = r95,
            ee90DiameterMm = r90 * 2.0,
            maxRadiusMm = rMax
        )
    }

    private fun getLensPrescription(elem: OpticalElement): LensPrescription {
        val f = elem.focalLengthMm
        val fAbs = abs(f)
        val nd = elem.material.nd
        val thickness = elem.thicknessMm

        return when (elem.type) {
            OpticalElementType.PLANO_CONVEX -> {
                val r = fAbs * (nd - 1.0)
                if (f >= 0.0) {
                    if (elem.orientation == com.example.optical_designer.model.LensOrientation.CURVED_FACE_LEFT) {
                        // Curved face facing left at zFront (R1 = +r > 0), flat back face facing right at zBack (R2 = inf)
                        LensPrescription(
                            r1 = r,
                            r2 = Double.POSITIVE_INFINITY,
                            nGlass = nd
                        )
                    } else {
                        // Flat face facing left at zFront (R1 = inf), convex back face facing right at zBack (R2 = -r < 0)
                        LensPrescription(
                            r1 = Double.POSITIVE_INFINITY,
                            r2 = -r,
                            nGlass = nd
                        )
                    }
                } else {
                    // Negative focal length (Plano-Concave)
                    if (elem.orientation == com.example.optical_designer.model.LensOrientation.CURVED_FACE_LEFT) {
                        // Concave face facing left at zFront (R1 = -r < 0), flat back face facing right at zBack (R2 = inf)
                        LensPrescription(
                            r1 = -r,
                            r2 = Double.POSITIVE_INFINITY,
                            nGlass = nd
                        )
                    } else {
                        // Flat face facing left at zFront (R1 = inf), concave back face facing right at zBack (R2 = +r > 0)
                        LensPrescription(
                            r1 = Double.POSITIVE_INFINITY,
                            r2 = r,
                            nGlass = nd
                        )
                    }
                }
            }
            OpticalElementType.BICONVEX -> {
                val disc = 1.0 - thickness / (nd * fAbs)
                val r = if (disc > 0.0 && fAbs > 0.0) {
                    fAbs * (nd - 1.0) * (1.0 + sqrt(disc))
                } else {
                    2.0 * fAbs * (nd - 1.0)
                }

                if (f >= 0.0) {
                    LensPrescription(
                        r1 = r,
                        r2 = -r,
                        nGlass = nd
                    )
                } else {
                    // Biconcave
                    LensPrescription(
                        r1 = -r,
                        r2 = r,
                        nGlass = nd
                    )
                }
            }
            OpticalElementType.PARABOLIC_MIRROR,
            OpticalElementType.APERTURE_STOP,
            OpticalElementType.DETECTOR -> {
                LensPrescription(
                    r1 = Double.POSITIVE_INFINITY,
                    r2 = Double.POSITIVE_INFINITY,
                    nGlass = 1.0
                )
            }
        }
    }

    /**
     * Exact piecewise-linear sub-bin ray interpolation for aperture power collection.
     * Conserves energy strictly while eliminating discretization quantization artifacts.
     */
    private fun aperturePowerFraction(
        rays: List<OpticalRay>,
        radiusMm: Double
    ): Double {
        val sorted = rays.filter { !it.isBlocked }.sortedBy { abs(it.y) }
        var power = 0.0

        for (i in sorted.indices) {
            val r = abs(sorted[i].y)
            if (r <= radiusMm) {
                power += sorted[i].weight
            } else {
                if (i > 0) {
                    val r0 = abs(sorted[i - 1].y)
                    val dr = r - r0
                    if (dr > 1e-12) {
                        val t = ((radiusMm - r0) / dr).coerceIn(0.0, 1.0)
                        power += sorted[i].weight * t
                    }
                }
                break
            }
        }
        return power
    }

    /**
     * Calculates output beam divergence as the 90% encircled angular spread (EE90 power divergence).
     * Works for arbitrary non-Gaussian sources (LEDs, Laser Diodes, Flat-Tops, etc.).
     */
    private fun calculateEE90DivergenceDeg(rays: List<OpticalRay>): Double {
        val unblocked = rays.filter { !it.isBlocked }
        if (unblocked.isEmpty()) return 0.0

        val sorted = unblocked.sortedBy { abs(atan(it.u)) }
        val totalWeight = sorted.sumOf { it.weight }.coerceAtLeast(1e-12)

        var accum = 0.0
        val target = totalWeight * 0.90

        for (ray in sorted) {
            accum += ray.weight
            if (accum >= target) {
                val halfAngleRad = abs(atan(ray.u))
                return (2.0 * halfAngleRad * 180.0 / kotlin.math.PI).coerceAtLeast(0.0)
            }
        }
        return (2.0 * abs(atan(sorted.last().u)) * 180.0 / kotlin.math.PI).coerceAtLeast(0.0)
    }

    private fun refractSurface(ray: OpticalRay, nBefore: Double, nAfter: Double, radius: Double) {
        if (ray.isBlocked) return
        if (radius.isInfinite() || radius == 0.0) {
            // Paraxial Snell's law refraction at flat surface: u_after = (nBefore / nAfter) * u_before
            ray.u *= (nBefore / nAfter)
        } else {
            // Paraxial Snell's law refraction matrix at curved surface:
            // u_after = (nBefore / nAfter) * u_before - ((nAfter - nBefore) / (nAfter * radius)) * y
            val powerCoeff = (nAfter - nBefore) / (nAfter * radius)
            ray.u = (nBefore / nAfter) * ray.u - powerCoeff * ray.y
        }
    }

    /**
     * Physics-based Analytical Engine for multi-lens optical systems.
     * Uses ABCD Matrix Ray Tracing (surface refractions and internal glass propagation)
     * and aperture-filtered ray bundle tracing to support any arbitrary combination of optical elements,
     * thickness, curvatures, orientations, and relative distances.
     */
    fun calculateSystem(
        source: LightSource,
        elements: List<OpticalElement>,
        maxViewportZ: Double = 300.0
    ): AnalyticalOpticsResult {
        val activeElements = elements.filter { it.enabled }.sortedBy { it.positionZMm }
        val sourceColor = Wavelength(source.cwlNm).toColor()

        val divHalfRad = (source.divergenceYDeg * kotlin.math.PI / 360.0)
        val initialSlope = tan(divHalfRad)
        val emitterHalfHeight = max(0.001, source.emitterHeightMm / 2.0)

        // Generate phase-space sampled ray bundle with adaptive ray density
        val rawRays = mutableListOf<OpticalRay>()
        val ySampleCount = when (source.sourceType) {
            com.example.optical_designer.model.SourceType.COLLIMATED -> 61
            com.example.optical_designer.model.SourceType.LASER_DIODE -> 61
            else -> 51
        }
        val uSampleCount = when (source.sourceType) {
            com.example.optical_designer.model.SourceType.COLLIMATED -> 161
            com.example.optical_designer.model.SourceType.LASER_DIODE -> 161
            else -> 101
        }
        val deltaTheta = if (uSampleCount > 1) (2.0 * divHalfRad) / (uSampleCount - 1) else 0.0

        for (j in 0 until ySampleCount) {
            val tY = if (ySampleCount > 1) -1.0 + 2.0 * j / (ySampleCount - 1) else 0.0
            val y0 = tY * emitterHalfHeight

            for (i in 0 until uSampleCount) {
                val tTheta = if (uSampleCount > 1) -1.0 + 2.0 * i / (uSampleCount - 1) else 0.0
                val theta = tTheta * divHalfRad
                val u0 = tan(theta)

                val theta1 = theta - deltaTheta / 2.0
                val theta2 = theta + deltaTheta / 2.0

                val wSpatial = when (source.sourceType) {
                    com.example.optical_designer.model.SourceType.LASER_DIODE,
                    com.example.optical_designer.model.SourceType.COLLIMATED -> {
                        // Gaussian spatial waist profile (1/e^2 radius = emitterHalfHeight)
                        kotlin.math.exp(-2.0 * tY * tY)
                    }
                    com.example.optical_designer.model.SourceType.LED,
                    com.example.optical_designer.model.SourceType.POINT_SOURCE -> {
                        // Uniform spatial die emission
                        1.0
                    }
                }

                val wAngular = when (source.sourceType) {
                    com.example.optical_designer.model.SourceType.LASER_DIODE,
                    com.example.optical_designer.model.SourceType.COLLIMATED -> {
                        // Gaussian angular far-field profile (1/e^2 half-angle = divHalfRad)
                        val refHalfRad = max(1e-6, divHalfRad)
                        kotlin.math.exp(-2.0 * (theta / refHalfRad).pow(2))
                    }
                    com.example.optical_designer.model.SourceType.LED -> {
                        // Rigorous signed 3D Lambertian bin integration: ∫ cos(θ) sin|θ| dθ
                        if (theta1 < 0.0 && theta2 > 0.0) {
                            0.5 * (kotlin.math.sin(theta1) * kotlin.math.sin(theta1) + kotlin.math.sin(theta2) * kotlin.math.sin(theta2))
                        } else {
                            0.5 * abs(kotlin.math.sin(theta2) * kotlin.math.sin(theta2) - kotlin.math.sin(theta1) * kotlin.math.sin(theta1))
                        }
                    }
                    com.example.optical_designer.model.SourceType.POINT_SOURCE -> {
                        // Rigorous signed isotropic 3D bin integration: ∫ sin|θ| dθ
                        if (theta1 < 0.0 && theta2 > 0.0) {
                            (1.0 - cos(theta1)) + (1.0 - cos(theta2))
                        } else {
                            abs(cos(theta1) - cos(theta2))
                        }
                    }
                }

                val rawWeight = wSpatial * wAngular
                rawRays.add(OpticalRay(y0, u0, y0, weight = rawWeight, isBlocked = false))
            }
        }

        val totalInitialWeight = rawRays.sumOf { it.weight }.coerceAtLeast(1e-12)
        val rayBundle = rawRays.map { ray ->
            OpticalRay(ray.y, ray.u, ray.y0, weight = ray.weight / totalInitialWeight, isBlocked = ray.isBlocked)
        }.toMutableList()

        var currentZ = source.positionZMm
        var geometricCollectionFraction = 1.0
        var fresnelTransmissionFraction = 1.0

        val segments = mutableListOf<BeamSegment>()
        var detectorAnalysisResult: DetectorAnalysisResult? = null
        var lastWaistZMm: Double? = null

        // Trace ray bundle through active optical elements using ABCD surface & glass propagation
        for (elem in activeElements) {
            val thickness = elem.thicknessMm
            val zElem = elem.positionZMm
            val zFront = if (elem.type == OpticalElementType.PLANO_CONVEX) {
                max(currentZ, zElem)
            } else {
                max(currentZ, zElem - thickness / 2.0)
            }
            val zBack = if (elem.type == OpticalElementType.PLANO_CONVEX) {
                max(zFront + 0.01, zElem + thickness)
            } else {
                max(zFront + 0.01, zElem + thickness / 2.0)
            }

            // Target Z boundary for free space propagation to element front boundary / DETECTOR
            val zTarget = if (elem.type == OpticalElementType.DETECTOR) zElem else zFront

            // 1. Free-space propagation to element front boundary
            if (zTarget > currentZ) {
                propagateFreeSpace(
                    rayBundle = rayBundle,
                    startZ = currentZ,
                    endZ = zTarget,
                    sourceColor = sourceColor,
                    fallbackInitialRadius = emitterHalfHeight + currentZ * initialSlope,
                    segments = segments
                )
                currentZ = zTarget
            }

            // 2. DETECTOR Screen handling: Sub-bin ray interpolation & beam footprint area
            if (elem.type == OpticalElementType.DETECTOR) {
                val ee = calculateEncircledEnergy(rayBundle)
                val detRadiusMm = max(0.001, elem.clearApertureMm / 2.0)

                val unblockedDetRays = rayBundle.filter { !it.isBlocked }
                val arrivingWeightFraction = unblockedDetRays.sumOf { it.weight }
                val arrivingPowerW = source.opticalPowerW * arrivingWeightFraction * fresnelTransmissionFraction

                // Exact piecewise-linear sub-bin ray interpolation for aperture power collection
                val landedWeightFraction = aperturePowerFraction(unblockedDetRays, detRadiusMm)
                val landedPowerW = source.opticalPowerW * landedWeightFraction * fresnelTransmissionFraction

                val detOpticalEfficiencyPct = if (source.opticalPowerW > 0.0) {
                    (landedPowerW / source.opticalPowerW) * 100.0
                } else 0.0

                val encircledPowerFraction = if (arrivingPowerW > 0.0) {
                    (landedPowerW / arrivingPowerW).coerceIn(0.0, 1.0)
                } else 0.0

                val encircledPowerPct = encircledPowerFraction * 100.0
                val spilloverLossPct = 100.0 - encircledPowerPct

                // Reported spot diameter (EE90 arriving spot diameter)
                val reportedSpotDiamMm = if (ee.ee90DiameterMm > 0.0) ee.ee90DiameterMm else detRadiusMm * 2.0

                // Correct Beam Irradiance using beam footprint area (NOT detector area)
                val spotRadiusOnDetM = min(detRadiusMm, ee.ee90RadiusMm.coerceAtLeast(0.001)) / 1000.0
                val beamFootprintAreaM2 = kotlin.math.PI * spotRadiusOnDetM * spotRadiusOnDetM

                val integratedIrradianceWm2 = if (beamFootprintAreaM2 > 0.0) landedPowerW / beamFootprintAreaM2 else 0.0
                val integratedIrradiancemWcm2 = integratedIrradianceWm2 / 10.0

                val detApertureAreaM2 = kotlin.math.PI * (detRadiusMm / 1000.0) * (detRadiusMm / 1000.0)
                val apertureFillPct = if (detApertureAreaM2 > 0.0) {
                    ((beamFootprintAreaM2 / detApertureAreaM2) * 100.0).coerceIn(0.0, 100.0)
                } else 0.0

                val effectiveFocusZ = lastWaistZMm ?: calculateSystemFocalZ(activeElements)

                detectorAnalysisResult = DetectorAnalysisResult(
                    detectorName = elem.name,
                    detectorZMm = elem.positionZMm,
                    detectorApertureMm = elem.clearApertureMm,
                    spotDiameterMm = reportedSpotDiamMm,
                    landedPowerW = landedPowerW,
                    arrivingPowerW = arrivingPowerW,
                    opticalEfficiencyPct = detOpticalEfficiencyPct,
                    encircledPowerPct = encircledPowerPct,
                    spilloverLossPct = spilloverLossPct,
                    apertureFillPct = apertureFillPct,
                    integratedIrradianceWm2 = integratedIrradianceWm2,
                    integratedIrradiancemWcm2 = integratedIrradiancemWcm2,
                    peakIrradianceWm2 = integratedIrradianceWm2,
                    bestFocusZMm = effectiveFocusZ,
                    ee50DiameterMm = ee.ee50RadiusMm * 2.0,
                    ee80DiameterMm = ee.ee80RadiusMm * 2.0,
                    ee90DiameterMm = ee.ee90DiameterMm,
                    ee95DiameterMm = ee.ee95RadiusMm * 2.0
                )

                break
            }

            // 3. Clear Aperture Clipping & Ray Weight Collection Fraction at zFront
            val apertureRadius = elem.clearApertureMm / 2.0
            val beamRadiusAtElem = getTopUnblockedRadius(rayBundle, apertureRadius)

            if (apertureRadius > 0.0) {
                for (ray in rayBundle) {
                    if (!ray.isBlocked && abs(ray.y) > apertureRadius) {
                        ray.isBlocked = true
                    }
                }
            }

            val unblockedWeight = rayBundle.filter { !it.isBlocked }.sumOf { it.weight }
            geometricCollectionFraction = unblockedWeight

            val transmittedTopY = if (apertureRadius > 0.0) {
                min(apertureRadius, beamRadiusAtElem)
            } else {
                beamRadiusAtElem
            }

            // 4. Matrix ABCD Refraction and Thickness Propagation
            when (elem.type) {
                OpticalElementType.PLANO_CONVEX,
                OpticalElementType.BICONVEX -> {
                    val presc = getLensPrescription(elem)

                    // Refraction at Surface 1 (Air -> Glass)
                    for (ray in rayBundle) {
                        refractSurface(ray, nBefore = 1.0, nAfter = presc.nGlass, radius = presc.r1)
                    }

                    // Single glass element (Plano-Convex or Biconvex)
                    val glassThickness = max(0.001, zBack - zFront)
                    propagateRayBundleInPlace(rayBundle, glassThickness)

                    val exitTopYLens = getTopUnblockedRadius(rayBundle, transmittedTopY)
                    segments.add(
                        BeamSegment(
                            startZ = zFront,
                            endZ = zBack,
                            startTopY = transmittedTopY,
                            startBottomY = -transmittedTopY,
                            endTopY = exitTopYLens,
                            endBottomY = -exitTopYLens,
                            color = sourceColor
                        )
                    )

                    // Refraction at Surface 2 (Glass -> Air)
                    for (ray in rayBundle) {
                        refractSurface(ray, nBefore = presc.nGlass, nAfter = 1.0, radius = presc.r2)
                    }

                    fresnelTransmissionFraction *= elem.effectiveTransmissionFraction

                    // Track minimum RMS spot size focal waist Z across all surviving rays
                    val unblocked = rayBundle.filter { !it.isBlocked }
                    if (unblocked.isNotEmpty()) {
                        val sumYU = unblocked.sumOf { it.weight * it.y * it.u }
                        val sumU2 = unblocked.sumOf { it.weight * it.u * it.u }
                        if (sumU2 > 1e-12 && sumYU < 0.0) {
                            val dzWaist = -sumYU / sumU2
                            if (dzWaist > 0.0) {
                                lastWaistZMm = zBack + dzWaist
                            }
                        }
                    }

                    currentZ = zBack
                }
                OpticalElementType.PARABOLIC_MIRROR -> {
                    val f = elem.focalLengthMm
                    if (f != 0.0) {
                        for (ray in rayBundle) {
                            if (!ray.isBlocked) {
                                ray.u -= (ray.y / f)
                            }
                        }
                    }
                    fresnelTransmissionFraction *= elem.effectiveTransmissionFraction

                    val unblocked = rayBundle.filter { !it.isBlocked }
                    if (unblocked.isNotEmpty()) {
                        val sumYU = unblocked.sumOf { it.weight * it.y * it.u }
                        val sumU2 = unblocked.sumOf { it.weight * it.u * it.u }
                        if (sumU2 > 1e-12 && sumYU < 0.0) {
                            val dzWaist = -sumYU / sumU2
                            if (dzWaist > 0.0) {
                                lastWaistZMm = currentZ + dzWaist
                            }
                        }
                    }
                }
                OpticalElementType.APERTURE_STOP -> {
                    // Pure aperture stop
                }
                OpticalElementType.DETECTOR -> {
                    // Handled above
                }
            }
        }

        // 5. Free-space propagation past final element to viewport boundary
        if (detectorAnalysisResult == null) {
            val maxElementZ = activeElements.maxOfOrNull {
                if (it.type == OpticalElementType.PLANO_CONVEX) {
                    it.positionZMm + it.thicknessMm
                } else {
                    it.positionZMm + it.thicknessMm / 2.0
                }
            } ?: 100.0
            val targetZBound = maxOf(maxViewportZ, source.positionZMm, maxElementZ)
            val endZ = targetZBound + maxOf(60.0, targetZBound * 0.1)

            if (endZ > currentZ) {
                propagateFreeSpace(
                    rayBundle = rayBundle,
                    startZ = currentZ,
                    endZ = endZ,
                    sourceColor = sourceColor,
                    fallbackInitialRadius = 0.1,
                    segments = segments
                )
            }
        }

        // Calculate output beam divergence using EE90 angular width
        val unblockedRays = rayBundle.filter { !it.isBlocked }
        val outputDivergenceDeg = if (unblockedRays.isNotEmpty()) {
            calculateEE90DivergenceDeg(unblockedRays)
        } else source.divergenceYDeg

        val detectorZ = detectorAnalysisResult?.detectorZMm
            ?: (activeElements.firstOrNull { it.type == OpticalElementType.DETECTOR }?.positionZMm
                ?: (activeElements.lastOrNull()?.positionZMm ?: 150.0))
        val spotSize = detectorAnalysisResult?.spotDiameterMm ?: calculateSpotSizeAtZ(segments, detectorZ)

        val netEfficiencyFraction = geometricCollectionFraction * fresnelTransmissionFraction
        val geometricCollectionPct = (geometricCollectionFraction * 100.0).coerceIn(0.0, 100.0)
        val croppedLossPct = 100.0 - geometricCollectionPct
        val fresnelTransmissionPct = (fresnelTransmissionFraction * 100.0).coerceIn(0.0, 100.0)
        val reflectionLossPct = geometricCollectionPct * (1.0 - fresnelTransmissionFraction)

        val finalOpticalEfficiencyPct = (netEfficiencyFraction * 100.0).coerceIn(0.0, 100.0)
        val finalOutputPowerW = (source.opticalPowerW * netEfficiencyFraction)

        val outputHalfRad = outputDivergenceDeg * kotlin.math.PI / 360.0
        val solidAngleSr = if (outputHalfRad > 0.0) {
            2.0 * kotlin.math.PI * (1.0 - cos(outputHalfRad))
        } else {
            0.0
        }
        val radiantIntensityWsr = if (solidAngleSr > 0.0) {
            finalOutputPowerW / solidAngleSr
        } else {
            0.0
        }

        val effectiveFocalZ = lastWaistZMm ?: calculateSystemFocalZ(activeElements)

        val finalEE = calculateEncircledEnergy(rayBundle)
        val lastOpticZ = activeElements.lastOrNull { it.type != OpticalElementType.DETECTOR }?.let {
            if (it.type == OpticalElementType.PLANO_CONVEX) it.positionZMm + it.thicknessMm else it.positionZMm + it.thicknessMm / 2.0
        } ?: 0.0

        return AnalyticalOpticsResult(
            beamSegments = segments,
            opticalEfficiencyPct = finalOpticalEfficiencyPct,
            geometricCollectionPct = geometricCollectionPct,
            croppedLossPct = croppedLossPct,
            fresnelTransmissionPct = fresnelTransmissionPct,
            reflectionLossPct = reflectionLossPct,
            inputDivergenceDeg = source.divergenceYDeg,
            outputDivergenceDeg = outputDivergenceDeg,
            spotDiameterAtDetectorMm = spotSize,
            focalDistanceMm = if (effectiveFocalZ == null || effectiveFocalZ.isNaN()) 0.0 else effectiveFocalZ,
            sphericalAberrationMm = 0.0,
            sourcePowerW = source.opticalPowerW,
            outputPowerW = finalOutputPowerW,
            outputSolidAngleSr = solidAngleSr,
            radiantIntensityWsr = radiantIntensityWsr,
            detectorResult = detectorAnalysisResult,
            ee50DiameterMm = finalEE.ee50RadiusMm * 2.0,
            ee80DiameterMm = finalEE.ee80RadiusMm * 2.0,
            ee90DiameterMm = finalEE.ee90DiameterMm,
            ee95DiameterMm = finalEE.ee95RadiusMm * 2.0,
            exitZMm = lastOpticZ,
            finalRays = unblockedRays.map { ray ->
                OpticalRay(ray.y, ray.u, ray.y0, weight = ray.weight, isBlocked = ray.isBlocked)
            }
        )
    }

    fun calculateIrradianceAtRange(
        result: AnalyticalOpticsResult,
        targetZMm: Double
    ): IrradianceAtRangeResult {
        val dz = max(0.0, targetZMm - result.exitZMm)

        // Propagate unblocked final rays to exact target Z range
        val propagatedRays = result.finalRays.filter { !it.isBlocked }.map { ray ->
            val yAtZ = ray.y + ray.u * dz
            OpticalRay(yAtZ, ray.u, ray.y0, weight = ray.weight, isBlocked = false)
        }

        val ee = calculateEncircledEnergy(propagatedRays)
        val ee90DiamMm = if (ee.ee90DiameterMm > 0.0) ee.ee90DiameterMm else calculateSpotSizeAtZ(result.beamSegments, targetZMm)
        val ee80DiamMm = ee.ee80RadiusMm * 2.0
        val ee50DiamMm = ee.ee50RadiusMm * 2.0

        val spotRadiusM = (ee90DiamMm / 2.0) / 1000.0
        val spotAreaM2 = kotlin.math.PI * spotRadiusM * spotRadiusM

        val irradianceWm2 = if (spotAreaM2 > 0.0) {
            (0.90 * result.outputPowerW) / spotAreaM2
        } else {
            0.0
        }

        return IrradianceAtRangeResult(
            targetZMm = targetZMm,
            spotDiameterMm = ee90DiamMm,
            spotAreaM2 = spotAreaM2,
            irradianceWm2 = irradianceWm2,
            irradiancemWcm2 = irradianceWm2 / 10.0,
            ee50DiameterMm = ee50DiamMm,
            ee80DiameterMm = ee80DiamMm,
            ee90DiameterMm = ee90DiamMm
        )
    }

    private fun getTopUnblockedRadius(bundle: List<OpticalRay>, fallback: Double): Double {
        val unblocked = bundle.filter { !it.isBlocked }
        return if (unblocked.isNotEmpty()) {
            max(0.001, unblocked.maxOf { abs(it.y) })
        } else {
            max(0.001, fallback)
        }
    }

    private fun propagateRayBundleInPlace(bundle: List<OpticalRay>, dz: Double) {
        for (ray in bundle) {
            if (!ray.isBlocked) {
                ray.y += ray.u * dz
            }
        }
    }

    private fun propagateFreeSpace(
        rayBundle: List<OpticalRay>,
        startZ: Double,
        endZ: Double,
        sourceColor: Color,
        fallbackInitialRadius: Double,
        segments: MutableList<BeamSegment>
    ) {
        if (endZ <= startZ) return

        val unblocked = rayBundle.filter { !it.isBlocked }
        if (unblocked.isEmpty()) {
            val r1 = getTopUnblockedRadius(rayBundle, fallbackInitialRadius)
            val dz = endZ - startZ
            propagateRayBundleInPlace(rayBundle, dz)
            val r2 = getTopUnblockedRadius(rayBundle, r1)
            segments.add(
                BeamSegment(
                    startZ = startZ,
                    endZ = endZ,
                    startTopY = r1,
                    startBottomY = -r1,
                    endTopY = r2,
                    endBottomY = -r2,
                    color = sourceColor
                )
            )
            return
        }

        val totalDz = endZ - startZ
        val numSteps = maxOf(10, (totalDz / 5.0).toInt().coerceAtMost(100))
        val stepDz = totalDz / numSteps.toDouble()

        val zPoints = DoubleArray(numSteps + 1)
        val rPoints = DoubleArray(numSteps + 1)

        var curZ = startZ
        zPoints[0] = curZ
        rPoints[0] = getTopUnblockedRadius(rayBundle, fallbackInitialRadius)

        for (step in 1..numSteps) {
            propagateRayBundleInPlace(rayBundle, stepDz)
            curZ += stepDz
            zPoints[step] = curZ
            rPoints[step] = getTopUnblockedRadius(rayBundle, rPoints[step - 1])
        }
        zPoints[numSteps] = endZ

        var segStartIdx = 0
        for (i in 1 until numSteps) {
            val prevSlope = (rPoints[i] - rPoints[segStartIdx]) / (zPoints[i] - zPoints[segStartIdx])
            val nextSlope = (rPoints[i + 1] - rPoints[i]) / (zPoints[i + 1] - zPoints[i])

            if (abs(nextSlope - prevSlope) > 1e-4) {
                segments.add(
                    BeamSegment(
                        startZ = zPoints[segStartIdx],
                        endZ = zPoints[i],
                        startTopY = rPoints[segStartIdx],
                        startBottomY = -rPoints[segStartIdx],
                        endTopY = rPoints[i],
                        endBottomY = -rPoints[i],
                        color = sourceColor
                    )
                )
                segStartIdx = i
            }
        }

        segments.add(
            BeamSegment(
                startZ = zPoints[segStartIdx],
                endZ = zPoints[numSteps],
                startTopY = rPoints[segStartIdx],
                startBottomY = -rPoints[segStartIdx],
                endTopY = rPoints[numSteps],
                endBottomY = -rPoints[numSteps],
                color = sourceColor
            )
        )
    }

    private fun calculateSystemFocalZ(elements: List<OpticalElement>): Double? {
        var rayY = 1.0
        var rayU = 0.0
        var currentZ = 0.0

        val lensElements = elements.filter {
            it.type != OpticalElementType.APERTURE_STOP && it.type != OpticalElementType.DETECTOR
        }
        if (lensElements.isEmpty()) return null

        for (elem in lensElements) {
            val thickness = elem.thicknessMm
            val zElem = elem.positionZMm
            val zFront = if (elem.type == OpticalElementType.PLANO_CONVEX) {
                max(currentZ, zElem)
            } else {
                max(currentZ, zElem - thickness / 2.0)
            }
            val zBack = max(zFront + 0.01, zFront + thickness)

            val dz = zFront - currentZ
            rayY += rayU * dz
            currentZ = zFront

            if (elem.type == OpticalElementType.PARABOLIC_MIRROR) {
                val f = elem.focalLengthMm
                if (f != 0.0) {
                    rayU -= rayY / f
                }
            } else {
                val presc = getLensPrescription(elem)

                // Refraction at Surface 1
                if (presc.r1.isInfinite()) {
                    rayU /= presc.nGlass
                } else {
                    val powerCoeff = (presc.nGlass - 1.0) / (presc.nGlass * presc.r1)
                    rayU = (rayU / presc.nGlass) - powerCoeff * rayY
                }

                if (!presc.isDoublet) {
                    // Internal glass propagation
                    val glassThickness = max(0.001, zBack - zFront)
                    rayY += rayU * glassThickness
                    currentZ = zBack

                    // Refraction at Surface 2
                    if (presc.r2.isInfinite()) {
                        rayU *= presc.nGlass
                    } else {
                        val powerCoeff = (1.0 - presc.nGlass) / (1.0 * presc.r2)
                        rayU = (presc.nGlass * rayU) - powerCoeff * rayY
                    }
                } else {
                    val totalT = max(0.001, zBack - zFront)
                    val crownT = (presc.crownThickness).coerceIn(0.001, totalT - 0.001)
                    val flintT = max(0.001, totalT - crownT)

                    // Crown propagation
                    rayY += rayU * crownT

                    // Cemented interface
                    if (!presc.rInt.isInfinite()) {
                        val powerCoeff = (presc.nFlint - presc.nGlass) / (presc.nFlint * presc.rInt)
                        rayU = (presc.nGlass / presc.nFlint) * rayU - powerCoeff * rayY
                    } else {
                        rayU *= (presc.nGlass / presc.nFlint)
                    }

                    // Flint propagation
                    rayY += rayU * flintT
                    currentZ = zBack

                    // Surface 3
                    if (!presc.r2.isInfinite()) {
                        val powerCoeff = (1.0 - presc.nFlint) / (1.0 * presc.r2)
                        rayU = (presc.nFlint * rayU) - powerCoeff * rayY
                    } else {
                        rayU *= presc.nFlint
                    }
                }
            }
        }

        if (rayU < 0.0) {
            val dzToFocus = -rayY / rayU
            return currentZ + dzToFocus
        }

        return null
    }

    private fun calculateSpotSizeAtZ(segments: List<BeamSegment>, targetZ: Double): Double {
        val seg = segments.firstOrNull { targetZ >= (it.startZ - 1e-6) && targetZ <= (it.endZ + 1e-6) }
            ?: segments.lastOrNull() ?: return 0.0
        val dz = seg.endZ - seg.startZ
        val t = if (dz > 0) (targetZ - seg.startZ) / dz else 0.0
        val topY = seg.startTopY + t * (seg.endTopY - seg.startTopY)
        val bottomY = seg.startBottomY + t * (seg.endBottomY - seg.startBottomY)
        return abs(topY - bottomY)
    }
}
