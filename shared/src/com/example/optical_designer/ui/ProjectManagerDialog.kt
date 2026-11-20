package com.example.optical_designer.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.optical_designer.engine.OpticalPreset
import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.model.OpticalProject
import com.example.optical_designer.storage.ProjectRepository

@Composable
fun ProjectManagerDialog(
    currentProject: OpticalProject,
    onProjectNameChange: (String) -> Unit,
    onSaveProject: () -> Unit,
    onLoadProject: (OpticalProject) -> Unit,
    onLoadPreset: (OpticalPreset) -> Unit,
    onNewProject: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var projectNameInput by remember(currentProject.name) { mutableStateOf(currentProject.name) }
    var savedProjects by remember { mutableStateOf(ProjectRepository.getSavedProjects()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshSavedList() {
        savedProjects = ProjectRepository.getSavedProjects()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📁 Project Manager",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onDismissRequest) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Project Quick Bar with dedicated line for text field
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Active Workspace Project",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Full width project name edit box
                        OutlinedTextField(
                            value = projectNameInput,
                            onValueChange = {
                                projectNameInput = it
                                onProjectNameChange(it)
                            },
                            label = { Text("Project Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Save action button row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    onSaveProject()
                                    refreshSavedList()
                                    statusMessage = "Project '${currentProject.name}' saved!"
                                }
                            ) {
                                Text("💾 Save")
                            }
                        }

                        if (statusMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Saved Projects (${savedProjects.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Preset Templates") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content View based on selected tab
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> SavedProjectsTab(
                            savedProjects = savedProjects,
                            currentProjectId = currentProject.id,
                            onLoad = { proj ->
                                onLoadProject(proj)
                                statusMessage = "Loaded project '${proj.name}'"
                                onDismissRequest()
                            },
                            onSaveOver = { proj ->
                                onSaveProject()
                                refreshSavedList()
                                statusMessage = "Saved over '${proj.name}'"
                            },
                            onDelete = { projId ->
                                ProjectRepository.deleteProjectFromLibrary(projId)
                                refreshSavedList()
                                statusMessage = "Project deleted"
                            },
                            onNewProject = {
                                onNewProject()
                                refreshSavedList()
                                onDismissRequest()
                            }
                        )
                        1 -> PresetsTab(
                            onSelectPreset = { preset ->
                                onLoadPreset(preset)
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedProjectsTab(
    savedProjects: List<OpticalProject>,
    currentProjectId: String,
    onLoad: (OpticalProject) -> Unit,
    onSaveOver: (OpticalProject) -> Unit,
    onDelete: (String) -> Unit,
    onNewProject: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Saved Projects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = onNewProject,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("+ New Project")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (savedProjects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📁 No saved projects yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Click '💾 Save' above to save your current active project.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedProjects, key = { it.id }) { project ->
                    val isCurrent = project.id == currentProjectId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            // Project name on its own line to fit long names
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action buttons on row below
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onLoad(project) },
                                    enabled = !isCurrent
                                ) {
                                    Text(if (isCurrent) "Loaded" else "Load")
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = { onDelete(project.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetsTab(
    onSelectPreset: (OpticalPreset) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Start from a Preset Template",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(OpticalPresets.ALL_PRESETS) { preset ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { onSelectPreset(preset) }) {
                            Text("Use Preset")
                        }
                    }
                }
            }
        }
    }
}
