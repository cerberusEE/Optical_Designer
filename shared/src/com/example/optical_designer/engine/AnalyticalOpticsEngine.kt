package com.example.optical_designer.engine

import androidx.compose.ui.graphics.Color
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.Wavelength
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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
    val spotDiameterMm: Double,
    val landedPowerW: Double,
    val arrivingPowerW: Double,
    val opticalEfficiencyPct: Double,
    val encircledPowerPct: Double,
    val spilloverLossPct: Double,
    val apertureFillPct: Double,
    val integratedIrradianceWm2: Double,
    val integratedIrradiancemWcm2: Double,
    val peakIrradianceWm2: Double,
    val bestFocusZMm: Double? = null
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
    val spotDiameterAtDetectorMm: Double,
    val focalDistanceMm: Double,
    val sphericalAberrationMm: Double,
    val sourcePowerW: Double = 1.0,
    val outputPowerW: Double = 0.24,
    val outputSolidAngleSr: Double = 0.0,
    val radiantIntensityWsr: Double = 0.0,
    val detectorResult: DetectorAnalysisResult? = null
)

data class IrradianceAtRangeResult(
    val targetZMm: Double,
    val spotDiameterMm: Double,
    val spotAreaM2: Double,
    val irradianceWm2: Double,
    val irradiancemWcm2: Double
)

/**
 * Paraxial Ray representation for matrix optics and ray bundle tracing.
 * @param y Transverse height from optical axis in mm
 * @param u Ray slope dy/dz = tan(theta)
 * @param y0 Initial source origin height in mm
 * @param isBlocked Whether this ray has been clipped/blocked by an aperture stop
 */
private data class OpticalRay(
    var y: Double,
    var u: Double,
    val y0: Double,
    var isBlocked: Boolean = false
)

object AnalyticalOpticsEngine {

