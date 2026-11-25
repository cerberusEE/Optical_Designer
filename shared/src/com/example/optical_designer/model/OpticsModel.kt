package com.example.optical_designer.model

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Wavelength helper class for optical spectrum rendering.
 * Supports wavelengths from vacuum UV to far LWIR (1 nm - 100 µm).
 * Uses CIE 1931 Color Matching Functions for visible spectrum (400 - 700 nm)
 * and light gray / neutral contrast gray for non-visible UV and IR wavelengths.
 */
data class Wavelength(
    val nm: Double
) {
    /**
     * Converts wavelength in nm to a perceived RGB Compose Color representation.
     * Evaluates CIE 1931 color matching functions for 400 - 700 nm, and light gray for UV/IR.
     */
    fun toColor(isDarkTheme: Boolean = true): Color {
        val wl = nm

        // Outside visible spectrum (vacuum UV < 400 nm or NIR/MWIR/LWIR > 700 nm):
        // Return light gray for dark theme, or neutral medium-dark gray for light theme
        if (wl !in 400.0..700.0) {
            return if (isDarkTheme) Color(0xFFB0BEC5) else Color(0xFF546E7A)
        }

        // Visible spectrum (400nm - 700nm): CIE 1931 Color Matching Functions (CMFs)
        val x = cieX(wl)
        val y = cieY(wl)
        val z = cieZ(wl)

        // Convert XYZ to linear sRGB via CIE D65 matrix
        var rLin =  3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        var gLin = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        var bLin =  0.0556434 * x - 0.2040259 * y + 1.0572252 * z

        // Normalize color so peak chromaticity component is 1.0
        val maxComp = maxOf(rLin, gLin, bLin, 0.0001)
        rLin = (rLin / maxComp).coerceIn(0.0, 1.0)
        gLin = (gLin / maxComp).coerceIn(0.0, 1.0)
        bLin = (bLin / maxComp).coerceIn(0.0, 1.0)

        // sRGB gamma correction (gamma ≈ 2.2)
        val gamma = 1.0 / 2.2
        var r = rLin.pow(gamma)
        var g = gLin.pow(gamma)
        var b = bLin.pow(gamma)

        // Adjust contrast slightly for light theme
        if (!isDarkTheme) {
            r *= 0.85
            g *= 0.85
            b *= 0.85
        }

        val red = (r.coerceIn(0.0, 1.0) * 255).roundToInt()
        val green = (g.coerceIn(0.0, 1.0) * 255).roundToInt()
        val blue = (b.coerceIn(0.0, 1.0) * 255).roundToInt()

        return Color(red, green, blue)
    }

    private fun gaussian(x: Double, alpha: Double, mu: Double, sigma1: Double, sigma2: Double): Double {
        val d = x - mu
        val sig = if (d < 0.0) sigma1 else sigma2
        return alpha * kotlin.math.exp(-0.5 * (d / sig) * (d / sig))
    }

    private fun cieX(wl: Double): Double {
        return gaussian(wl, 1.056, 599.8, 37.9, 31.0) +
               gaussian(wl, 0.362, 442.0, 16.0, 26.7) -
               gaussian(wl, 0.065, 501.1, 20.4, 26.2)
    }

    private fun cieY(wl: Double): Double {
        return gaussian(wl, 0.821, 568.8, 46.9, 40.5) +
               gaussian(wl, 0.286, 530.9, 16.3, 31.1)
    }

    private fun cieZ(wl: Double): Double {
        return gaussian(wl, 1.217, 437.0, 11.8, 36.0) +
               gaussian(wl, 0.681, 459.0, 26.0, 13.8)
    }
}

/**
 * Optical Glass material parameters with Thorlabs / Edmund Optics properties.
 */
