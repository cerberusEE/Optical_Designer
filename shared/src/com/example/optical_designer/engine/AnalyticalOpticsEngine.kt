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
    val weight: Double = 1.0,
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

        // Generate phase-space sampled ray bundle with radiometric power weighting
        val rawRays = mutableListOf<OpticalRay>()
        val ySampleCount = 21
        val uSampleCount = 41

        for (j in 0 until ySampleCount) {
            val tY = if (ySampleCount > 1) -1.0 + 2.0 * j / (ySampleCount - 1) else 0.0
            val y0 = tY * emitterHalfHeight

            for (i in 0 until uSampleCount) {
                val tU = if (uSampleCount > 1) -1.0 + 2.0 * i / (uSampleCount - 1) else 0.0
                val u0 = tU * initialSlope
                val theta = atan(u0)

                val wSpatial = when (source.sourceType) {
                    com.example.optical_designer.model.SourceType.LASER_DIODE,
                    com.example.optical_designer.model.SourceType.COLLIMATED -> {
                        kotlin.math.exp(-2.0 * tY * tY)
                    }
                    com.example.optical_designer.model.SourceType.LED,
                    com.example.optical_designer.model.SourceType.POINT_SOURCE -> {
                        1.0
                    }
                }

                val wAngular = when (source.sourceType) {
                    com.example.optical_designer.model.SourceType.LASER_DIODE,
                    com.example.optical_designer.model.SourceType.COLLIMATED -> {
                        val refHalfRad = max(1e-6, divHalfRad)
                        kotlin.math.exp(-2.0 * (theta / refHalfRad).pow(2))
                    }
                    com.example.optical_designer.model.SourceType.LED -> {
                        val absSin = abs(kotlin.math.sin(theta))
                        val cosTheta = cos(theta)
                        cosTheta * max(0.05, absSin)
                    }
                    com.example.optical_designer.model.SourceType.POINT_SOURCE -> {
                        val absSin = abs(kotlin.math.sin(theta))
                        max(0.05, absSin)
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

        // Trace ray bundle through active optical elements
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

            // 2. DETECTOR Screen handling: calculate encircled power & irradiance based on radiometric ray weights
            if (elem.type == OpticalElementType.DETECTOR) {
                val beamRadiusAtDet = getTopUnblockedRadius(rayBundle, 0.1)
                val spotDiamMm = beamRadiusAtDet * 2.0
                val detRadiusMm = max(0.001, elem.clearApertureMm / 2.0)

                val unblockedDetRays = rayBundle.filter { !it.isBlocked }
                val arrivingWeightFraction = unblockedDetRays.sumOf { it.weight }
                val arrivingPowerW = source.opticalPowerW * arrivingWeightFraction * fresnelTransmissionFraction

                val landedRays = unblockedDetRays.filter { abs(it.y) <= detRadiusMm }
                val landedWeightFraction = landedRays.sumOf { it.weight }
                val landedPowerW = source.opticalPowerW * landedWeightFraction * fresnelTransmissionFraction

                val detOpticalEfficiencyPct = if (source.opticalPowerW > 0.0) {
                    (landedPowerW / source.opticalPowerW) * 100.0
                } else 0.0

                val encircledPowerFraction = if (arrivingWeightFraction > 0.0) {
                    (landedWeightFraction / arrivingWeightFraction).coerceIn(0.0, 1.0)
                } else 0.0

                val encircledPowerPct = encircledPowerFraction * 100.0
                val spilloverLossPct = 100.0 - encircledPowerPct

                val spotRadiusOnDetM = min(detRadiusMm, beamRadiusAtDet) / 1000.0
                val spotAreaM2 = kotlin.math.PI * spotRadiusOnDetM * spotRadiusOnDetM

                val integratedIrradianceWm2 = if (spotAreaM2 > 0.0) landedPowerW / spotAreaM2 else 0.0
                val integratedIrradiancemWcm2 = integratedIrradianceWm2 / 10.0

                // Radiometric peak irradiance using spatial ray density distribution
                val centralRays = landedRays.filter { abs(it.y) <= 0.25 * max(1e-4, beamRadiusAtDet) }
                val centralWeightFraction = centralRays.sumOf { it.weight }
                val peakMultiplier = if (landedWeightFraction > 0.0 && beamRadiusAtDet > 0.0) {
                    val centralAreaFraction = 0.0625 // (0.25)^2
                    (centralWeightFraction / landedWeightFraction) / centralAreaFraction
                } else 1.0
                val peakIrradianceWm2 = integratedIrradianceWm2 * peakMultiplier.coerceIn(1.0, 3.0)

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
                    peakIrradianceWm2 = peakIrradianceWm2,
                    bestFocusZMm = effectiveFocusZ
                )

                // Terminate propagation at detector plane
                break
            }

            // 3. Clear Aperture Clipping & Ray Weight Collection Fraction at zFront
            val apertureRadius = elem.clearApertureMm / 2.0
            val beamRadiusAtElem = getTopUnblockedRadius(rayBundle, apertureRadius)

            if (apertureRadius > 0.0) {
                // Block rays exceeding clear aperture boundary
                for (ray in rayBundle) {
                    if (!ray.isBlocked && abs(ray.y) > apertureRadius) {
                        ray.isBlocked = true
                    }
                }
            }

            // Update geometric collection fraction from surviving ray weights
            val unblockedWeight = rayBundle.filter { !it.isBlocked }.sumOf { it.weight }
            geometricCollectionFraction = unblockedWeight

            // Top radius of transmitted (unblocked) rays entering the element at zFront:
            val transmittedTopY = if (apertureRadius > 0.0) {
                min(apertureRadius, beamRadiusAtElem)
            } else {
                beamRadiusAtElem
            }

            // Propagate rays from zFront to refraction plane if not already there
            val zRefract = if (elem.type == OpticalElementType.PLANO_CONVEX) zFront else zElem
            if (zRefract > currentZ) {
                val dzToElem = zRefract - currentZ
                propagateRayBundleInPlace(rayBundle, dzToElem)
                currentZ = zRefract
            }

            // 4. Optical Element Refraction / Reflection at Refraction Plane: Δu = -y / f
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

                    // Track focal waist Z for metrics if rays are converging
                    val unblocked = rayBundle.filter { !it.isBlocked }
                    val axialRays = unblocked.filter { abs(it.y0) < 1e-5 }
                    val candidateRays = axialRays.ifEmpty { unblocked }
                    val convergingRay = candidateRays.firstOrNull { it.y > 0 && it.u < 0 }
                    if (convergingRay != null) {
                        val dzWaist = -convergingRay.y / convergingRay.u
                        val waistZ = currentZ + dzWaist
                        if (waistZ > currentZ) {
                            lastWaistZMm = waistZ
                        }
                    }
                }
                OpticalElementType.APERTURE_STOP -> {
                    // Pure aperture stop: no slope change
                }
                OpticalElementType.DETECTOR -> {
                    // Handled above
                }
            }

            // 5. Internal propagation through optic thickness to zBack
            val dzRear = zBack - currentZ
            if (dzRear > 0) {
                propagateRayBundleInPlace(rayBundle, dzRear)
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

                currentZ = zBack
            }
        }

        // 6. Free-space propagation past final element to viewport boundary (if no detector stopped it)
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

        // Output Divergence Angle calculation based on beam energy distribution
        val unblockedRays = rayBundle.filter { !it.isBlocked }
        val outputDivergenceDeg = if (unblockedRays.isNotEmpty()) {
            val totalUnblockedWeight = unblockedRays.sumOf { it.weight }.coerceAtLeast(1e-12)
            val meanU = unblockedRays.sumOf { it.weight * it.u } / totalUnblockedWeight
            val varianceU = unblockedRays.sumOf { it.weight * (it.u - meanU) * (it.u - meanU) } / totalUnblockedWeight
            val rmsU = sqrt(max(0.0, varianceU))

            val effectiveSlope = when (source.sourceType) {
                com.example.optical_designer.model.SourceType.LASER_DIODE,
                com.example.optical_designer.model.SourceType.COLLIMATED -> {
                    // Gaussian 1/e^2 divergence angle definition: 2 * RMS slope
                    2.0 * rmsU
                }
                com.example.optical_designer.model.SourceType.LED,
                com.example.optical_designer.model.SourceType.POINT_SOURCE -> {
                    // LED / Lambertian divergence: 95th percentile encircled angular power slope
                    val sortedRays = unblockedRays.sortedBy { abs(it.u) }
                    var cumWeight = 0.0
                    val targetWeight = 0.95 * totalUnblockedWeight
                    var p95Slope = abs(sortedRays.last().u)
                    for (r in sortedRays) {
                        cumWeight += r.weight
                        if (cumWeight >= targetWeight) {
                            p95Slope = abs(r.u)
                            break
                        }
                    }
                    val profileSlope = max(sqrt(3.0) * rmsU, p95Slope)
                    min(sortedRays.maxOf { abs(it.u) }, profileSlope)
                }
            }
            (2.0 * atan(effectiveSlope) * 180.0 / kotlin.math.PI)
        } else source.divergenceYDeg

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
        var parallelRayY = 1.0
        var parallelRayU = 0.0
        var currentZ = 0.0

        val lensElements = elements.filter {
            it.type != OpticalElementType.APERTURE_STOP && it.type != OpticalElementType.DETECTOR
        }
        if (lensElements.isEmpty()) return null

        for ((_, _, _, positionZMm, _, f) in lensElements) {
            val dz = positionZMm - currentZ
            parallelRayY += parallelRayU * dz
            currentZ = positionZMm

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
        for ((_, _, type, _, clearApertureMm, f, _, material) in elements) {
            if (type == OpticalElementType.APERTURE_STOP || type == OpticalElementType.DETECTOR) continue
            if (f == 0.0) continue

            val nd = material.nd
            val yIn = clearApertureMm / 2.0

            val shapeFactor = when (type) {
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
