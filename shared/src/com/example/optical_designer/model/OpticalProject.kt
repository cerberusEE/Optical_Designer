package com.example.optical_designer.model

import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.util.currentTimeMillis
import com.example.optical_designer.util.generateUuid

data class OpticalProject(
    val id: String = generateUuid(),
    val name: String = "Untitled Project",
    val updatedAt: Long = currentTimeMillis(),
    val source: LightSource = OpticalPresets.LED_COLLIMATOR_1.source,
    val elements: List<OpticalElement> = OpticalPresets.LED_COLLIMATOR_1.elements
)
