package com.example.optical_designer.storage

import com.example.optical_designer.engine.OpticalPreset
import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.model.OpticalProject
import com.example.optical_designer.util.currentTimeMillis
import com.example.optical_designer.util.generateUuid

object ProjectRepository {

    private const val KEY_ACTIVE_PROJECT = "active_optical_project"
    private const val KEY_SAVED_PROJECTS = "saved_optical_projects_list"
    private const val KEY_EVALUATION_RANGE_METERS = "evaluation_range_z_meters"

    fun loadActiveProject(): OpticalProject {
        val json = PlatformStorage.getString(KEY_ACTIVE_PROJECT)
        if (!json.isNullOrBlank()) {
            val project = ProjectJsonSerializer.deserializeProject(json)
            if (project != null) return project
        }
        // Default initial project if none saved
        val defaultPreset = OpticalPresets.LED_COLLIMATOR_1
        val initialProject = OpticalProject(
            id = "proj_default_led_collimator",
            name = defaultPreset.name,
            updatedAt = currentTimeMillis(),
            source = defaultPreset.source,
            elements = defaultPreset.elements
        )
        saveActiveProject(initialProject)
        return initialProject
    }

    fun saveActiveProject(project: OpticalProject) {
        val json = ProjectJsonSerializer.serializeProject(project)
        PlatformStorage.saveString(KEY_ACTIVE_PROJECT, json)
    }

    fun getSavedProjects(): List<OpticalProject> {
        val json = PlatformStorage.getString(KEY_SAVED_PROJECTS)
        if (json.isNullOrBlank()) return emptyList()
        return ProjectJsonSerializer.deserializeProjectList(json)
    }

    fun saveProjectToLibrary(project: OpticalProject): OpticalProject {
        val currentList = getSavedProjects().toMutableList()
        val updatedProject = project.copy(updatedAt = currentTimeMillis())
        val index = currentList.indexOfFirst { it.id == updatedProject.id }

        if (index >= 0) {
            currentList[index] = updatedProject
        } else {
            currentList.add(0, updatedProject) // Add to top
        }

        val json = ProjectJsonSerializer.serializeProjectList(currentList)
        PlatformStorage.saveString(KEY_SAVED_PROJECTS, json)

        // Also sync to active project
        saveActiveProject(updatedProject)
        return updatedProject
    }

    fun deleteProjectFromLibrary(projectId: String) {
        val currentList = getSavedProjects().toMutableList()
        val removed = currentList.removeAll { it.id == projectId }
        if (removed) {
            val json = ProjectJsonSerializer.serializeProjectList(currentList)
            PlatformStorage.saveString(KEY_SAVED_PROJECTS, json)
        }
    }

    fun createNewProject(name: String = "New Optical Project", preset: OpticalPreset? = null): OpticalProject {
        val template = preset ?: OpticalPresets.LED_COLLIMATOR_1
        val newProject = OpticalProject(
            id = generateUuid(),
            name = name.ifBlank { "New Optical Project" },
            updatedAt = currentTimeMillis(),
            source = template.source,
            elements = template.elements
        )
        saveActiveProject(newProject)
        return newProject
    }

    fun getSavedEvaluationRangeMeters(): Double? {
        val str = PlatformStorage.getString(KEY_EVALUATION_RANGE_METERS)
        return str?.toDoubleOrNull()
    }

    fun saveEvaluationRangeMeters(rangeMeters: Double) {
        PlatformStorage.saveString(KEY_EVALUATION_RANGE_METERS, rangeMeters.toString())
    }
}
