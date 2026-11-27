package com.example.optical_designer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.optical_designer.engine.AnalyticalOpticsResult
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.Wavelength
import com.example.optical_designer.util.NumberFormatUtil
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Composable
fun RayTraceView(
    source: LightSource,
    elements: List<OpticalElement>,
    opticsResult: AnalyticalOpticsResult,
    onElementsChange: (List<OpticalElement>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(2.5f) } // Pixels per mm
    var offsetX by remember { mutableStateOf(80f) }
    var offsetY by remember { mutableStateOf(0f) }

    var selectedElementId by remember { mutableStateOf<String?>(null) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val selectedElement = elements.firstOrNull { it.id == selectedElementId }

    val currentSliderPosition by rememberUpdatedState(sliderPosition)
    val currentElements by rememberUpdatedState(elements)
    val currentOnElementsChange by rememberUpdatedState(onElementsChange)

    // Frame-precise continuous velocity nudge animation loop
    LaunchedEffect(selectedElementId, sliderPosition != 0f) {
        if (selectedElementId != null && sliderPosition != 0f) {
            var lastTimeNanos = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameTimeNanos ->
                    val dtSec = (frameTimeNanos - lastTimeNanos) / 1_000_000_000.0
                    lastTimeNanos = frameTimeNanos

                    val pos = currentSliderPosition
                    if (pos != 0f) {
                        val speedMmPerSec = pos.toDouble() * 10.0
                        val deltaZ = speedMmPerSec * dtSec

                        val elemList = currentElements
                        val currentElem = elemList.firstOrNull { it.id == selectedElementId }
                        if (currentElem != null && abs(deltaZ) > 1e-6) {
                            val newZ = max(0.0, currentElem.positionZMm + deltaZ)
                            val updated = elemList.map {
                                if (it.id == selectedElementId) it.copy(positionZMm = newZ) else it
                            }
                            currentOnElementsChange(updated)
                        }
                    }
                }
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val isDarkTheme = isSystemInDarkTheme()

    // Theme-aware colors
    val canvasBg = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val axisLineColor = if (isDarkTheme) Color.Gray.copy(alpha = 0.5f) else Color.DarkGray.copy(alpha = 0.6f)
    val tickLineColor = if (isDarkTheme) Color.DarkGray.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.5f)
    val tickTextColor = if (isDarkTheme) Color.LightGray else Color(0xFF1E293B)

    // Perceived wavelength color matching (CIE 1931 functions for visible light, light gray for non-visible UV/IR)
    val wavelengthColor = Wavelength(source.cwlNm).toColor(isDarkTheme)
    val beamFillColor = wavelengthColor.copy(alpha = if (isDarkTheme) 0.30f else 0.40f)
    val beamStrokeColor = wavelengthColor.copy(alpha = if (isDarkTheme) 0.90f else 1.00f)

    Column(modifier = modifier.fillMaxSize()) {
        // --- Metric Overlay Header Card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val detRes = opticsResult.detectorResult
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (detRes != null) {
                    MetricItem(
                        label = "Landed Power",
                        value = NumberFormatUtil.formatPower(detRes.landedPowerW),
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        label = "Detector Efficiency",
                        value = "${NumberFormatUtil.formatToDisplay(detRes.opticalEfficiencyPct)}%",
                        color = if (detRes.opticalEfficiencyPct > 50) Color(0xFF2E7D32) else Color(0xFFD84315),
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        label = "Encircled Power",
                        value = "${NumberFormatUtil.formatToDisplay(detRes.encircledPowerPct)}%",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    MetricItem(
                        label = "Efficiency",
                        value = "${NumberFormatUtil.formatToDisplay(opticsResult.opticalEfficiencyPct)}%",
                        color = if (opticsResult.opticalEfficiencyPct > 70) Color(0xFF2E7D32) else Color(0xFFD84315),
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        label = "Divergence",
                        value = "${NumberFormatUtil.formatToDisplay(opticsResult.outputDivergenceDeg)}°",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        label = "Spot Size (EE90)",
                        value = NumberFormatUtil.formatDistanceUnit(opticsResult.spotDiameterAtDetectorMm),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Canvas Container with Zoom & Tap Selection Gestures ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(canvasBg)
                .pointerInput(elements, scale, offsetX, offsetY) {
                    detectTapGestures { tapOffset ->
                        val canvasHeight = size.height
                        val centerY = canvasHeight / 2f + offsetY

                        var hitElem: OpticalElement? = null
                        for (elem in elements.filter { it.enabled }) {
                            val zX = offsetX + elem.positionZMm.toFloat() * scale
                            val apertureHalf = (elem.clearApertureMm.toFloat() / 2f) * scale
                            val thickness = (elem.thicknessMm.toFloat() * scale).coerceAtLeast(6f)

                            val leftZ = if (elem.type == OpticalElementType.PLANO_CONVEX) zX else zX - thickness / 2f
                            val rightZ = leftZ + thickness

                            val touchPadding = 20f
                            if (tapOffset.x in (leftZ - touchPadding)..(rightZ + touchPadding) &&
                                tapOffset.y in (centerY - apertureHalf - touchPadding)..(centerY + apertureHalf + touchPadding)
                            ) {
                                hitElem = elem
                                break
                            }
                        }

                        if (hitElem != null) {
                            selectedElementId = hitElem.id
                        } else {
                            selectedElementId = null
                            sliderPosition = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.0000001f, 1000f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerY = canvasHeight / 2f + offsetY

                // 1. Optical Axis
                drawLine(
                    color = axisLineColor,
                    start = Offset(0f, centerY),
                    end = Offset(canvasWidth, centerY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Scale grid markers (Dynamic grid calculation based on view range)
                val minVisibleZ = (0f - offsetX) / scale
                val maxVisibleZ = (canvasWidth - offsetX) / scale
                val visibleSpanZ = (maxVisibleZ - minVisibleZ).toDouble()

                if (visibleSpanZ > 0) {
                    val rawStep = visibleSpanZ / 8.0
                    val step = NumberFormatUtil.calculateNiceGridStep(rawStep)

                    val startTick = floor(minVisibleZ / step) * step
                    val endTick = ceil(maxVisibleZ / step) * step

                    var currentTick = startTick
                    var safetyCount = 0
                    while (currentTick <= endTick && safetyCount < 200) {
                        safetyCount++
                        val x = offsetX + currentTick.toFloat() * scale
                        if (x in 0f..canvasWidth) {
                            drawLine(
                                color = tickLineColor,
                                start = Offset(x, centerY - 8f),
                                end = Offset(x, centerY + 8f),
                                strokeWidth = 1f
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = NumberFormatUtil.formatDistanceUnit(currentTick),
                                style = TextStyle(color = tickTextColor, fontSize = 9.sp),
                                topLeft = Offset(x - 16f, centerY + 10f)
                            )
                        }
                        currentTick += step
                    }
                }

                // 2. Render Solid Semi-transparent Emitted Light Beam Surface
                for ((startZ, endZ, startTopY, startBottomY, endTopY, endBottomY) in opticsResult.beamSegments) {
                    val x1 = offsetX + startZ.toFloat() * scale
                    val x2 = offsetX + endZ.toFloat() * scale
                    val yTop1 = centerY - startTopY.toFloat() * scale
                    val yBottom1 = centerY - startBottomY.toFloat() * scale
                    val yTop2 = centerY - endTopY.toFloat() * scale
                    val yBottom2 = centerY - endBottomY.toFloat() * scale

                    val beamPath = Path().apply {
                        moveTo(x1, yTop1)
                        lineTo(x2, yTop2)
                        lineTo(x2, yBottom2)
                        lineTo(x1, yBottom1)
                        close()
                    }

                    // Semi-transparent light beam body fill
                    drawPath(
                        path = beamPath,
                        color = beamFillColor
                    )

                    // Sharp outer beam envelope boundary lines
                    drawLine(
                        color = beamStrokeColor,
                        start = Offset(x1, yTop1),
                        end = Offset(x2, yTop2),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = beamStrokeColor,
                        start = Offset(x1, yBottom1),
                        end = Offset(x2, yBottom2),
                        strokeWidth = 2f
                    )
                }

                // 3. Render Optical Elements
                for (elem in elements.filter { it.enabled }) {
                    val isSelected = elem.id == selectedElementId
                    drawOpticalElement(elem, offsetX, centerY, scale, isDarkTheme, isSelected)
                }

                // 4. Render Light Source Symbol
                val sourceX = offsetX + source.positionZMm.toFloat() * scale
                val emitHalfH = (source.emitterHeightMm.toFloat() / 2f) * scale
                val emitH = max(16f, emitHalfH * 2f)
                val sourceSymbolColor = if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE65100)

                drawRect(
                    color = sourceSymbolColor,
                    topLeft = Offset(sourceX - 6f, centerY - max(8f, emitHalfH)),
                    size = Size(12f, emitH)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = source.name,
                    style = TextStyle(color = sourceSymbolColor, fontSize = 11.sp),
                    topLeft = Offset(sourceX - 25f, centerY - max(8f, emitHalfH) - 20f)
                )
            }

            // --- Top-Left Quick Element Selection Chips Overlay ---
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Optics:",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                )
                for ((id, name, _, positionZMm) in elements.filter { it.enabled }) {
                    val isSel = id == selectedElementId
                    FilterChip(
                        selected = isSel,
                        onClick = {
                            selectedElementId = if (isSel) null else id
                            sliderPosition = 0f
                        },
                        label = {
                            Text(
                                text = "$name (${NumberFormatUtil.formatToDisplay(positionZMm)}mm)",
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }

            // --- Floating Canvas Controls ---
            val controlCardBg = if (isDarkTheme) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.90f)
            val controlBtnTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)

            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = controlCardBg
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { scale = (scale * 1.5f).coerceAtMost(1000f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("+", color = controlBtnTextColor, fontSize = 18.sp)
                    }
                    Button(
                        onClick = { scale = (scale / 1.5f).coerceAtLeast(0.0000001f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("-", color = controlBtnTextColor, fontSize = 18.sp)
                    }
                    Button(
                        onClick = {
                            val maxZ = max(300.0, max(source.positionZMm, elements.maxOfOrNull { it.positionZMm } ?: 200.0))
                            scale = if (maxZ > 1000) (800f / maxZ.toFloat()).coerceIn(0.000001f, 10f) else 2.5f
                            offsetX = 80f
                            offsetY = 0f
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Reset", color = controlBtnTextColor, fontSize = 12.sp)
                    }
                }
            }

            // --- Bottom Velocity Nudge Control Panel (Shown when an optic is selected) ---
            selectedElement?.let { elem ->
                val cardBg = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                        .fillMaxWidth(0.95f),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // Card Header: Element Name and Position
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (elem.type != OpticalElementType.DETECTOR && elem.type != OpticalElementType.APERTURE_STOP) {
                                    "${elem.name} (f=${NumberFormatUtil.formatToDisplay(elem.focalLengthMm)}mm) • Z = ${NumberFormatUtil.formatToDisplay(elem.positionZMm, 2)} mm"
                                } else {
                                    "${elem.name} • Z = ${NumberFormatUtil.formatToDisplay(elem.positionZMm, 2)} mm"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Velocity Slider: Slide left (negative velocity) or right (positive velocity). Snaps to 0 when released.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "◀ Left",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )

                            Slider(
                                value = sliderPosition,
                                onValueChange = { sliderPosition = it },
                                onValueChangeFinished = { sliderPosition = 0f },
                                valueRange = -1f..1f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Text(
                                text = "Right ▶",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        // Velocity status feedback label (-10 mm/s to +10 mm/s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "-10 mm/s",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (sliderPosition != 0f) {
                                    "Velocity: ${NumberFormatUtil.formatToDisplay(sliderPosition * 10.0, 1)} mm/s"
                                } else {
                                    "Hold slider left/right to move • Snap 0 mm/s (Stopped)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sliderPosition != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+10 mm/s",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Fine Nudge Step Buttons (-5mm, -1mm, -0.1mm, +0.1mm, +1mm, +5mm)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val stepDeltas = listOf(-5.0, -1.0, -0.1, 0.1, 1.0, 5.0)
                            for (delta in stepDeltas) {
                                OutlinedButton(
                                    onClick = {
                                        val newZ = max(0.0, elem.positionZMm + delta)
                                        val updated = elements.map {
                                            if (it.id == elem.id) it.copy(positionZMm = newZ) else it
                                        }
                                        onElementsChange(updated)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = if (delta > 0) "+${NumberFormatUtil.formatToDisplay(delta)}mm" else "${NumberFormatUtil.formatToDisplay(delta)}mm",
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawOpticalElement(
    elem: OpticalElement,
    offsetX: Float,
    centerY: Float,
    scale: Float,
    isDarkTheme: Boolean = true,
    isSelected: Boolean = false
) {
    val zX = offsetX + elem.positionZMm.toFloat() * scale
    val apertureHalf = (elem.clearApertureMm.toFloat() / 2f) * scale
    val thickness = (elem.thicknessMm.toFloat() * scale).coerceAtLeast(6f)

    if (isSelected) {
        val pad = 6f
        val leftBound = if (elem.type == OpticalElementType.PLANO_CONVEX) zX - pad else zX - thickness / 2f - pad
        val rightBound = if (elem.type == OpticalElementType.PLANO_CONVEX) zX + thickness + pad else zX + thickness / 2f + pad
        val topBound = centerY - apertureHalf - pad
        val bottomBound = centerY + apertureHalf + pad

        val highlightColor = Color(0xFFFFD54F) // Bright yellow selection glow
        drawRect(
            color = highlightColor,
            topLeft = Offset(leftBound, topBound),
            size = Size(rightBound - leftBound, bottomBound - topBound),
            style = Stroke(
                width = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
        )
    }

    val glassFillColor = if (isDarkTheme) Color(0x5581D4FA) else Color(0x330288D1)
    val glassStrokeColor = if (isDarkTheme) Color(0xFF4FC3F7) else Color(0xFF0288D1)

    when (elem.type) {
        OpticalElementType.PLANO_CONVEX -> {
            val path = Path()
            if (elem.orientation == com.example.optical_designer.model.LensOrientation.CURVED_FACE_LEFT) {
                val targetApexZ = zX
                val edgeZ = (zX + thickness - 2f).coerceAtLeast(targetApexZ + 1f)
                val controlZ = 2f * targetApexZ - edgeZ

                // Curved face facing left at zX, flat back surface facing right at zX + thickness
                path.moveTo(zX + thickness, centerY - apertureHalf)
                path.lineTo(zX + thickness, centerY + apertureHalf)
                path.lineTo(edgeZ, centerY + apertureHalf)
                path.quadraticTo(
                    controlZ, centerY,
                    edgeZ, centerY - apertureHalf
                )
                path.close()
            } else {
                val targetApexZ = zX + thickness
                val edgeZ = (zX + 2f).coerceAtMost(targetApexZ - 1f)
                val controlZ = 2f * targetApexZ - edgeZ

                // Flat face facing left at zX, convex back surface facing right at zX + thickness
                path.moveTo(zX, centerY - apertureHalf)
                path.lineTo(zX, centerY + apertureHalf)
                path.lineTo(edgeZ, centerY + apertureHalf)
                path.quadraticTo(
                    controlZ, centerY,
                    edgeZ, centerY - apertureHalf
                )
                path.close()
            }

            drawPath(path = path, color = glassFillColor)
            drawPath(path = path, color = glassStrokeColor, style = Stroke(width = 2f))
        }

        OpticalElementType.BICONVEX -> {
            val path = Path()
            val leftControlZ = zX - thickness
            val rightControlZ = zX + thickness

            path.moveTo(zX, centerY - apertureHalf)
            path.quadraticTo(
                leftControlZ, centerY,
                zX, centerY + apertureHalf
            )
            path.quadraticTo(
                rightControlZ, centerY,
                zX, centerY - apertureHalf
            )
            path.close()

            drawPath(path = path, color = glassFillColor)
            drawPath(path = path, color = glassStrokeColor, style = Stroke(width = 2f))
        }

        OpticalElementType.PARABOLIC_MIRROR -> {
            val path = Path()
            val controlZ = zX - thickness

            path.moveTo(zX, centerY - apertureHalf)
            path.quadraticTo(
                controlZ, centerY,
                zX, centerY + apertureHalf
            )

            val mirrorColor = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF37474F)
            drawPath(
                path = path,
                color = mirrorColor,
                style = Stroke(width = 4f)
            )
        }

        OpticalElementType.APERTURE_STOP -> {
            val stopColor = if (isDarkTheme) Color(0xFFFF5252) else Color(0xFFD32F2F)
            drawLine(
                color = stopColor,
                start = Offset(zX, centerY - apertureHalf - 40f),
                end = Offset(zX, centerY - apertureHalf),
                strokeWidth = 4f
            )
            drawLine(
                color = stopColor,
                start = Offset(zX, centerY + apertureHalf),
                end = Offset(zX, centerY + apertureHalf + 40f),
                strokeWidth = 4f
            )
        }

        OpticalElementType.DETECTOR -> {
            val mountLen = 25f
            val mountColor = if (isDarkTheme) Color(0xFFFFB300) else Color(0xFFE65100)
            val fillCol = if (isDarkTheme) Color(0x33FFC107) else Color(0x44FB8C00)
            val borderCol = if (isDarkTheme) Color(0xFFFFD54F) else Color(0xFFF57F17)
            val crosshairCol = if (isDarkTheme) Color(0xFFFFECB3) else Color(0xFFE65100)

            // Screen outer mechanical mount / frame
            drawLine(
                color = mountColor,
                start = Offset(zX, centerY - apertureHalf - mountLen),
                end = Offset(zX, centerY - apertureHalf),
                strokeWidth = 5f
            )
            drawLine(
                color = mountColor,
                start = Offset(zX, centerY + apertureHalf),
                end = Offset(zX, centerY + apertureHalf + mountLen),
                strokeWidth = 5f
            )

            // Active sensor screen surface fill
            drawRect(
                color = fillCol,
                topLeft = Offset(zX - 3f, centerY - apertureHalf),
                size = Size(6f, apertureHalf * 2f)
            )

            // Active sensor screen border plane line
            drawLine(
                color = borderCol,
                start = Offset(zX, centerY - apertureHalf),
                end = Offset(zX, centerY + apertureHalf),
                strokeWidth = 4f
            )

            // Crosshair tick on optical axis
            drawLine(
                color = crosshairCol,
                start = Offset(zX - 6f, centerY),
                end = Offset(zX + 6f, centerY),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}