data class GlassMaterial(
    val name: String,
    val nd: Double,
    val vd: Double,
    val surfaceReflectance: Double = 0.04, // Uncoated surface Fresnel reflection fraction
    val internalTransmissionPct: Double = 99.0 // Internal transmittance percentage
) {
    companion object {
        val N_BK7 = GlassMaterial("N-BK7 (Crown)", nd = 1.5168, vd = 64.17, surfaceReflectance = 0.04, internalTransmissionPct = 99.2)
        val FUSED_SILICA = GlassMaterial("Fused Silica (Quartz)", nd = 1.4585, vd = 67.8, surfaceReflectance = 0.035, internalTransmissionPct = 99.5)
        val GERMANIUM = GlassMaterial("Germanium (Ge)", nd = 4.0028, vd = 100.0, surfaceReflectance = 0.36, internalTransmissionPct = 95.0)
        val SILICON = GlassMaterial("Silicon (Si)", nd = 3.4223, vd = 90.0, surfaceReflectance = 0.30, internalTransmissionPct = 96.0)
        val ZNSE = GlassMaterial("Zinc Selenide (ZnSe)", nd = 2.4028, vd = 70.0, surfaceReflectance = 0.17, internalTransmissionPct = 98.0)
        val CAF2 = GlassMaterial("Calcium Fluoride (CaF2)", nd = 1.4338, vd = 95.2, surfaceReflectance = 0.032, internalTransmissionPct = 99.5)
        val SAPPHIRE = GlassMaterial("Sapphire (Al2O3)", nd = 1.7682, vd = 72.2, surfaceReflectance = 0.077, internalTransmissionPct = 98.5)
        val F2 = GlassMaterial("F2 (Flint)", nd = 1.6200, vd = 36.37, surfaceReflectance = 0.05, internalTransmissionPct = 98.5)
        val N_SF11 = GlassMaterial("N-SF11 (Dense Flint)", nd = 1.7847, vd = 25.76, surfaceReflectance = 0.079, internalTransmissionPct = 97.0)
        val ACRYLIC = GlassMaterial("Acrylic (PMMA)", nd = 1.4910, vd = 57.4, surfaceReflectance = 0.039, internalTransmissionPct = 98.0)

        val PRESETS = listOf(
            N_BK7,
            FUSED_SILICA,
            GERMANIUM,
            SILICON,
            ZNSE,
            CAF2,
            SAPPHIRE,
            F2,
            N_SF11,
            ACRYLIC
        )
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
    val opticalPowerW: Double = 1.0,   // Total source radiant flux / power in Watts
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

enum class LensOrientation(val displayName: String) {
    CURVED_FACE_LEFT("Curved Face Left"),
    FLAT_FACE_LEFT("Flat Face Left");

    companion object {
        fun fromBoolean(isFlipped: Boolean): LensOrientation =
            if (isFlipped) FLAT_FACE_LEFT else CURVED_FACE_LEFT
    }
}

data class OpticalElement(
    val id: String,
    val name: String,
    val type: OpticalElementType,
    val positionZMm: Double,
    val clearApertureMm: Double, // Diameter / clear height in mm
    val focalLengthMm: Double = 50.0, // Nominal focal length in mm
    val explicitThicknessMm: Double? = null, // Optional explicit thickness override
    val material: GlassMaterial = GlassMaterial.N_BK7,
    val isCoated: Boolean = false, // Checkbox for AR coated optics / custom transmission override
    val customTransmissionPct: Double = 99.0, // Manual transmission entry percentage when coated
    val orientation: LensOrientation = LensOrientation.CURVED_FACE_LEFT, // Explicit lens surface orientation
    val enabled: Boolean = true
) {
    /** Helper property for backward compatibility */
    val isFlipped: Boolean
        get() = orientation == LensOrientation.FLAT_FACE_LEFT
    /**
     * Calculates center thickness automatically assuming a spherical lens with given focal length, aperture, and glass refractive index.
     */
    val thicknessMm: Double
        get() = explicitThicknessMm ?: calculateLensThickness(type, focalLengthMm, clearApertureMm, material.nd)

    /**
     * Calculates net optical throughput transmission fraction for this element.
     */
    val effectiveTransmissionFraction: Double
        get() {
            if (!enabled) return 1.0
            if (type == OpticalElementType.DETECTOR || type == OpticalElementType.APERTURE_STOP) return 1.0
            if (type == OpticalElementType.PARABOLIC_MIRROR) return 0.95
            if (isCoated) return (customTransmissionPct / 100.0).coerceIn(0.0, 1.0)

            val surfaceLoss = (1.0 - material.surfaceReflectance * 2.0).coerceIn(0.0, 1.0)
            val internalTrans = (material.internalTransmissionPct / 100.0).coerceIn(0.0, 1.0)
            return surfaceLoss * internalTrans
        }
}

/**
 * Calculates center thickness (mm) for spherical optics based on lens maker equation and sagitta.
 */
fun calculateLensThickness(
    type: OpticalElementType,
    focalLengthMm: Double,
    clearApertureMm: Double,
    nd: Double
): Double {
    val minEdgeThicknessMm = 1.5
    val y = clearApertureMm / 2.0

    return when (type) {
        OpticalElementType.PLANO_CONVEX -> {
            if (focalLengthMm <= 0.0 || nd <= 1.0) {
                minEdgeThicknessMm
            } else {
                val r = abs(focalLengthMm) * (nd - 1.0)
                val effectiveR = max(r, y * 1.001)
                val sag = effectiveR - sqrt(effectiveR * effectiveR - y * y)
                (sag + minEdgeThicknessMm).coerceIn(1.0, 100.0)
            }
        }
        OpticalElementType.BICONVEX -> {
            if (focalLengthMm <= 0.0 || nd <= 1.0) {
                minEdgeThicknessMm
            } else {
                val r = 2.0 * abs(focalLengthMm) * (nd - 1.0)
                val effectiveR = max(r, y * 1.001)
                val sag = effectiveR - sqrt(effectiveR * effectiveR - y * y)
                (2.0 * sag + minEdgeThicknessMm).coerceIn(1.0, 100.0)
            }
        }
        OpticalElementType.ACHROMATIC_DOUBLET -> {
            if (focalLengthMm <= 0.0 || nd <= 1.0) {
                minEdgeThicknessMm * 2.0
            } else {
                val r = 2.0 * abs(focalLengthMm) * (nd - 1.0)
                val effectiveR = max(r, y * 1.001)
                val sag = effectiveR - sqrt(effectiveR * effectiveR - y * y)
                (2.0 * sag + minEdgeThicknessMm * 1.5).coerceIn(2.0, 100.0)
            }
        }
        OpticalElementType.PARABOLIC_MIRROR -> 5.0
        OpticalElementType.APERTURE_STOP -> 1.0
        OpticalElementType.DETECTOR -> 1.0
    }
}
