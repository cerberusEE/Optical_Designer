package com.example.optical_designer.model

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Wavelength helper class for optical spectrum rendering.
 */
data class Wavelength(
    val nm: Double
) {
    /**
     * Converts wavelength in nm to an RGB Compose Color representation for rendering the light beam.
     */
    fun toColor(): Color {
        val wl = nm
        var r = 0.0
        var g = 0.0
        var b = 0.0

        when {
            wl in 380.0..440.0 -> {
                r = -(wl - 440.0) / (440.0 - 380.0)
                g = 0.0
                b = 1.0
            }
            wl in 440.0..490.0 -> {
                r = 0.0
                g = (wl - 440.0) / (490.0 - 440.0)
                b = 1.0
            }
            wl in 490.0..510.0 -> {
                r = 0.0
                g = 1.0
                b = -(wl - 510.0) / (510.0 - 490.0)
            }
            wl in 510.0..580.0 -> {
                r = (wl - 510.0) / (580.0 - 510.0)
                g = 1.0
                b = 0.0
            }
            wl in 580.0..645.0 -> {
                r = 1.0
                g = -(wl - 645.0) / (645.0 - 580.0)
                b = 0.0
            }
            wl in 645.0..780.0 -> {
                r = 1.0
                g = 0.0
                b = 0.0
            }
            wl < 380.0 -> { // UV
                r = 0.5
                g = 0.0
                b = 0.8
            }
            else -> { // IR (>780nm)
                r = 0.8
                g = 0.1
                b = 0.1
            }
        }

        // Intensity factor near vision limits
        val factor = when {
            wl in 380.0..420.0 -> 0.3 + 0.7 * (wl - 380.0) / (420.0 - 380.0)
            wl in 420.0..700.0 -> 1.0
            wl in 700.0..780.0 -> 0.3 + 0.7 * (780.0 - wl) / (780.0 - 700.0)
            else -> 0.5
        }

        val gamma = 0.8
        val red = ((r * factor).coerceIn(0.0, 1.0).pow(gamma) * 255).roundToInt()
        val green = ((g * factor).coerceIn(0.0, 1.0).pow(gamma) * 255).roundToInt()
        val blue = ((b * factor).coerceIn(0.0, 1.0).pow(gamma) * 255).roundToInt()

        return Color(red, green, blue)
    }
}

/**
 * Optical Glass material parameters.
 */
data class GlassMaterial(
    val name: String,
    val nd: Double,
    val vd: Double,
    val surfaceReflectance: Double = 0.04 // ~4% reflection per uncoated surface
) {
    companion object {
        val AIR = GlassMaterial("Air", nd = 1.0, vd = 0.0, surfaceReflectance = 0.0)
        val N_BK7 = GlassMaterial("N-BK7 (Crown)", nd = 1.5168, vd = 64.17)
        val F2 = GlassMaterial("F2 (Flint)", nd = 1.6200, vd = 36.37)
        val N_SF11 = GlassMaterial("N-SF11 (Dense Flint)", nd = 1.7847, vd = 25.76)
        val ACRYLIC = GlassMaterial("Acrylic (PMMA)", nd = 1.4910, vd = 57.4)
        val FUSED_SILICA = GlassMaterial("Fused Silica", nd = 1.4585, vd = 67.8)

        val PRESETS = listOf(N_BK7, F2, N_SF11, ACRYLIC, FUSED_SILICA)
    }
}

enum class SourceType(val displayName: String) {
    LED("LED Source"),
    LASER_DIODE("Laser Diode"),
    COLLIMATED("Collimated Laser"),
    POINT_SOURCE("Point Source")
}

data class LightSource(
    val id: String = "light_source",
    val name: String = "Light Source",
    val sourceType: SourceType = SourceType.LED,
    val cwlNm: Double = 520.0,         // Center Wavelength in nm
    val divergenceXDeg: Double = 30.0,   // Full angle X divergence in deg (optional 2-axis)
    val divergenceYDeg: Double = 30.0,   // Full angle Y divergence in deg (for 2-axis)
    val emitterWidthMm: Double = 1.0,    // Emitter X dimension in mm
    val emitterHeightMm: Double = 1.0,   // Emitter Y dimension in mm
    val positionZMm: Double = 0.0        // Z location on optical axis
)

enum class OpticalElementType(val displayName: String) {
    PLANO_CONVEX("Plano-Convex Lens"),
    BICONVEX("Biconvex Lens"),
    ACHROMATIC_DOUBLET("Achromatic Doublet"),
    PARABOLIC_MIRROR("Parabolic Mirror"),
    APERTURE_STOP("Aperture Stop / Iris"),
    DETECTOR("Detector Screen")
}

data class OpticalElement(
    val id: String,
    val name: String,
    val type: OpticalElementType,
    val positionZMm: Double,
    val clearApertureMm: Double, // Diameter / clear height in mm
    val focalLengthMm: Double = 50.0, // Nominal focal length in mm
    val thicknessMm: Double = 5.0,     // Center thickness of lens/element
    val material: GlassMaterial = GlassMaterial.N_BK7,
    val enabled: Boolean = true
)
