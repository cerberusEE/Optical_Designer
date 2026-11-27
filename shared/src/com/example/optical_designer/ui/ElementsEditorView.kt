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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.optical_designer.util.NumberFormatUtil

@Composable
fun ScientificNumberTextField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minConstraint: Double? = null,
    maxConstraint: Double? = null,
    allowNegative: Boolean = true
) {
    var textState by remember { mutableStateOf(NumberFormatUtil.formatToDisplay(value)) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        val currentParsed = NumberFormatUtil.parseDoubleOrNull(textState)
        if (currentParsed != value) {
            textState = NumberFormatUtil.formatToDisplay(value)
            isError = false
        }
    }

    OutlinedTextField(
        value = textState,
        onValueChange = { newText ->
            textState = newText
            val parsed = NumberFormatUtil.parseDoubleOrNull(newText)
            if (parsed != null) {
                val validMin = minConstraint == null || parsed >= minConstraint
                val validMax = maxConstraint == null || parsed <= maxConstraint
                val validNeg = allowNegative || parsed >= 0.0
                if (validMin && validMax && validNeg) {
                    isError = false
                    onValueChange(parsed)
                } else {
                    isError = true
                }
            } else {
                isError = newText.isNotBlank()
            }
        },
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) { { Text(placeholder) } } else null,
        isError = isError,
        singleLine = true,
        supportingText = if (isError) {
            { Text("Invalid number (supports e.g. 100, 1.5e3, 1e6)", color = MaterialTheme.colorScheme.error) }
        } else null,
        modifier = modifier
    )
}

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
                                    text = "${NumberFormatUtil.formatPower(source.opticalPowerW)} • ${source.sourceType.displayName} • ${NumberFormatUtil.formatToDisplay(source.cwlNm)}nm",
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
                                text = "Divergence: ${NumberFormatUtil.formatToDisplay(source.divergenceXDeg)}° × ${NumberFormatUtil.formatToDisplay(source.divergenceYDeg)}°",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Emitter: ${NumberFormatUtil.formatToDisplay(source.emitterWidthMm)} × ${NumberFormatUtil.formatToDisplay(source.emitterHeightMm)} mm",
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = element.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (element.type == OpticalElementType.DETECTOR) {
                            "Detector Screen • Circular Aperture"
                        } else if (element.type == OpticalElementType.APERTURE_STOP) {
                            "Aperture Stop / Iris"
                        } else if (element.isCoated) {
                            "${element.type.displayName} • ${element.material.name} (AR Coated ${NumberFormatUtil.formatToDisplay(element.customTransmissionPct, 1)}% T)"
                        } else {
                            "${element.type.displayName} • ${element.material.name}"
                        },
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

            Spacer(modifier = Modifier.height(8.dp))

            // Real-time Position Z Manual Entry Edit Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScientificNumberTextField(
                    value = element.positionZMm,
                    onValueChange = onPositionChange,
                    label = "Position Z (mm)",
                    modifier = Modifier.weight(1.2f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    if (element.type != OpticalElementType.DETECTOR && element.type != OpticalElementType.APERTURE_STOP) {
                        Text(
                            text = "f = ${NumberFormatUtil.formatToDisplay(element.focalLengthMm)} mm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Ø ${NumberFormatUtil.formatToDisplay(element.clearApertureMm)} mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    var cwl by remember { mutableStateOf(source.cwlNm) }
    var opticalPower by remember { mutableStateOf(source.opticalPowerW) }
    var divX by remember { mutableStateOf(source.divergenceXDeg) }
    var divY by remember { mutableStateOf(source.divergenceYDeg) }
    var emitterW by remember { mutableStateOf(source.emitterWidthMm) }
    var emitterH by remember { mutableStateOf(source.emitterHeightMm) }
    var posZ by remember { mutableStateOf(source.positionZMm) }

    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Light Source") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScientificNumberTextField(
                            value = cwl,
                            onValueChange = { cwl = it },
                            label = "Wavelength (nm)",
                            minConstraint = 1.0,
                            allowNegative = false,
                            modifier = Modifier.weight(1f)
                        )
                        ScientificNumberTextField(
                            value = opticalPower,
                            onValueChange = { opticalPower = it },
                            label = "Optical Power (W)",
                            minConstraint = 0.0001,
                            allowNegative = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    ScientificNumberTextField(
                        value = posZ,
                        onValueChange = { posZ = it },
                        label = "Position Z (mm)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScientificNumberTextField(
                            value = divX,
                            onValueChange = { divX = it },
                            label = "Divergence X (°)",
                            minConstraint = 0.0,
                            maxConstraint = 180.0,
                            allowNegative = false,
                            modifier = Modifier.weight(1f)
                        )
                        ScientificNumberTextField(
                            value = divY,
                            onValueChange = { divY = it },
                            label = "Divergence Y (°)",
                            minConstraint = 0.0,
                            maxConstraint = 180.0,
                            allowNegative = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScientificNumberTextField(
                            value = emitterW,
                            onValueChange = { emitterW = it },
                            label = "Emitter Width (mm)",
                            minConstraint = 0.0001,
                            allowNegative = false,
                            modifier = Modifier.weight(1f)
                        )
                        ScientificNumberTextField(
                            value = emitterH,
                            onValueChange = { emitterH = it },
                            label = "Emitter Height (mm)",
                            minConstraint = 0.0001,
                            allowNegative = false,
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
                        cwlNm = cwl,
                        opticalPowerW = opticalPower,
                        divergenceXDeg = divX,
                        divergenceYDeg = divY,
                        emitterWidthMm = emitterW,
                        emitterHeightMm = emitterH,
                        positionZMm = posZ
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
    var posZ by remember { mutableStateOf(element.positionZMm) }
    var focalLength by remember { mutableStateOf(element.focalLengthMm) }
    var aperture by remember { mutableStateOf(element.clearApertureMm) }
    var material by remember { mutableStateOf(element.material) }
    var isCoated by remember { mutableStateOf(element.isCoated) }
    var customTrans by remember { mutableStateOf(element.customTransmissionPct) }
    var orientation by remember { mutableStateOf(element.orientation) }

    var matDropdownExpanded by remember { mutableStateOf(false) }
    var orientDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${element.type.displayName}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    ScientificNumberTextField(
                        value = posZ,
                        onValueChange = { posZ = it },
                        label = "Position Z (mm)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (element.type != OpticalElementType.DETECTOR && element.type != OpticalElementType.APERTURE_STOP) {
                    item {
                        ScientificNumberTextField(
                            value = focalLength,
                            onValueChange = { focalLength = it },
                            label = "Focal Length f (mm)",
                            allowNegative = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
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
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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

                    item {
                        ExposedDropdownMenuBox(
                            expanded = orientDropdownExpanded,
                            onExpandedChange = { orientDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = orientation.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Lens Surface Orientation") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = orientDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = orientDropdownExpanded,
                                onDismissRequest = { orientDropdownExpanded = false }
                            ) {
                                for (orient in com.example.optical_designer.model.LensOrientation.entries) {
                                    DropdownMenuItem(
                                        text = { Text(orient.displayName) },
                                        onClick = {
                                            orientation = orient
                                            orientDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "AR Coating",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = isCoated,
                                onCheckedChange = { isCoated = it }
                            )
                        }
                    }

                    if (isCoated) {
                        item {
                            ScientificNumberTextField(
                                value = customTrans,
                                onValueChange = { customTrans = it },
                                label = "Optical Transmission (%)",
                                minConstraint = 0.0,
                                maxConstraint = 100.0,
                                allowNegative = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    ScientificNumberTextField(
                        value = aperture,
                        onValueChange = { aperture = it },
                        label = if (element.type == OpticalElementType.DETECTOR) "Circular Screen Aperture Ø (mm)" else "Clear Aperture Ø (mm)",
                        minConstraint = 0.001,
                        allowNegative = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    element.copy(
                        name = name,
                        positionZMm = posZ,
                        focalLengthMm = focalLength,
                        clearApertureMm = aperture,
                        material = material,
                        isCoated = isCoated,
                        customTransmissionPct = customTrans,
                        orientation = orientation
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
        OpticalElementType.PLANO_CONVEX -> OpticalElement(id, "Plano-Convex Lens", type, 60.0, 30.0, 50.0)
        OpticalElementType.BICONVEX -> OpticalElement(id, "Biconvex Lens", type, 60.0, 30.0, 50.0)
        OpticalElementType.PARABOLIC_MIRROR -> OpticalElement(id, "Parabolic Mirror", type, 50.0, 40.0, 50.0)
        OpticalElementType.APERTURE_STOP -> OpticalElement(id, "Iris Aperture", type, 40.0, 15.0, 0.0)
        OpticalElementType.DETECTOR -> OpticalElement(id, "Detector Screen", type, 180.0, 50.0, 0.0)
    }
}
