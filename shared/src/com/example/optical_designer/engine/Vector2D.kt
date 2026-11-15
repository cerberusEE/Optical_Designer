package com.example.optical_designer.engine

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 2D Vector in Optical coordinates (Z = optical axis, Y = transverse height).
 */
data class Vector2D(
    val z: Double,
    val y: Double
) {
    operator fun plus(v: Vector2D) = Vector2D(z + v.z, y + v.y)
    operator fun minus(v: Vector2D) = Vector2D(z - v.z, y - v.y)
    operator fun times(scalar: Double) = Vector2D(z * scalar, y * scalar)
    operator fun div(scalar: Double) = Vector2D(z / scalar, y / scalar)

    fun dot(v: Vector2D): Double = z * v.z + y * v.y
    fun length(): Double = sqrt(z * z + y * y)

    fun normalize(): Vector2D {
        val len = length()
        return if (len > 1e-12) Vector2D(z / len, y / len) else Vector2D(1.0, 0.0)
    }

    /**
     * Angle in degrees relative to the +Z optical axis.
     */
    fun angleDeg(): Double = atan2(y, z) * 180.0 / kotlin.math.PI

    companion object {
        val ZERO = Vector2D(0.0, 0.0)
        val UNIT_Z = Vector2D(1.0, 0.0)
    }
}
