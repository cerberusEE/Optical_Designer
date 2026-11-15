package com.example.optical_designer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import kotlin.math.max
import kotlin.math.round

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricItem(
                    label = "Efficiency",
                    value = "${opticsResult.opticalEfficiencyPct.format(1)}%",
                    color = if (opticsResult.opticalEfficiencyPct > 70) Color(0xFF2E7D32) else Color(0xFFD84315),
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "Divergence",
                    value = "${opticsResult.outputDivergenceDeg.format(1)}°",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "Spot Size",
                    value = "${opticsResult.spotDiameterAtDetectorMm.format(1)} mm",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Canvas Container with Zoom Controls ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0F172A)) // Dark slate background for optics view
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 15f)
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
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(0f, centerY),
                    end = Offset(canvasWidth, centerY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Scale grid markers (every 50mm)
                for (zMm in 0..400 step 50) {
                    val x = offsetX + zMm * scale
                    if (x in 0f..canvasWidth) {
                        drawLine(
                            color = Color.DarkGray.copy(alpha = 0.4f),
                            start = Offset(x, centerY - 8f),
                            end = Offset(x, centerY + 8f),
                            strokeWidth = 1f
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${zMm}mm",
                            style = TextStyle(color = Color.LightGray, fontSize = 9.sp),
                            topLeft = Offset(x - 12f, centerY + 10f)
                        )
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
                        color = seg.color.copy(alpha = 0.30f)
                    )

                    // Sharp outer beam envelope boundary lines
                    drawLine(
                        color = seg.color.copy(alpha = 0.90f),
                        start = Offset(x1, yTop1),
                        end = Offset(x2, yTop2),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = seg.color.copy(alpha = 0.90f),
                        start = Offset(x1, yBottom1),
                        end = Offset(x2, yBottom2),
                        strokeWidth = 2f
                    )
                }

                // 3. Render Optical Elements
                for (elem in elements.filter { it.enabled }) {
                    drawOpticalElement(elem, offsetX, centerY, scale)
                }

                // 4. Render Light Source Symbol
                val sourceX = offsetX + source.positionZMm.toFloat() * scale
                val emitHalfH = (source.emitterHeightMm.toFloat() / 2f) * scale
                val emitH = max(16f, emitHalfH * 2f)
                drawRect(
                    color = Color(0xFFFFB74D),
                    topLeft = Offset(sourceX - 6f, centerY - max(8f, emitHalfH)),
                    size = Size(12f, emitH)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = source.name,
                    style = TextStyle(color = Color(0xFFFFB74D), fontSize = 11.sp),
                    topLeft = Offset(sourceX - 25f, centerY - max(8f, emitHalfH) - 20f)
                )
            }

            // --- Floating Canvas Controls ---
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { scale = (scale * 1.25f).coerceAtMost(15f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("+", color = Color.White, fontSize = 18.sp)
                    }
                    Button(
                        onClick = { scale = (scale / 1.25f).coerceAtLeast(0.5f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("-", color = Color.White, fontSize = 18.sp)
                    }
                    Button(
                        onClick = {
                            scale = 2.5f
                            offsetX = 80f
                            offsetY = 0f
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Reset", color = Color.White, fontSize = 12.sp)
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
    scale: Float
) {
    val zX = offsetX + elem.positionZMm.toFloat() * scale
    val apertureHalf = (elem.clearApertureMm.toFloat() / 2f) * scale
    val thickness = (elem.thicknessMm.toFloat() * scale).coerceAtLeast(6f)

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

            drawPath(path = path, color = Color(0x5581D4FA))
            drawPath(path = path, color = Color(0xFF4FC3F7), style = Stroke(width = 2f))
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

            drawPath(path = path, color = Color(0x5581D4FA))
            drawPath(path = path, color = Color(0xFF4FC3F7), style = Stroke(width = 2f))
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

            drawPath(path = crownPath, color = Color(0x6681D4FA))
            drawPath(path = crownPath, color = Color(0xFF4FC3F7), style = Stroke(width = 1.5f))

            drawPath(path = flintPath, color = Color(0x66B388FF))
            drawPath(path = flintPath, color = Color(0xFFD1C4E9), style = Stroke(width = 1.5f))
        }

        OpticalElementType.PARABOLIC_MIRROR -> {
            val path = Path()
            val leftZ = zX - thickness / 2f

            path.moveTo(zX, centerY - apertureHalf)
            path.quadraticTo(
                leftZ, centerY,
                zX, centerY + apertureHalf
            )

            drawPath(
                path = path,
                color = Color(0xFFE0E0E0),
                style = Stroke(width = 4f)
            )
        }

        OpticalElementType.APERTURE_STOP -> {
            drawLine(
                color = Color(0xFFFF5252),
                start = Offset(zX, centerY - apertureHalf - 40f),
                end = Offset(zX, centerY - apertureHalf),
                strokeWidth = 4f
            )
            drawLine(
                color = Color(0xFFFF5252),
                start = Offset(zX, centerY + apertureHalf),
                end = Offset(zX, centerY + apertureHalf + 40f),
                strokeWidth = 4f
            )
        }

        OpticalElementType.DETECTOR -> {
            drawLine(
                color = Color(0xFFFFD54F),
                start = Offset(zX, centerY - apertureHalf),
                end = Offset(zX, centerY + apertureHalf),
                strokeWidth = 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
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

private fun Double.format(digits: Int): String {
    val factor = when (digits) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    return (round(this * factor) / factor).toString()
}
