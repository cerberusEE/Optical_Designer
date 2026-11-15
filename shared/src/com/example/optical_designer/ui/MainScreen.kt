package com.example.optical_designer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.optical_designer.engine.AnalyticalOpticsEngine
import com.example.optical_designer.engine.OpticalPresets

enum class NavTab(val title: String) {
    VIEWER("Viewer"),
    ELEMENTS("Elements"),
    ANALYSIS("Analysis")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpticalDesignerApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Initial default state loaded from preset
    val initialPreset = OpticalPresets.LED_COLLIMATOR
    var lightSource by remember { mutableStateOf(initialPreset.source) }
    var opticalElements by remember { mutableStateOf(initialPreset.elements) }

    // Compute live analytical optics result
    val opticsResult = remember(lightSource, opticalElements) {
        AnalyticalOpticsEngine.calculateSystem(lightSource, opticalElements)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Optical Designer",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "LED & Laser Optics Workbench",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
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
                        opticsResult = opticsResult
                    )
                }
                NavTab.ELEMENTS -> {
                    ElementsEditorView(
                        source = lightSource,
                        onSourceChange = { lightSource = it },
                        elements = opticalElements,
                        onElementsChange = { opticalElements = it },
                        onSelectPreset = { preset ->
                            lightSource = preset.source
                            opticalElements = preset.elements
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
}
