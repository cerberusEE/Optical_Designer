package com.example.optical_designer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.optical_designer.engine.AnalyticalOpticsResult
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import kotlin.math.round

@Composable
fun AnalysisView(
    source: LightSource,
    elements: List<OpticalElement>,
    result: AnalyticalOpticsResult,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Overall Optical Efficiency Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Net Optical Efficiency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${result.opticalEfficiencyPct.format(1)}%",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (result.opticalEfficiencyPct > 70) Color(0xFF2E7D32) else Color(0xFFD84315)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (result.opticalEfficiencyPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (result.opticalEfficiencyPct > 70) Color(0xFF2E7D32) else Color(0xFFD84315)
                    )
                }
            }
        }

        // --- Detailed Efficiency & Loss Breakdown Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Efficiency Loss Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "Geometric Collection",
                            value = "${result.geometricCollectionPct.format(1)}%",
                            subtitle = "Captured within clear aperture",
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "Cropped Aperture Loss",
                            value = "${result.croppedLossPct.format(1)}%",
                            subtitle = "Lost due to lens size limit",
                            valueColor = if (result.croppedLossPct > 20) Color(0xFFD84315) else Color.Unspecified,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "Glass Transmission",
                            value = "${result.fresnelTransmissionPct.format(1)}%",
                            subtitle = "Transmitted through optics",
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "Surface Reflection Loss",
                            value = "${result.reflectionLossPct.format(1)}%",
                            subtitle = "Uncoated glass Fresnel loss",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = getEfficiencyGuidance(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Beam & Angular Performance Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Beam & Divergence Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "Input Source Divergence",
                            value = "${source.divergenceYDeg.format(1)}°",
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "Output Beam Divergence",
                            value = "${result.outputDivergenceDeg.format(1)}°",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "Emitter Width/Height",
                            value = "${source.emitterWidthMm} × ${source.emitterHeightMm} mm",
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "Spot Size at Detector",
                            value = "${result.spotDiameterAtDetectorMm.format(1)} mm",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // --- Spherical Aberration Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Spherical Aberration Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "Longitudinal Spherical Aberration",
                            value = "${result.sphericalAberrationMm.format(2)} mm",
                            subtitle = "Marginal vs Paraxial focal shift"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = getAberrationGuidance(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- System Summary Table ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Optical Prescription Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Source: ${source.name} (${source.cwlNm.format(0)}nm, ${source.divergenceYDeg.format(0)}° div)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    for (elem in elements.filter { it.enabled }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "@ Z=${elem.positionZMm.format(0)}mm: ${elem.name}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1.5f)
                            )
                            Text(
                                text = "f=${elem.focalLengthMm.format(0)}mm | Ø${elem.clearApertureMm.format(0)}mm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisRow(
    label: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getEfficiencyGuidance(result: AnalyticalOpticsResult): String {
    return when {
        result.croppedLossPct > 50.0 ->
            "Major clipping detected (${result.croppedLossPct.format(1)}% lost)! Increase lens clear aperture diameter or place lens closer to the source to collect more light."
        result.croppedLossPct > 10.0 ->
            "Moderate clipping detected (${result.croppedLossPct.format(1)}% lost). Increasing lens aperture will improve efficiency."
        else ->
            "Geometric collection is high. Remaining loss (${result.reflectionLossPct.format(1)}%) is due to glass surface reflections."
    }
}

private fun getAberrationGuidance(result: AnalyticalOpticsResult): String {
    return when {
        result.sphericalAberrationMm > 2.0 ->
            "Significant spherical aberration detected (${result.sphericalAberrationMm.format(1)} mm shift). Consider reducing lens clear aperture / adding an aperture stop or using a doublet / parabolic mirror."
        else ->
            "System optical efficiency and beam profile are optimal."
    }
}

private fun Double.format(digits: Int): String {
    val factor = when (digits) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    return (round(this * factor) / factor).toString()
}
