package com.example.optical_designer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.optical_designer.engine.AnalyticalOpticsResult
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.Wavelength
import com.example.optical_designer.util.NumberFormatUtil
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Composable
fun RayTraceView(
    source: LightSource,
    elements: List<OpticalElement>,
    opticsResult: AnalyticalOpticsResult,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(2.5f) } // Pixels per mm
    var offsetX by remember { mutableStateOf(80f) }
    var offsetY by remember { mutableStateOf(0f) }

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
                        value = "${NumberFormatUtil.formatToDisplay(detRes.opticalEfficiencyPct, 1)}%",
                        color = if (detRes.opticalEfficiencyPct > 50) Color(0xFF2E7D32) else Color(0xFFD84315),
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        label = "Encircled Power",
                        value = "${NumberFormatUtil.formatToDisplay(detRes.encircledPowerPct, 1)}%",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    MetricItem(
                        label = "Efficiency",
                        value = "${NumberFormatUtil.formatToDisplay(opticsResult.opticalEfficiencyPct, 1)}%",
                        color = if (opticsResult.opticalEfficiencyPct > 70) Color(0xFF2E7D32) else Color(0xFFD84315),
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        label = "Divergence",
                        value = "${NumberFormatUtil.formatToDisplay(opticsResult.outputDivergenceDeg, 1)}°",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        label = "Spot Size",
                        value = NumberFormatUtil.formatDistanceUnit(opticsResult.spotDiameterAtDetectorMm),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Canvas Container with Zoom Controls ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(canvasBg)
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
                for (seg in opticsResult.beamSegments) {
                    val x1 = offsetX + seg.startZ.toFloat() * scale
                    val x2 = offsetX + seg.endZ.toFloat() * scale
                    val yTop1 = centerY - seg.startTopY.toFloat() * scale
                    val yBottom1 = centerY - seg.startBottomY.toFloat() * scale
                    val yTop2 = centerY - seg.endTopY.toFloat() * scale
                    val yBottom2 = centerY - seg.endBottomY.toFloat() * scale

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
                    drawOpticalElement(elem, offsetX, centerY, scale, isDarkTheme)
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
        }
    }
}

private fun DrawScope.drawOpticalElement(
    elem: OpticalElement,
    offsetX: Float,
    centerY: Float,
    scale: Float,
    isDarkTheme: Boolean = true
) {
    val zX = offsetX + elem.positionZMm.toFloat() * scale
    val apertureHalf = (elem.clearApertureMm.toFloat() / 2f) * scale
    val thickness = (elem.thicknessMm.toFloat() * scale).coerceAtLeast(6f)

    val glassFillColor = if (isDarkTheme) Color(0x5581D4FA) else Color(0x330288D1)
    val glassStrokeColor = if (isDarkTheme) Color(0xFF4FC3F7) else Color(0xFF0288D1)

    when (elem.type) {
        OpticalElementType.PLANO_CONVEX -> {
            val path = Path()
            val leftFlatZ = zX - thickness / 2f
            val rightApexZ = zX + thickness / 2f
            val edgeZ = (leftFlatZ + 2f).coerceAtMost(rightApexZ - 1f)

            // Flat front surface facing divergent beam (left), convex back surface facing right
            path.moveTo(leftFlatZ, centerY - apertureHalf)
            path.lineTo(leftFlatZ, centerY + apertureHalf)
            path.lineTo(edgeZ, centerY + apertureHalf)
            path.quadraticTo(
                rightApexZ, centerY,
                edgeZ, centerY - apertureHalf
            )
            path.close()

            drawPath(path = path, color = glassFillColor)
            drawPath(path = path, color = glassStrokeColor, style = Stroke(width = 2f))
        }

        OpticalElementType.BICONVEX -> {
            val path = Path()
            val leftApexZ = zX - thickness / 2f
            val rightApexZ = zX + thickness / 2f

            path.moveTo(zX, centerY - apertureHalf)
            path.quadraticTo(
                leftApexZ, centerY,
                zX, centerY + apertureHalf
            )
            path.quadraticTo(
                rightApexZ, centerY,
                zX, centerY - apertureHalf
            )
            path.close()

            drawPath(path = path, color = glassFillColor)
            drawPath(path = path, color = glassStrokeColor, style = Stroke(width = 2f))
        }

        OpticalElementType.ACHROMATIC_DOUBLET -> {
            val leftZ = zX - thickness / 2f
            val rightZ = zX + thickness / 2f
            val midZ = zX

            val crownPath = Path().apply {
                moveTo(leftZ, centerY - apertureHalf)
                lineTo(leftZ, centerY + apertureHalf)
                lineTo(midZ, centerY + apertureHalf)
                quadraticTo(midZ + thickness / 4f, centerY, midZ, centerY - apertureHalf)
                close()
            }

            val flintPath = Path().apply {
                moveTo(midZ, centerY - apertureHalf)
                quadraticTo(midZ + thickness / 4f, centerY, midZ, centerY + apertureHalf)
                lineTo(rightZ, centerY + apertureHalf)
                quadraticTo(rightZ - thickness / 3f, centerY, rightZ, centerY - apertureHalf)
                close()
            }

            val crownFill = if (isDarkTheme) Color(0x6681D4FA) else Color(0x330288D1)
            val crownStroke = if (isDarkTheme) Color(0xFF4FC3F7) else Color(0xFF0288D1)
            val flintFill = if (isDarkTheme) Color(0x66B388FF) else Color(0x33673AB7)
            val flintStroke = if (isDarkTheme) Color(0xFFD1C4E9) else Color(0xFF673AB7)

            drawPath(path = crownPath, color = crownFill)
            drawPath(path = crownPath, color = crownStroke, style = Stroke(width = 1.5f))

            drawPath(path = flintPath, color = flintFill)
            drawPath(path = flintPath, color = flintStroke, style = Stroke(width = 1.5f))
        }

        OpticalElementType.PARABOLIC_MIRROR -> {
            val path = Path()
            val leftZ = zX - thickness / 2f

            path.moveTo(zX, centerY - apertureHalf)
            path.quadraticTo(
                leftZ, centerY,
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
