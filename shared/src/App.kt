package com.example.optical_designer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.optical_designer.ui.OpticalDesignerApp
import org.jetbrains.compose.reload.DevelopmentEntryPoint

@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        OpticalDesignerApp()
    }
}
