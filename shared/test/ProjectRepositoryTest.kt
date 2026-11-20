package com.example.optical_designer

import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.model.OpticalProject
import com.example.optical_designer.storage.ProjectRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectRepositoryTest {

    @Test
    fun testDefaultActiveProjectIsLoaded() {
        val active = ProjectRepository.loadActiveProject()
        assertNotNull(active)
        assertTrue(active.name.isNotBlank())
        assertTrue(active.elements.isNotEmpty())
    }

    @Test
    fun testSavingAndRestoringActiveProject() {
        val customProject = OpticalProject(
            id = "test_custom_active",
            name = "My Custom Laser System",
            source = OpticalPresets.BEAM_EXPANDER.source,
            elements = OpticalPresets.BEAM_EXPANDER.elements
        )

        ProjectRepository.saveActiveProject(customProject)
        val loaded = ProjectRepository.loadActiveProject()

        assertEquals("test_custom_active", loaded.id)
        assertEquals("My Custom Laser System", loaded.name)
        assertEquals(OpticalPresets.BEAM_EXPANDER.elements.size, loaded.elements.size)
    }

    @Test
    fun testSavedProjectsLibraryCRUD() {
        val proj1 = OpticalProject(
            id = "lib_p1",
            name = "Collimator Design 1",
            source = OpticalPresets.LED_COLLIMATOR.source,
            elements = OpticalPresets.LED_COLLIMATOR.elements
        )
        val proj2 = OpticalProject(
            id = "lib_p2",
            name = "Beam Expander Design 2",
            source = OpticalPresets.BEAM_EXPANDER.source,
            elements = OpticalPresets.BEAM_EXPANDER.elements
        )

        // Save proj1 to library
        ProjectRepository.saveProjectToLibrary(proj1)
        var saved = ProjectRepository.getSavedProjects()
        assertTrue(saved.any { it.id == "lib_p1" })

        // Save proj2 to library
        ProjectRepository.saveProjectToLibrary(proj2)
        saved = ProjectRepository.getSavedProjects()
        assertTrue(saved.any { it.id == "lib_p1" })
        assertTrue(saved.any { it.id == "lib_p2" })

        // Delete proj1
        ProjectRepository.deleteProjectFromLibrary("lib_p1")
        saved = ProjectRepository.getSavedProjects()
        assertTrue(saved.none { it.id == "lib_p1" })
        assertTrue(saved.any { it.id == "lib_p2" })
    }

    @Test
    fun testEvaluationRangePersistence() {
        ProjectRepository.saveEvaluationRangeMeters(10.5)
        val loaded = ProjectRepository.getSavedEvaluationRangeMeters()
        assertEquals(10.5, loaded)
    }
}
