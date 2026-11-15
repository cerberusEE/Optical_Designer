package com.example.optical_designer.engine

import androidx.compose.ui.graphics.Color
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.Wavelength
import kotlin.math.abs
import kotlin.math.atan
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
    val sphericalAberrationMm: Double
)

object AnalyticalOpticsEngine {

    fun calculateSystem(
        source: LightSource,
        elements: List<OpticalElement>,
        maxViewportZ: Double = 300.0
    ): AnalyticalOpticsResult {
        val activeElements = elements.filter { it.enabled }.sortedBy { it.positionZMm }
        val sourceColor = Wavelength(source.cwlNm).toColor()

        val divHalfRad = (source.divergenceYDeg * kotlin.math.PI / 360.0)
        val initialHeightHalf = source.emitterHeightMm / 2.0

        // Initial beam state at source (z = positionZMm)
        var currentTopSlope = tan(divHalfRad)
        var currentBottomSlope = -tan(divHalfRad)

        var currentTopY = initialHeightHalf
        var currentBottomY = -initialHeightHalf
        var currentZ = source.positionZMm

        var geometricCollectionFraction = 1.0
        var fresnelTransmissionFraction = 1.0

        val segments = mutableListOf<BeamSegment>()

        var effectiveFocalZ = Double.NaN
        var finalDivergenceRad = divHalfRad

        for (elem in activeElements) {
            if (elem.positionZMm <= currentZ) continue

            val zNext = elem.positionZMm
            val dz = zNext - currentZ

            // Propagate beam to element Z position
            val topYAtElem = currentTopY + currentTopSlope * dz
            val bottomYAtElem = currentBottomY + currentBottomSlope * dz

            // Add beam segment from currentZ to zNext
            segments.add(
                BeamSegment(
                    startZ = currentZ,
                    endZ = zNext,
                    startTopY = currentTopY,
                    startBottomY = currentBottomY,
                    endTopY = topYAtElem,
                    endBottomY = bottomYAtElem,
                    color = sourceColor
                )
            )

            currentZ = zNext
            val apertureHalf = elem.clearApertureMm / 2.0

            // Check clipping at clear aperture boundary
            val beamRadius = max(abs(topYAtElem), abs(bottomYAtElem))
            if (beamRadius > apertureHalf) {
                // Efficiency area fraction loss
                val passRatio = (apertureHalf / beamRadius).coerceIn(0.0, 1.0)
                geometricCollectionFraction *= (passRatio * passRatio)

                // Clamp beam height exiting aperture
                currentTopY = apertureHalf
                currentBottomY = -apertureHalf
            } else {
                currentTopY = topYAtElem
                currentBottomY = bottomYAtElem
            }

            // Apply element optical transformation
            when (elem.type) {
                OpticalElementType.PLANO_CONVEX, OpticalElementType.BICONVEX, OpticalElementType.ACHROMATIC_DOUBLET -> {
                    val f = elem.focalLengthMm
                    if (f != 0.0) {
                        val distanceToSource = currentZ - source.positionZMm

                        // Central ray divergence slope transformation: m_out = m_in * (1 - z_L / f)
                        val mCentralIn = tan(divHalfRad)
                        val mCentralOut = mCentralIn * (1.0 - distanceToSource / f)

                        // Extended emitter size divergence contribution: m_emitter = (h/2) / f
                        val mEmitterOut = (source.emitterHeightMm / 2.0) / abs(f)

                        val totalHalfSlope = abs(mCentralOut) + mEmitterOut
                        finalDivergenceRad = atan(totalHalfSlope)

                        currentTopSlope = mCentralOut - mEmitterOut
                        currentBottomSlope = -mCentralOut + mEmitterOut

                        if (currentTopSlope != currentBottomSlope) {
                            val dy = currentTopY - currentBottomY
                            val dSlope = currentTopSlope - currentBottomSlope
                            if (dSlope != 0.0) {
                                effectiveFocalZ = currentZ - (dy / dSlope)
                            }
                        }

                        // Fresnel surface reflection loss
                        fresnelTransmissionFraction *= (1.0 - elem.material.surfaceReflectance * 2.0)
                    }
                }

                OpticalElementType.PARABOLIC_MIRROR -> {
                    val f = elem.focalLengthMm
                    if (f != 0.0) {
                        val distanceToSource = currentZ - source.positionZMm
                        val mCentralIn = tan(divHalfRad)
                        val mCentralOut = mCentralIn * (1.0 - distanceToSource / f)
                        val mEmitterOut = (source.emitterHeightMm / 2.0) / abs(f)

                        val totalHalfSlope = abs(mCentralOut) + mEmitterOut
                        finalDivergenceRad = atan(totalHalfSlope)

                        currentTopSlope = -(mCentralOut - mEmitterOut)
                        currentBottomSlope = -(-mCentralOut + mEmitterOut)

                        fresnelTransmissionFraction *= 0.95 // Reflectivity
                    }
                }

                OpticalElementType.APERTURE_STOP, OpticalElementType.DETECTOR -> {
                    // No change to beam slope
                }
            }
        }

        // Final propagation step past last element to edge of viewport
        val endZ = max(maxViewportZ, (activeElements.lastOrNull()?.positionZMm ?: 100.0) + 60.0)
        if (endZ > currentZ) {
            val dz = endZ - currentZ
            val endTopY = currentTopY + currentTopSlope * dz
            val endBottomY = currentBottomY + currentBottomSlope * dz

            segments.add(
                BeamSegment(
                    startZ = currentZ,
                    endZ = endZ,
                    startTopY = currentTopY,
                    startBottomY = currentBottomY,
                    endTopY = endTopY,
                    endBottomY = endBottomY,
                    color = sourceColor
                )
            )
        }

        // Spot size at detector screen
        val detector = activeElements.firstOrNull { it.type == OpticalElementType.DETECTOR }
        val detectorZ = detector?.positionZMm ?: (activeElements.lastOrNull()?.positionZMm ?: 150.0)
        val spotSize = calculateSpotSizeAtZ(segments, detectorZ)

        // Spherical Aberration Calculation
        val sphericalAb = calculateSphericalAberration(activeElements)

        val netEfficiencyFraction = geometricCollectionFraction * fresnelTransmissionFraction
        val geometricCollectionPct = (geometricCollectionFraction * 100.0).coerceIn(0.0, 100.0)
        val croppedLossPct = 100.0 - geometricCollectionPct
        val fresnelTransmissionPct = (fresnelTransmissionFraction * 100.0).coerceIn(0.0, 100.0)
        val reflectionLossPct = geometricCollectionPct * (1.0 - fresnelTransmissionFraction)
        val netEfficiencyPct = (netEfficiencyFraction * 100.0).coerceIn(0.0, 100.0)

        return AnalyticalOpticsResult(
            beamSegments = segments,
            opticalEfficiencyPct = netEfficiencyPct,
            geometricCollectionPct = geometricCollectionPct,
            croppedLossPct = croppedLossPct,
            fresnelTransmissionPct = fresnelTransmissionPct,
            reflectionLossPct = reflectionLossPct,
            inputDivergenceDeg = source.divergenceYDeg,
            outputDivergenceDeg = (finalDivergenceRad * 360.0 / kotlin.math.PI),
            spotDiameterAtDetectorMm = spotSize,
            focalDistanceMm = if (effectiveFocalZ.isNaN()) 0.0 else effectiveFocalZ,
            sphericalAberrationMm = sphericalAb
        )
    }