    /**
     * Physics-based Analytical Engine for multi-lens optical systems.
     * Uses Matrix Ray Tracing and aperture-filtered ray bundle tracing
     * at lens principal planes to support any arbitrary combination of optical elements
     * and relative distances.
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

        // Generate ray bundle covering spatial emitter extent and angular divergence spectrum
        val rayBundle = mutableListOf<OpticalRay>()
        val ySamples = listOf(-emitterHalfHeight, -emitterHalfHeight * 0.5, 0.0, emitterHalfHeight * 0.5, emitterHalfHeight)
        val uStepCount = 10

        for (y0 in ySamples) {
            for (i in -uStepCount..uStepCount) {
                val u0 = (i.toDouble() / uStepCount.toDouble()) * initialSlope
                rayBundle.add(OpticalRay(y0, u0, y0, false))
            }
        }

        var currentZ = source.positionZMm
        var geometricCollectionFraction = 1.0
        var fresnelTransmissionFraction = 1.0

        val segments = mutableListOf<BeamSegment>()
        var detectorAnalysisResult: DetectorAnalysisResult? = null
        var lastWaistZMm: Double? = null

        // Trace ray bundle through active optical elements
        for (elem in activeElements) {
            val thickness = elem.thicknessMm
            val zElem = elem.positionZMm
            val zFront = max(currentZ, zElem - thickness / 2.0)
            val zBack = max(zFront + 0.01, zElem + thickness / 2.0)

            // Target Z boundary for free space propagation to element front boundary / DETECTOR
            val zTarget = if (elem.type == OpticalElementType.DETECTOR) zElem else zFront

            // 1. Free-space propagation to element front boundary
            if (zTarget > currentZ) {
                val dz = zTarget - currentZ

                val startTopY = getTopUnblockedRadius(rayBundle, emitterHalfHeight + currentZ * initialSlope)
                val startBottomY = -startTopY

                // Check for intermediate focal waist between currentZ and zTarget
                val intermediateWaistZ = findWaistZBetween(rayBundle, currentZ, zTarget)

                if (intermediateWaistZ != null) {
                    val dz1 = intermediateWaistZ - currentZ
                    val waistRays = propagateRayBundleTemp(rayBundle, dz1)
                    val waistTopY = getTopUnblockedRadius(waistRays, startTopY)

                    segments.add(
                        BeamSegment(
                            startZ = currentZ,
                            endZ = intermediateWaistZ,
                            startTopY = startTopY,
                            startBottomY = startBottomY,
                            endTopY = waistTopY,
                            endBottomY = -waistTopY,
                            color = sourceColor
                        )
                    )

                    val dz2 = zTarget - intermediateWaistZ
                    propagateRayBundleInPlace(rayBundle, dz1)
                    val endTopY = getTopUnblockedRadius(propagateRayBundleTemp(rayBundle, dz2), waistTopY)

                    segments.add(
                        BeamSegment(
                            startZ = intermediateWaistZ,
                            endZ = zTarget,
                            startTopY = waistTopY,
                            startBottomY = -waistTopY,
                            endTopY = endTopY,
                            endBottomY = -endTopY,
                            color = sourceColor
                        )
                    )

                    propagateRayBundleInPlace(rayBundle, dz2)
                    lastWaistZMm = intermediateWaistZ
                } else {
                    propagateRayBundleInPlace(rayBundle, dz)
                    val endTopY = getTopUnblockedRadius(rayBundle, startTopY)

                    segments.add(
                        BeamSegment(
                            startZ = currentZ,
                            endZ = zTarget,
                            startTopY = startTopY,
                            startBottomY = startBottomY,
                            endTopY = endTopY,
                            endBottomY = -endTopY,
                            color = sourceColor
                        )
                    )
                }

                currentZ = zTarget
            }

            // 2. DETECTOR Screen handling: calculate encircled power & irradiance, then stop propagation
            if (elem.type == OpticalElementType.DETECTOR) {
                val beamRadiusAtDet = getTopUnblockedRadius(rayBundle, 0.1)
                val spotDiamMm = beamRadiusAtDet * 2.0
                val detRadiusMm = max(0.001, elem.clearApertureMm / 2.0)

                val arrivingPowerW = source.opticalPowerW * geometricCollectionFraction * fresnelTransmissionFraction

                val passRatio = (detRadiusMm / max(1e-6, beamRadiusAtDet)).coerceIn(0.0, 1.0)
                val encircledPowerFraction = passRatio * passRatio
                val landedPowerW = arrivingPowerW * encircledPowerFraction

                val detOpticalEfficiencyPct = if (source.opticalPowerW > 0.0) {
                    (landedPowerW / source.opticalPowerW) * 100.0
                } else 0.0

                val encircledPowerPct = encircledPowerFraction * 100.0
                val spilloverLossPct = 100.0 - encircledPowerPct

                val spotRadiusOnDetM = min(detRadiusMm, beamRadiusAtDet) / 1000.0
                val spotAreaM2 = kotlin.math.PI * spotRadiusOnDetM * spotRadiusOnDetM

                val integratedIrradianceWm2 = if (spotAreaM2 > 0.0) landedPowerW / spotAreaM2 else 0.0
                val integratedIrradiancemWcm2 = integratedIrradianceWm2 / 10.0

                val detApertureAreaM2 = kotlin.math.PI * (detRadiusMm / 1000.0) * (detRadiusMm / 1000.0)
                val apertureFillPct = if (detApertureAreaM2 > 0.0) {
                    ((spotAreaM2 / detApertureAreaM2) * 100.0).coerceIn(0.0, 100.0)
                } else 0.0

                val effectiveFocusZ = lastWaistZMm ?: calculateSystemFocalZ(activeElements)

                detectorAnalysisResult = DetectorAnalysisResult(
                    detectorName = elem.name,
                    detectorZMm = elem.positionZMm,
                    detectorApertureMm = elem.clearApertureMm,
                    spotDiameterMm = spotDiamMm,
                    landedPowerW = landedPowerW,
                    arrivingPowerW = arrivingPowerW,
                    opticalEfficiencyPct = detOpticalEfficiencyPct,
                    encircledPowerPct = encircledPowerPct,
                    spilloverLossPct = spilloverLossPct,
                    apertureFillPct = apertureFillPct,
                    integratedIrradianceWm2 = integratedIrradianceWm2,
                    integratedIrradiancemWcm2 = integratedIrradiancemWcm2,
                    peakIrradianceWm2 = integratedIrradianceWm2,
                    bestFocusZMm = effectiveFocusZ
                )

                // Terminate propagation at detector plane
                break
            }

            // 3. Clear Aperture Clipping & Transmission Ratio at Element Front Face zFront
            val apertureRadius = elem.clearApertureMm / 2.0
            val beamRadiusAtElem = getTopUnblockedRadius(rayBundle, apertureRadius)

            if (beamRadiusAtElem > apertureRadius && apertureRadius > 0.0) {
                val passRatio = (apertureRadius / beamRadiusAtElem).coerceIn(0.0, 1.0)
                geometricCollectionFraction *= (passRatio * passRatio)

                // Block rays exceeding aperture boundary at front face
                for (ray in rayBundle) {
                    if (!ray.isBlocked && abs(ray.y) > apertureRadius) {
                        ray.isBlocked = true
                    }
                }
            }

            // Propagate rays from zFront to principal plane zElem if not already there
            if (zElem > currentZ) {
                val dzToElem = zElem - currentZ
                propagateRayBundleInPlace(rayBundle, dzToElem)
                currentZ = zElem
            }

            // 4. Optical Element Refraction / Reflection at Principal Plane zElem: Δu = -y / f
            when (elem.type) {
                OpticalElementType.PLANO_CONVEX,
                OpticalElementType.BICONVEX,
                OpticalElementType.ACHROMATIC_DOUBLET,
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
                }
                OpticalElementType.APERTURE_STOP -> {
                    // Pure aperture stop: no slope change
                }
                OpticalElementType.DETECTOR -> {
                    // Handled above
                }
            }

            // 5. Internal propagation through optic thickness from principal plane zElem to zBack
            val dzRear = zBack - currentZ
            if (dzRear > 0) {
                val startTopYLens = getTopUnblockedRadius(rayBundle, 0.1)
                propagateRayBundleInPlace(rayBundle, dzRear)
                val endTopYLens = getTopUnblockedRadius(rayBundle, startTopYLens)

                segments.add(
                    BeamSegment(
                        startZ = zFront,
                        endZ = zBack,
                        startTopY = startTopYLens,
                        startBottomY = -startTopYLens,
                        endTopY = endTopYLens,
                        endBottomY = -endTopYLens,
                        color = sourceColor
                    )
                )

                currentZ = zBack
            }
        }

        // 6. Free-space propagation past final element to viewport boundary (if no detector stopped it)
        if (detectorAnalysisResult == null) {
            val maxElementZ = activeElements.maxOfOrNull { it.positionZMm + it.thicknessMm / 2.0 } ?: 100.0
            val targetZBound = maxOf(maxViewportZ, source.positionZMm, maxElementZ)
            val endZ = targetZBound + maxOf(60.0, targetZBound * 0.1)

            if (endZ > currentZ) {
                val dz = endZ - currentZ
                val startTopY = getTopUnblockedRadius(rayBundle, 0.1)

                val intermediateWaistZ = findWaistZBetween(rayBundle, currentZ, endZ)

                if (intermediateWaistZ != null) {
                    val dz1 = intermediateWaistZ - currentZ
                    val waistRays = propagateRayBundleTemp(rayBundle, dz1)
                    val waistTopY = getTopUnblockedRadius(waistRays, startTopY)

                    segments.add(
                        BeamSegment(
                            startZ = currentZ,
                            endZ = intermediateWaistZ,
                            startTopY = startTopY,
                            startBottomY = -startTopY,
                            endTopY = waistTopY,
                            endBottomY = -waistTopY,
                            color = sourceColor
                        )
                    )

                    val dz2 = endZ - intermediateWaistZ
                    propagateRayBundleInPlace(rayBundle, dz1)
                    val endTopY = getTopUnblockedRadius(propagateRayBundleTemp(rayBundle, dz2), waistTopY)

                    segments.add(
                        BeamSegment(
                            startZ = intermediateWaistZ,
                            endZ = endZ,
                            startTopY = waistTopY,
                            startBottomY = -waistTopY,
                            endTopY = endTopY,
                            endBottomY = -endTopY,
                            color = sourceColor
                        )
                    )

                    propagateRayBundleInPlace(rayBundle, dz2)
                    lastWaistZMm = intermediateWaistZ
                } else {
                    val endTopY = getTopUnblockedRadius(propagateRayBundleTemp(rayBundle, dz), startTopY)
                    segments.add(
                        BeamSegment(
                            startZ = currentZ,
                            endZ = endZ,
                            startTopY = startTopY,
                            startBottomY = -startTopY,
                            endTopY = endTopY,
                            endBottomY = -endTopY,
                            color = sourceColor
                        )
                    )
                    propagateRayBundleInPlace(rayBundle, dz)
                }
            }
        }

        // Output Divergence Angle calculation from unblocked transmitted ray bundle slopes
        val unblockedRays = rayBundle.filter { !it.isBlocked }
        val maxFinalSlope = if (unblockedRays.isNotEmpty()) {
            unblockedRays.maxOf { abs(it.u) }
        } else initialSlope

        val outputDivergenceDeg = (2.0 * atan(maxFinalSlope) * 180.0 / kotlin.math.PI)

        // Spot size at detector screen
        val detectorZ = detectorAnalysisResult?.detectorZMm
            ?: (activeElements.firstOrNull { it.type == OpticalElementType.DETECTOR }?.positionZMm
                ?: (activeElements.lastOrNull()?.positionZMm ?: 150.0))
        val spotSize = detectorAnalysisResult?.spotDiameterMm ?: calculateSpotSizeAtZ(segments, detectorZ)

        // Spherical Aberration accumulated across all elements
        val sphericalAb = calculateSphericalAberration(activeElements)

        val netEfficiencyFraction = geometricCollectionFraction * fresnelTransmissionFraction
        val geometricCollectionPct = (geometricCollectionFraction * 100.0).coerceIn(0.0, 100.0)
        val croppedLossPct = 100.0 - geometricCollectionPct
        val fresnelTransmissionPct = (fresnelTransmissionFraction * 100.0).coerceIn(0.0, 100.0)
        val reflectionLossPct = geometricCollectionPct * (1.0 - fresnelTransmissionFraction)

        val finalOpticalEfficiencyPct = detectorAnalysisResult?.opticalEfficiencyPct
            ?: (netEfficiencyFraction * 100.0).coerceIn(0.0, 100.0)
        val finalOutputPowerW = detectorAnalysisResult?.landedPowerW
            ?: (source.opticalPowerW * netEfficiencyFraction)

        // Radiometric Calculations: Power & Radiant Intensity
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

        // Determine system paraxial focal Z distance
        val effectiveFocalZ = lastWaistZMm ?: calculateSystemFocalZ(activeElements)

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
            sphericalAberrationMm = sphericalAb,
            sourcePowerW = source.opticalPowerW,
            outputPowerW = finalOutputPowerW,
            outputSolidAngleSr = solidAngleSr,
            radiantIntensityWsr = radiantIntensityWsr,
            detectorResult = detectorAnalysisResult
        )
    }

    fun calculateIrradianceAtRange(
        result: AnalyticalOpticsResult,
        targetZMm: Double
    ): IrradianceAtRangeResult {
        val spotDiamMm = calculateSpotSizeAtZ(result.beamSegments, targetZMm)
        val spotRadiusM = (spotDiamMm / 2.0) / 1000.0
        val areaM2 = kotlin.math.PI * spotRadiusM * spotRadiusM

        val irradianceWm2 = if (areaM2 > 0.0) {
            result.outputPowerW / areaM2
        } else {
            0.0
        }

        return IrradianceAtRangeResult(
            targetZMm = targetZMm,
            spotDiameterMm = spotDiamMm,
            spotAreaM2 = areaM2,
            irradianceWm2 = irradianceWm2,
            irradiancemWcm2 = irradianceWm2 / 10.0
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

    private fun propagateRayBundleTemp(bundle: List<OpticalRay>, dz: Double): List<OpticalRay> {
        return bundle.map {
            if (it.isBlocked) it.copy() else OpticalRay(it.y + it.u * dz, it.u, it.y0, false)
        }
    }

    private fun findWaistZBetween(bundle: List<OpticalRay>, startZ: Double, endZ: Double): Double? {
        val unblocked = bundle.filter { !it.isBlocked }
        // Prefer axial rays (originating from center of source y0=0) to calculate paraxial focus
        val axialRays = unblocked.filter { abs(it.y0) < 1e-5 }
        val candidateRays = if (axialRays.isNotEmpty()) axialRays else unblocked

        val convergingRay = candidateRays.firstOrNull { it.y > 0 && it.u < 0 } ?: return null
        val dzWaist = -convergingRay.y / convergingRay.u
        val waistZ = startZ + dzWaist
        return if (waistZ > (startZ + 1e-3) && waistZ < (endZ - 1e-3)) waistZ else null
    }

    private fun calculateSystemFocalZ(elements: List<OpticalElement>): Double? {
        var parallelRayY = 1.0
        var parallelRayU = 0.0
        var currentZ = 0.0

        val lensElements = elements.filter {
            it.type != OpticalElementType.APERTURE_STOP && it.type != OpticalElementType.DETECTOR
        }
        if (lensElements.isEmpty()) return null

        for (elem in lensElements) {
            val dz = elem.positionZMm - currentZ
            parallelRayY += parallelRayU * dz
            currentZ = elem.positionZMm

            val f = elem.focalLengthMm
            if (f != 0.0) {
                parallelRayU -= parallelRayY / f
            }
        }

        if (parallelRayU < 0.0) {
            val dzToFocus = -parallelRayY / parallelRayU
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

    private fun calculateSphericalAberration(
        elements: List<OpticalElement>
    ): Double {
        var totalLsa = 0.0
        for (lens in elements) {
            if (lens.type == OpticalElementType.APERTURE_STOP || lens.type == OpticalElementType.DETECTOR) continue
            val f = lens.focalLengthMm
            if (f == 0.0) continue

            val nd = lens.material.nd
            val yIn = lens.clearApertureMm / 2.0

            val shapeFactor = when (lens.type) {
                OpticalElementType.PLANO_CONVEX -> 0.25
                OpticalElementType.ACHROMATIC_DOUBLET -> 0.08
                OpticalElementType.BICONVEX -> 0.35
                OpticalElementType.PARABOLIC_MIRROR -> 0.02
                OpticalElementType.APERTURE_STOP, OpticalElementType.DETECTOR -> 0.0
            }

            val lsa = (yIn * yIn / (2.0 * abs(f))) * ((nd * nd) / ((nd - 1.0) * (nd - 1.0))) * shapeFactor
            totalLsa += lsa
        }

        return abs(totalLsa)
    }
}
