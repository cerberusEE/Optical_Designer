package com.example.optical_designer

import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.model.GlassMaterial
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.OpticalProject
import com.example.optical_designer.model.SourceType
import com.example.optical_designer.storage.ProjectJsonSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProjectSerializationTest {

    @Test
    fun testSingleProjectSerializationAndDeserialization() {
        val original = OpticalProject(
            id = "proj_test_1",
            name = "Test Laser System",
            updatedAt = 1700000000000L,
            source = LightSource(
                id = "src_1",
                name = "633nm HeNe Laser",
                sourceType = SourceType.LASER_DIODE,
                cwlNm = 632.8,
                opticalPowerW = 0.005,
                divergenceXDeg = 1.5,
                divergenceYDeg = 1.5,
                emitterWidthMm = 0.8,
                emitterHeightMm = 0.8,
                positionZMm = 0.0
            ),
            elements = listOf(
                OpticalElement(
                    id = "elem_1",
                    name = "Collimator Lens",
                    type = OpticalElementType.BICONVEX,
                    positionZMm = 45.0,
                    clearApertureMm = 25.0,
                    focalLengthMm = 45.0,
                    explicitThicknessMm = 4.2,
                    material = GlassMaterial.FUSED_SILICA,
                    isCoated = true,
                    customTransmissionPct = 99.5,
                    enabled = true
                )
            )
        )

        val json = ProjectJsonSerializer.serializeProject(original)
        assertNotNull(json)

        val restored = ProjectJsonSerializer.deserializeProject(json)
        assertNotNull(restored)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.updatedAt, restored.updatedAt)
        assertEquals(original.source.name, restored.source.name)
        assertEquals(original.source.cwlNm, restored.source.cwlNm)
        assertEquals(original.elements.size, restored.elements.size)

        val restoredElem = restored.elements.first()
        assertEquals("Collimator Lens", restoredElem.name)
        assertEquals(OpticalElementType.BICONVEX, restoredElem.type)
        assertEquals(45.0, restoredElem.positionZMm)
        assertEquals(4.2, restoredElem.explicitThicknessMm)
        assertEquals(GlassMaterial.FUSED_SILICA.name, restoredElem.material.name)
        assertEquals(true, restoredElem.isCoated)
        assertEquals(99.5, restoredElem.customTransmissionPct)
    }

    @Test
    fun testProjectListSerializationAndDeserialization() {
        val project1 = OpticalProject(
            id = "p1",
            name = "Preset 1 Project",
            source = OpticalPresets.LED_COLLIMATOR.source,
            elements = OpticalPresets.LED_COLLIMATOR.elements
        )
        val project2 = OpticalProject(
            id = "p2",
            name = "Preset 2 Project",
            source = OpticalPresets.BEAM_EXPANDER.source,
            elements = OpticalPresets.BEAM_EXPANDER.elements
        )

        val projects = listOf(project1, project2)
        val json = ProjectJsonSerializer.serializeProjectList(projects)
        val restoredList = ProjectJsonSerializer.deserializeProjectList(json)

        assertEquals(2, restoredList.size)
        assertEquals("p1", restoredList[0].id)
        assertEquals("Preset 1 Project", restoredList[0].name)
        assertEquals("p2", restoredList[1].id)
        assertEquals("Preset 2 Project", restoredList[1].name)
    }
}
