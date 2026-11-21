package com.example.optical_designer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.optical_designer.engine.AnalyticalOpticsEngine
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.storage.ProjectRepository
import kotlinx.coroutines.launch

enum class NavTab(val title: String) {
    VIEWER("Viewer"),
    ELEMENTS("Elements"),
    ANALYSIS("Analysis")
}

@Composable
fun OpticalDesignerApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Load active project session from persistent storage
    var currentProject by remember { mutableStateOf(ProjectRepository.loadActiveProject()) }
    var lightSource by remember(currentProject.id) { mutableStateOf(currentProject.source) }
    var opticalElements by remember(currentProject.id) { mutableStateOf(currentProject.elements) }

    var showProjectManager by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Auto-persist active project session whenever lightSource, opticalElements, or project name changes
    fun persistActiveSession(
        updatedSource: LightSource = lightSource,
        updatedElements: List<OpticalElement> = opticalElements,
        updatedName: String = currentProject.name
    ) {
        val updatedProject = currentProject.copy(
            name = updatedName,
            source = updatedSource,
            elements = updatedElements
        )
        currentProject = updatedProject
        ProjectRepository.saveActiveProject(updatedProject)
    }

    // Compute live analytical optics result
    val opticsResult = remember(lightSource, opticalElements) {
        AnalyticalOpticsEngine.calculateSystem(lightSource, opticalElements)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentProject.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                actions = {
                    OutlinedButton(
                        onClick = { showProjectManager = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("📁 Projects")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(tab.title) },
                        icon = {
                            Text(
                                text = when (tab) {
                                    NavTab.VIEWER -> "🔍"
                                    NavTab.ELEMENTS -> "⚙️"
                                    NavTab.ANALYSIS -> "📊"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (NavTab.entries[selectedTab]) {
                NavTab.VIEWER -> {
                    RayTraceView(
                        source = lightSource,
                        elements = opticalElements,
                        opticsResult = opticsResult,
                        onElementsChange = { newElements ->
                            opticalElements = newElements
                            persistActiveSession(updatedElements = newElements)
                        }
                    )
                }
                NavTab.ELEMENTS -> {
                    ElementsEditorView(
                        source = lightSource,
                        onSourceChange = { newSource ->
                            lightSource = newSource
                            persistActiveSession(updatedSource = newSource)
                        },
                        elements = opticalElements,
                        onElementsChange = { newElements ->
                            opticalElements = newElements
                            persistActiveSession(updatedElements = newElements)
                        },
                        onSelectPreset = { preset ->
                            lightSource = preset.source
                            opticalElements = preset.elements
                            persistActiveSession(
                                updatedSource = preset.source,
                                updatedElements = preset.elements,
                                updatedName = preset.name
                            )
                        }
                    )
                }
                NavTab.ANALYSIS -> {
                    AnalysisView(
                        source = lightSource,
                        elements = opticalElements,
                        result = opticsResult
                    )
                }
            }
        }
    }

    if (showProjectManager) {
        ProjectManagerDialog(
            currentProject = currentProject,
            onProjectNameChange = { newName ->
                persistActiveSession(updatedName = newName)
            },
            onSaveProject = {
                val saved = ProjectRepository.saveProjectToLibrary(currentProject)
                currentProject = saved
                scope.launch {
                    snackbarHostState.showSnackbar("Project '${saved.name}' saved!")
                }
            },
            onLoadProject = { loaded ->
                currentProject = loaded
                lightSource = loaded.source
                opticalElements = loaded.elements
                ProjectRepository.saveActiveProject(loaded)
                scope.launch {
                    snackbarHostState.showSnackbar("Loaded project '${loaded.name}'")
                }
            },
            onLoadPreset = { preset ->
                val newProject = ProjectRepository.createNewProject(name = preset.name, preset = preset)
                currentProject = newProject
                lightSource = newProject.source
                opticalElements = newProject.elements
                scope.launch {
                    snackbarHostState.showSnackbar("Loaded preset '${preset.name}'")
                }
            },
            onNewProject = {
                val newProject = ProjectRepository.createNewProject("New Optical Design")
                currentProject = newProject
                lightSource = newProject.source
                opticalElements = newProject.elements
                scope.launch {
                    snackbarHostState.showSnackbar("Created new project")
                }
            },
            onDismissRequest = { showProjectManager = false }
        )
    }
}
