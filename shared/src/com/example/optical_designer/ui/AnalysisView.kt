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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.optical_designer.engine.AnalyticalOpticsEngine
import com.example.optical_designer.engine.AnalyticalOpticsResult
import com.example.optical_designer.engine.DetectorAnalysisResult
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.storage.ProjectRepository
import com.example.optical_designer.util.NumberFormatUtil

@Composable
fun AnalysisView(
    source: LightSource,
    elements: List<OpticalElement>,
    result: AnalyticalOpticsResult,
    modifier: Modifier = Modifier
) {
    val detResult = result.detectorResult
    val detectorZ = detResult?.detectorZMm
        ?: elements.firstOrNull { it.type == OpticalElementType.DETECTOR }?.positionZMm
        ?: (elements.maxOfOrNull { it.positionZMm } ?: 180.0)

    val defaultRangeMeters = detectorZ / 1000.0

    // Preserved across tab switches and app restarts (stored in persistent platform storage, not project data)
    var targetRangeZMeters by remember {
        mutableStateOf(ProjectRepository.getSavedEvaluationRangeMeters() ?: defaultRangeMeters)
    }

    fun updateTargetRange(newRangeMeters: Double) {
        targetRangeZMeters = newRangeMeters
        if ((!newRangeMeters.isNaN()) && (!newRangeMeters.isInfinite()) && (newRangeMeters >= 0.0)) {
            ProjectRepository.saveEvaluationRangeMeters(newRangeMeters)
        }
    }

    val irradianceAtRange = remember(targetRangeZMeters, result) {
        AnalyticalOpticsEngine.calculateIrradianceAtRange(result, targetRangeZMeters * 1000.0)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Detector Screen / Aperture Analysis Card (Shown ONLY if a Detector Screen is present) ---
        if (detResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Detector Screen & Aperture Analysis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Integrated Power on Detector",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = NumberFormatUtil.formatPower(detResult.landedPowerW),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            Column {
                                Text(
                                    text = "Detector Efficiency",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${NumberFormatUtil.formatToDisplay(detResult.opticalEfficiencyPct)}%",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (detResult.opticalEfficiencyPct > 50) Color(0xFF2E7D32) else Color(0xFFD84315)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (detResult.opticalEfficiencyPct / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = if (detResult.opticalEfficiencyPct > 50) Color(0xFF2E7D32) else Color(0xFFD84315)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AnalysisRow(
                                label = "Average Irradiance",
                                value = NumberFormatUtil.formatIrradiance(detResult.integratedIrradianceWm2),
                                subtitle = "Landed power density over detector area",
                                modifier = Modifier.weight(1f)
                            )
                            AnalysisRow(
                                label = "Encircled Power",
                                value = "${NumberFormatUtil.formatToDisplay(detResult.encircledPowerPct)}%",
                                subtitle = "Captured fraction of arriving beam",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AnalysisRow(
                                label = "Screen Circular Aperture Ø",
                                value = NumberFormatUtil.formatDistanceUnit(detResult.detectorApertureMm),
                                subtitle = if (detResult.spilloverLossPct > 0.1) {
                                    "Spillover loss: ${NumberFormatUtil.formatToDisplay(detResult.spilloverLossPct)}%"
                                } else {
                                    "Aperture fill: ${NumberFormatUtil.formatToDisplay(detResult.apertureFillPct)}%"
                                },
                                valueColor = if (detResult.spilloverLossPct > 10.0) Color(0xFFD84315) else Color.Unspecified,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = getDetectorGuidance(detResult),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        } else {
            // --- Overall System Optical Efficiency Card (Shown when NO Detector Screen is present) ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Net System Optical Efficiency",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${NumberFormatUtil.formatToDisplay(result.opticalEfficiencyPct)}%",
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
        }

        // --- Beam Performance Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Beam Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "Source Divergence",
                            value = "${NumberFormatUtil.formatToDisplay(source.divergenceYDeg)}°",
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "Output Beam Divergence",
                            value = "${NumberFormatUtil.formatToDisplay(result.outputDivergenceDeg)}°",
                            subtitle = "FWHM 50% intensity angle",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    detResult?.bestFocusZMm?.let { bestZ ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AnalysisRow(
                                label = "Paraxial Focal Z",
                                value = "${NumberFormatUtil.formatToDisplay(bestZ / 1000.0, 3)} m",
                                subtitle = "Optimal focus distance",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // --- System Radiant Intensity & Target Range Analysis Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Radiant Intensity & Range Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "System Radiant Intensity",
                            value = NumberFormatUtil.formatRadiantIntensity(result.radiantIntensityWsr),
                            subtitle = "Directional flux leaving system",
                            valueColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "Net Output Power",
                            value = NumberFormatUtil.formatPower(result.outputPowerW),
                            subtitle = "Source flux: ${NumberFormatUtil.formatPower(result.sourcePowerW)}",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ScientificNumberTextField(
                        value = targetRangeZMeters,
                        onValueChange = { updateTargetRange(it) },
                        label = "Evaluation Range Z (m)",
                        allowNegative = false,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "Irradiance at Range",
                            value = NumberFormatUtil.formatIrradiance(irradianceAtRange.irradianceWm2),
                            subtitle = "Power density at Z = ${NumberFormatUtil.formatToDisplay(targetRangeZMeters, 3)} m",
                            modifier = Modifier.weight(1.5f)
                        )
                        AnalysisRow(
                            label = "EE90 Spot Diameter",
                            value = NumberFormatUtil.formatDistanceUnit(irradianceAtRange.spotDiameterMm),
                            subtitle = "Area: ${NumberFormatUtil.formatToDisplay(irradianceAtRange.spotAreaM2, 3)} m²",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalysisRow(
                            label = "EE50 Spot Diameter",
                            value = NumberFormatUtil.formatDistanceUnit(irradianceAtRange.ee50DiameterMm),
                            subtitle = "50% power encircled radius",
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "EE80 Spot Diameter",
                            value = NumberFormatUtil.formatDistanceUnit(irradianceAtRange.ee80DiameterMm),
                            subtitle = "80% power encircled radius",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // --- Optical Efficiency & Loss Breakdown Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Efficiency & Loss Breakdown",
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
                            value = "${NumberFormatUtil.formatToDisplay(result.geometricCollectionPct)}%",
                            subtitle = "Captured within clear apertures",
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "Aperture Crop Loss",
                            value = "${NumberFormatUtil.formatToDisplay(result.croppedLossPct)}%",
                            subtitle = "Lost to lens aperture boundary",
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
                            value = "${NumberFormatUtil.formatToDisplay(result.fresnelTransmissionPct)}%",
                            subtitle = "Transmitted through optics",
                            modifier = Modifier.weight(1f)
                        )
                        AnalysisRow(
                            label = "Surface Reflection Loss",
                            value = "${NumberFormatUtil.formatToDisplay(result.reflectionLossPct)}%",
                            subtitle = "Glass Fresnel surface reflection",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = getEfficiencyGuidance(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Optical Prescription Summary ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Optical System Prescription",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Source: ${source.name} (${NumberFormatUtil.formatToDisplay(source.cwlNm)}nm, ${NumberFormatUtil.formatPower(source.opticalPowerW)}, ${NumberFormatUtil.formatToDisplay(source.divergenceYDeg)}° div)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    for ((_, name, type, positionZMm, clearApertureMm, focalLengthMm) in elements.filter { it.enabled }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "@ Z=${NumberFormatUtil.formatToDisplay(positionZMm / 1000.0, 3)}m: $name",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1.5f)
                            )
                            Text(
                                text = if (type == OpticalElementType.DETECTOR) {
                                    "Detector Screen | Ø${NumberFormatUtil.formatToDisplay(
                                        clearApertureMm
                                    )}mm"
                                } else {
                                    "f=${NumberFormatUtil.formatToDisplay(focalLengthMm)}mm | Ø${NumberFormatUtil.formatToDisplay(
                                        clearApertureMm
                                    )}mm"
                                },
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

private fun getDetectorGuidance(det: DetectorAnalysisResult): String {
    return when {
        det.spilloverLossPct > 20.0 ->
            "Major spillover loss (${NumberFormatUtil.formatToDisplay(det.spilloverLossPct)}%)! Spot size (${NumberFormatUtil.formatDistanceUnit(det.spotDiameterMm)}) exceeds circular detector screen aperture (${NumberFormatUtil.formatDistanceUnit(det.detectorApertureMm)}). Move detector closer to focal plane or increase detector screen diameter."
        det.spilloverLossPct > 0.1 ->
            "Minor spillover loss (${NumberFormatUtil.formatToDisplay(det.spilloverLossPct)}%). Adjust detector position or aperture for 100% light collection."
        else ->
            "100% encircled power collection! All light reaching the detector plane is captured within the circular detector aperture (${NumberFormatUtil.formatToDisplay(det.apertureFillPct)}% aperture fill)."
    }
}

private fun getEfficiencyGuidance(result: AnalyticalOpticsResult): String {
    return when {
        result.croppedLossPct > 50.0 ->
            "Major clipping detected (${NumberFormatUtil.formatToDisplay(result.croppedLossPct)}% lost)! Increase lens clear aperture diameter or place lens closer to the source to collect more light."
        result.croppedLossPct > 10.0 ->
            "Moderate clipping detected (${NumberFormatUtil.formatToDisplay(result.croppedLossPct)}% lost). Increasing lens aperture will improve efficiency."
        else ->
            "Geometric collection is high. Remaining loss (${NumberFormatUtil.formatToDisplay(result.reflectionLossPct)}%) is due to glass surface reflections."
    }
}