    private fun calculateSpotSizeAtZ(segments: List<BeamSegment>, targetZ: Double): Double {
        val seg = segments.firstOrNull { targetZ in it.startZ..it.endZ } ?: return 0.0
        val t = if (seg.endZ > seg.startZ) (targetZ - seg.startZ) / (seg.endZ - seg.startZ) else 0.0
        val topY = seg.startTopY + t * (seg.endTopY - seg.startTopY)
        val bottomY = seg.startBottomY + t * (seg.endBottomY - seg.startBottomY)
        return abs(topY - bottomY)
    }

    private fun calculateSphericalAberration(
        elements: List<OpticalElement>
    ): Double {
        val lens = elements.firstOrNull {
            it.type == OpticalElementType.PLANO_CONVEX ||
            it.type == OpticalElementType.BICONVEX ||
            it.type == OpticalElementType.ACHROMATIC_DOUBLET
        } ?: return 0.0

        if (lens.focalLengthMm == 0.0) return 0.0

        val nd = lens.material.nd
        val yIn = min(lens.clearApertureMm / 2.0, 20.0)
        val f = lens.focalLengthMm

        val sphericalAb = if (lens.type == OpticalElementType.ACHROMATIC_DOUBLET) {
            0.1 * (yIn * yIn) / (2.0 * f)
        } else {
            (yIn * yIn / (2.0 * f)) * ((nd * nd) / ((nd - 1.0) * (nd - 1.0))) * 0.25
        }

        return abs(sphericalAb)
    }
}
