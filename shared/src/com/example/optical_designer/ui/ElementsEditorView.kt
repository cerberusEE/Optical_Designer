package com.example.optical_designer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.optical_designer.engine.OpticalPreset
import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.model.GlassMaterial
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.SourceType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementsEditorView(
    source: LightSource,
    onSourceChange: (LightSource) -> Unit,
    elements: List<OpticalElement>,
    onElementsChange: (List<OpticalElement>) -> Unit,
    onSelectPreset: (OpticalPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSourceDialog by remember { mutableStateOf(false) }
    var editingElement by remember { mutableStateOf<OpticalElement?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Presets Quick Picker Row ---
            item {
                Column {
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (preset in OpticalPresets.ALL_PRESETS) {
                            FilterChip(
                                selected = false,
                                onClick = { onSelectPreset(preset) },
                                label = { Text(preset.name, fontSize = 13.sp) }
                            )
                        }
                    }
                }
            }

            // --- Light Source Card ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${source.sourceType.displayName} • ${source.cwlNm.roundToInt()}nm Monochromatic",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { showSourceDialog = true }) {
                                Text("Edit Source")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Divergence: ${source.divergenceXDeg.roundToInt()}° × ${source.divergenceYDeg.roundToInt()}°",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Emitter: ${source.emitterWidthMm} × ${source.emitterHeightMm} mm",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // --- Optical Elements List Section Header ---
            item {
                Text(
                    text = "Optical Elements (${elements.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // --- Optical Elements Cards ---
            itemsIndexed(elements, key = { _, item -> item.id }) { index, elem ->
                ElementCard(
                    element = elem,
                    onPositionChange = { newZ ->
                        val updated = elements.toMutableList()
                        updated[index] = elem.copy(positionZMm = newZ)
                        onElementsChange(updated)
                    },
                    onToggleEnable = { enabled ->
                        val updated = elements.toMutableList()
                        updated[index] = elem.copy(enabled = enabled)
                        onElementsChange(updated)
                    },
                    onMoveUp = if (index > 0) {
                        {
                            val updated = elements.toMutableList()
                            val temp = updated[index]
                            updated[index] = updated[index - 1]
                            updated[index - 1] = temp
                            onElementsChange(updated)
                        }
                    } else null,
                    onMoveDown = if (index < elements.size - 1) {
                        {
                            val updated = elements.toMutableList()
                            val temp = updated[index]
                            updated[index] = updated[index + 1]
                            updated[index + 1] = temp
                            onElementsChange(updated)
                        }
                    } else null,
                    onDelete = {
                        val updated = elements.toMutableList()
                        updated.removeAt(index)
                        onElementsChange(updated)
                    },
                    onEdit = { editingElement = elem }
                )
            }

            item {
                Spacer(modifier = Modifier.height(70.dp)) // Padding for FAB
            }
        }

        // --- Floating Add Element Button ---
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text("+ Add Element", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onPrimary)
        }
    }

    // --- Light Source Edit Modal Dialog ---
    if (showSourceDialog) {
        LightSourceDialog(
            source = source,
            onDismiss = { showSourceDialog = false },
            onSave = { updated ->
                onSourceChange(updated)
                showSourceDialog = false
            }
        )
    }

    // --- Add New Element Modal Dialog ---
    if (showAddDialog) {
        AddElementDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newElem ->
                onElementsChange(elements + newElem)
                showAddDialog = false
            }
        )
    }

    // --- Element Detail Edit Modal Dialog ---
    editingElement?.let { elem ->
        EditElementDialog(
            element = elem,
            onDismiss = { editingElement = null },
            onSave = { updated ->
                onElementsChange(elements.map { if (it.id == updated.id) updated else it })
                editingElement = null
            }
        )
    }
}

