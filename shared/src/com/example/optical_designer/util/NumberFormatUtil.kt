package com.example.optical_designer.util

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

object NumberFormatUtil {

    /**
     * Parses a string containing standard decimal or scientific notation (e.g., "100", "1.5", "1e3", "1.5e-3", "1.2E6").
     * Returns null if parsing fails.
     */
    fun parseDoubleOrNull(input: String): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.toDoubleOrNull()
    }

    /**
     * Formats a Double value into a clean human-readable representation with at most [maxSigFigs] significant digits.
     * Uses scientific notation for extreme values (abs(val) >= 1e6 or abs(val) <= 1e-4).
     */
    fun formatToDisplay(value: Double, maxSigFigs: Int = 3): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        if (value == 0.0) return "0"

        val sigFigs = max(1, maxSigFigs)
        val sign = if (value < 0) "-" else ""
        val absVal = abs(value)

        var exp = floor(log10(absVal)).toInt()
        val mantissa = absVal / 10.0.pow(exp.toDouble())

        val factor = 10.0.pow((sigFigs - 1).toDouble())
        var roundedMantissa = round(mantissa * factor) / factor

        if (roundedMantissa >= 10.0) {
            roundedMantissa /= 10.0
            exp += 1
        }

        return if ((exp >= 6) || (exp <= -4)) {
            val mantissaStr = formatDecimalString(roundedMantissa, sigFigs - 1)
            "$sign${mantissaStr}e$exp"
        } else {
            val scaled = roundedMantissa * 10.0.pow(exp.toDouble())
            val decimalsAfterPoint = max(0, sigFigs - 1 - exp)
            val formatted = formatDecimalString(scaled, decimalsAfterPoint)
            "$sign$formatted"
        }
    }

    private fun formatDecimalString(value: Double, decimalsAfterPoint: Int): String {
        if (decimalsAfterPoint <= 0) {
            return round(value).toLong().toString()
        }
        val factor = 10.0.pow(decimalsAfterPoint.toDouble())
        val totalUnits = round(value * factor).toLong()
        val intFactor = factor.toLong()
        val intPart = totalUnits / intFactor
        val fracPart = abs(totalUnits % intFactor)

        if (fracPart == 0L) {
            return intPart.toString()
        }
        val fracStr = fracPart.toString().padStart(decimalsAfterPoint, '0').trimEnd('0')
        return "$intPart.$fracStr"
    }

    /**
     * Formats distance in millimeters into appropriate units (µm, mm, m, km) for optical system display,
     * formatted with at most 3 significant digits.
     */
    fun formatDistanceUnit(distanceMm: Double): String {
        val absDist = abs(distanceMm)
        return when {
            absDist >= 1e6 -> "${formatToDisplay(distanceMm / 1e6, 3)} km"
            absDist >= 1000 -> "${formatToDisplay(distanceMm / 1000.0, 3)} m"
            (absDist < 0.001) && (absDist > 0) -> "${formatToDisplay(distanceMm * 1000.0, 3)} µm"
            else -> "${formatToDisplay(distanceMm, 3)} mm"
        }
    }

    /**
     * Formats optical power in Watts into human readable units (µW, mW, W, kW) with at most 3 significant digits.
     */
    fun formatPower(watts: Double): String {
        val absW = abs(watts)
        return when {
            absW >= 1e3 -> "${formatToDisplay(watts / 1e3, 3)} kW"
            absW >= 1.0 -> "${formatToDisplay(watts, 3)} W"
            absW >= 1e-3 -> "${formatToDisplay(watts * 1e3, 3)} mW"
            absW >= 1e-6 -> "${formatToDisplay(watts * 1e6, 3)} µW"
            else -> "${formatToDisplay(watts, 3)} W"
        }
    }

    /**
     * Formats radiant intensity (W/sr) into human readable units with at most 3 significant digits.
     */
    fun formatRadiantIntensity(wsr: Double): String {
        val absVal = abs(wsr)
        return when {
            absVal >= 1e6 -> "${formatToDisplay(wsr / 1e6, 3)} MW/sr"
            absVal >= 1e3 -> "${formatToDisplay(wsr / 1e3, 3)} kW/sr"
            absVal >= 1.0 -> "${formatToDisplay(wsr, 3)} W/sr"
            absVal >= 1e-3 -> "${formatToDisplay(wsr * 1e3, 3)} mW/sr"
            else -> "${formatToDisplay(wsr, 3)} W/sr"
        }
    }

    /**
     * Formats irradiance in W/m² with at most 3 significant digits.
     */
    fun formatIrradiance(wm2: Double): String {
        return "${formatToDisplay(wm2, 3)} W/m²"
    }

    /**
     * Calculates a "nice" rounded step size for grid axis rendering given a raw step requirement.
     */
    fun calculateNiceGridStep(rawStep: Double): Double {
        if (rawStep <= 0.0) return 1.0
        val exponent = floor(log10(rawStep))
        val fraction = rawStep / 10.0.pow(exponent)
        val niceFraction = when {
            fraction < 1.5 -> 1.0
            fraction < 3.0 -> 2.0
            fraction < 7.0 -> 5.0
            else -> 10.0
        }
        return niceFraction * 10.0.pow(exponent)
    }
}