@Composable
private fun ElementCard(
    element: OpticalElement,
    onPositionChange: (Double) -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (element.enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = element.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${element.type.displayName} • ${element.material.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = element.enabled,
                        onCheckedChange = onToggleEnable
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Real-time Position Z Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Position Z: ${element.positionZMm.roundToInt()} mm",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "f = ${element.focalLengthMm.roundToInt()}mm | Ø ${element.clearApertureMm.roundToInt()}mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = element.positionZMm.toFloat(),
                    onValueChange = { onPositionChange(it.toDouble()) },
                    valueRange = 0f..300f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                onMoveUp?.let {
                    TextButton(onClick = it) { Text("▲ Up", fontSize = 12.sp) }
                }
                onMoveDown?.let {
                    TextButton(onClick = it) { Text("▼ Down", fontSize = 12.sp) }
                }
                TextButton(onClick = onEdit) { Text("Edit", fontSize = 12.sp) }
                TextButton(onClick = onDelete) { Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LightSourceDialog(
    source: LightSource,
    onDismiss: () -> Unit,
    onSave: (LightSource) -> Unit
) {
    var name by remember { mutableStateOf(source.name) }
    var sourceType by remember { mutableStateOf(source.sourceType) }
    var cwl by remember { mutableStateOf(source.cwlNm.toFloat()) }
    var divX by remember { mutableStateOf(source.divergenceXDeg.toFloat()) }
    var divY by remember { mutableStateOf(source.divergenceYDeg.toFloat()) }
    var emitterW by remember { mutableStateOf(source.emitterWidthMm.toFloat()) }
    var emitterH by remember { mutableStateOf(source.emitterHeightMm.toFloat()) }

    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Light Source") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = sourceType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Source Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            for (type in SourceType.entries) {
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        sourceType = type
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Wavelength: ${cwl.roundToInt()} nm")
                    Slider(value = cwl, onValueChange = { cwl = it }, valueRange = 380f..850f)
                }

                item {
                    Text("Divergence X (deg): ${divX.roundToInt()}°")
                    Slider(value = divX, onValueChange = { divX = it }, valueRange = 0f..120f)
                }

                item {
                    Text("Divergence Y (deg): ${divY.roundToInt()}°")
                    Slider(value = divY, onValueChange = { divY = it }, valueRange = 0f..120f)
                }

                item {
                    Text("Emitter Size: ${emitterW.format(1)} × ${emitterH.format(1)} mm")
                    Row {
                        Slider(
                            value = emitterW,
                            onValueChange = { emitterW = it },
                            valueRange = 0.05f..5f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = emitterH,
                            onValueChange = { emitterH = it },
                            valueRange = 0.05f..5f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    source.copy(
                        name = name,
                        sourceType = sourceType,
                        cwlNm = cwl.toDouble(),
                        divergenceXDeg = divX.toDouble(),
                        divergenceYDeg = divY.toDouble(),
                        emitterWidthMm = emitterW.toDouble(),
                        emitterHeightMm = emitterH.toDouble()
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddElementDialog(
    onDismiss: () -> Unit,
    onAdd: (OpticalElement) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Optical Element") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in OpticalElementType.entries) {
                    OutlinedButton(
                        onClick = {
                            val newElem = createDefaultElement(type)
                            onAdd(newElem)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(type.displayName)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditElementDialog(
    element: OpticalElement,
    onDismiss: () -> Unit,
    onSave: (OpticalElement) -> Unit
) {
    var name by remember { mutableStateOf(element.name) }
    var focalLength by remember { mutableStateOf(element.focalLengthMm.toFloat()) }
    var aperture by remember { mutableStateOf(element.clearApertureMm.toFloat()) }
    var thickness by remember { mutableStateOf(element.thicknessMm.toFloat()) }
    var material by remember { mutableStateOf(element.material) }

    var matDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${element.type.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (element.type != OpticalElementType.DETECTOR && element.type != OpticalElementType.APERTURE_STOP) {
                    Text("Focal Length f: ${focalLength.roundToInt()} mm")
                    Slider(value = focalLength, onValueChange = { focalLength = it }, valueRange = 10f..200f)

                    ExposedDropdownMenuBox(
                        expanded = matDropdownExpanded,
                        onExpandedChange = { matDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = material.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Glass Material") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = matDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = matDropdownExpanded,
                            onDismissRequest = { matDropdownExpanded = false }
                        ) {
                            for (mat in GlassMaterial.PRESETS) {
                                DropdownMenuItem(
                                    text = { Text("${mat.name} (nd=${mat.nd}, Vd=${mat.vd})") },
                                    onClick = {
                                        material = mat
                                        matDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text("Clear Aperture Ø: ${aperture.roundToInt()} mm")
                Slider(value = aperture, onValueChange = { aperture = it }, valueRange = 5f..100f)

                if (element.type != OpticalElementType.DETECTOR && element.type != OpticalElementType.APERTURE_STOP) {
                    Text("Center Thickness: ${thickness.format(1)} mm")
                    Slider(value = thickness, onValueChange = { thickness = it }, valueRange = 1f..20f)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    element.copy(
                        name = name,
                        focalLengthMm = focalLength.toDouble(),
                        clearApertureMm = aperture.toDouble(),
                        thicknessMm = thickness.toDouble(),
                        material = material
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun createDefaultElement(type: OpticalElementType): OpticalElement {
    val id = "elem_${(1000..9999).random()}"
    return when (type) {
        OpticalElementType.PLANO_CONVEX -> OpticalElement(id, "Plano-Convex Lens", type, 60.0, 30.0, 50.0, 6.0)
        OpticalElementType.BICONVEX -> OpticalElement(id, "Biconvex Lens", type, 60.0, 30.0, 50.0, 6.0)
        OpticalElementType.ACHROMATIC_DOUBLET -> OpticalElement(id, "Achromatic Doublet", type, 60.0, 35.0, 60.0, 8.0)
        OpticalElementType.PARABOLIC_MIRROR -> OpticalElement(id, "Parabolic Mirror", type, 50.0, 40.0, 50.0, 5.0)
        OpticalElementType.APERTURE_STOP -> OpticalElement(id, "Iris Aperture", type, 40.0, 15.0, 0.0, 1.0)
        OpticalElementType.DETECTOR -> OpticalElement(id, "Detector Screen", type, 180.0, 50.0, 0.0, 1.0)
    }
}

private fun Float.format(digits: Int): String {
    val factor = when (digits) {
        1 -> 10.0f
        2 -> 100.0f
        else -> 1.0f
    }
    return (kotlin.math.round(this * factor) / factor).toString()
}
