package com.example.optical_designer.storage

import com.example.optical_designer.engine.OpticalPresets
import com.example.optical_designer.model.GlassMaterial
import com.example.optical_designer.model.LightSource
import com.example.optical_designer.model.OpticalElement
import com.example.optical_designer.model.OpticalElementType
import com.example.optical_designer.model.OpticalProject
import com.example.optical_designer.model.SourceType
import com.example.optical_designer.util.currentTimeMillis
import com.example.optical_designer.util.generateUuid

sealed class JsonValue {
    data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue() {
        fun getString(key: String, default: String = ""): String {
            return (fields[key] as? JsonString)?.value ?: default
        }

        fun getDouble(key: String, default: Double = 0.0): Double {
            return (fields[key] as? JsonNumber)?.value ?: default
        }

        fun getNullableDouble(key: String): Double? {
            val v = fields[key]
            if (v is JsonNumber) return v.value
            return null
        }

        fun getLong(key: String, default: Long = 0L): Long {
            val v = fields[key]
            if (v is JsonNumber) return v.value.toLong()
            return default
        }

        fun getBoolean(key: String, default: Boolean = false): Boolean {
            return (fields[key] as? JsonBoolean)?.value ?: default
        }

        fun getObject(key: String): JsonObject? {
            return fields[key] as? JsonObject
        }

        fun getArray(key: String): JsonArray? {
            return fields[key] as? JsonArray
        }
    }

    data class JsonArray(val items: List<JsonValue>) : JsonValue()
    data class JsonString(val value: String) : JsonValue()
    data class JsonNumber(val value: Double) : JsonValue()
    data class JsonBoolean(val value: Boolean) : JsonValue()
    object JsonNull : JsonValue()

    fun toJsonString(): String {
        return when (this) {
            is JsonObject -> {
                val entries = fields.entries.joinToString(",") { (k, v) ->
                    "\"${escape(k)}\":${v.toJsonString()}"
                }
                "{$entries}"
            }
            is JsonArray -> {
                val elements = items.joinToString(",") { it.toJsonString() }
                "[$elements]"
            }
            is JsonString -> "\"${escape(value)}\""
            is JsonNumber -> {
                val l = value.toLong()
                if (l.toDouble() == value) l.toString() else value.toString()
            }
            is JsonBoolean -> value.toString()
            is JsonNull -> "null"
        }
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

class SimpleJsonParser(private val src: String) {
    private var pos = 0

    fun parseValue(): JsonValue {
        skipWhitespace()
        if (pos >= src.length) return JsonValue.JsonNull
        return when (val c = src[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.JsonString(parseString())
            't', 'f' -> JsonValue.JsonBoolean(parseBoolean())
            'n' -> { parseNull(); JsonValue.JsonNull }
            else -> if (c == '-' || c.isDigit()) JsonValue.JsonNumber(parseNumber()) else JsonValue.JsonNull
        }
    }

    private fun skipWhitespace() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun parseObject(): JsonValue.JsonObject {
        pos++ // skip '{'
        skipWhitespace()
        val fields = mutableMapOf<String, JsonValue>()
        if (pos < src.length && src[pos] == '}') {
            pos++
            return JsonValue.JsonObject(fields)
        }
        while (pos < src.length) {
            skipWhitespace()
            if (src[pos] != '"') break
            val key = parseString()
            skipWhitespace()
            if (pos < src.length && src[pos] == ':') pos++
            val value = parseValue()
            fields[key] = value
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') {
                pos++
            } else if (pos < src.length && src[pos] == '}') {
                pos++
                break
            }
        }
        return JsonValue.JsonObject(fields)
    }

    private fun parseArray(): JsonValue.JsonArray {
        pos++ // skip '['
        skipWhitespace()
        val items = mutableListOf<JsonValue>()
        if (pos < src.length && src[pos] == ']') {
            pos++
            return JsonValue.JsonArray(items)
        }
        while (pos < src.length) {
            val item = parseValue()
            items.add(item)
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') {
                pos++
            } else if (pos < src.length && src[pos] == ']') {
                pos++
                break
            }
        }
        return JsonValue.JsonArray(items)
    }

    private fun parseString(): String {
        pos++ // skip opening quote
        val sb = StringBuilder()
        while (pos < src.length) {
            val c = src[pos++]
            if (c == '"') break
            if (c == '\\' && pos < src.length) {
                when (val esc = src[pos++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(esc)
                }
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun parseBoolean(): Boolean {
        return if (src.startsWith("true", pos)) {
            pos += 4
            true
        } else if (src.startsWith("false", pos)) {
            pos += 5
            false
        } else {
            pos++
            false
        }
    }

    private fun parseNull() {
        if (src.startsWith("null", pos)) {
            pos += 4
        } else {
            pos++
        }
    }

    private fun parseNumber(): Double {
        val start = pos
        if (pos < src.length && src[pos] == '-') pos++
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == 'e' || src[pos] == 'E' || src[pos] == '+' || src[pos] == '-')) {
            pos++
        }
        val numStr = src.substring(start, pos)
        return numStr.toDoubleOrNull() ?: 0.0
    }
}

object ProjectJsonSerializer {

    fun serializeProject(project: OpticalProject): String {
        return projectToJsonObject(project).toJsonString()
    }

    fun deserializeProject(jsonString: String): OpticalProject? {
        if (jsonString.isBlank()) return null
        return try {
            val parsed = SimpleJsonParser(jsonString).parseValue()
            if (parsed is JsonValue.JsonObject) {
                parseProject(parsed)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun serializeProjectList(projects: List<OpticalProject>): String {
        val array = JsonValue.JsonArray(projects.map { projectToJsonObject(it) })
        return array.toJsonString()
    }

    fun deserializeProjectList(jsonString: String): List<OpticalProject> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val parsed = SimpleJsonParser(jsonString).parseValue()
            if (parsed is JsonValue.JsonArray) {
                parsed.items.mapNotNull { item ->
                    if (item is JsonValue.JsonObject) parseProject(item) else null
                }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun projectToJsonObject(project: OpticalProject): JsonValue.JsonObject {
        return JsonValue.JsonObject(
            mapOf(
                "id" to JsonValue.JsonString(project.id),
                "name" to JsonValue.JsonString(project.name),
                "updatedAt" to JsonValue.JsonNumber(project.updatedAt.toDouble()),
                "source" to lightSourceToJsonObject(project.source),
                "elements" to JsonValue.JsonArray(project.elements.map { elementToJsonObject(it) })
            )
        )
    }

    private fun lightSourceToJsonObject(source: LightSource): JsonValue.JsonObject {
        return JsonValue.JsonObject(
            mapOf(
                "id" to JsonValue.JsonString(source.id),
                "name" to JsonValue.JsonString(source.name),
                "sourceType" to JsonValue.JsonString(source.sourceType.name),
                "cwlNm" to JsonValue.JsonNumber(source.cwlNm),
                "opticalPowerW" to JsonValue.JsonNumber(source.opticalPowerW),
                "divergenceXDeg" to JsonValue.JsonNumber(source.divergenceXDeg),
                "divergenceYDeg" to JsonValue.JsonNumber(source.divergenceYDeg),
                "emitterWidthMm" to JsonValue.JsonNumber(source.emitterWidthMm),
                "emitterHeightMm" to JsonValue.JsonNumber(source.emitterHeightMm),
                "positionZMm" to JsonValue.JsonNumber(source.positionZMm)
            )
        )
    }

    private fun elementToJsonObject(element: OpticalElement): JsonValue.JsonObject {
        val fields = mutableMapOf(
            "id" to JsonValue.JsonString(element.id),
            "name" to JsonValue.JsonString(element.name),
            "type" to JsonValue.JsonString(element.type.name),
            "positionZMm" to JsonValue.JsonNumber(element.positionZMm),
            "clearApertureMm" to JsonValue.JsonNumber(element.clearApertureMm),
            "focalLengthMm" to JsonValue.JsonNumber(element.focalLengthMm),
            "material" to glassMaterialToJsonObject(element.material),
            "isCoated" to JsonValue.JsonBoolean(element.isCoated),
            "customTransmissionPct" to JsonValue.JsonNumber(element.customTransmissionPct),
            "orientation" to JsonValue.JsonString(element.orientation.name),
            "isFlipped" to JsonValue.JsonBoolean(element.isFlipped),
            "enabled" to JsonValue.JsonBoolean(element.enabled)
        )
        if (element.explicitThicknessMm != null) {
            fields["explicitThicknessMm"] = JsonValue.JsonNumber(element.explicitThicknessMm)
        } else {
            fields["explicitThicknessMm"] = JsonValue.JsonNull
        }
        return JsonValue.JsonObject(fields)
    }

    private fun glassMaterialToJsonObject(material: GlassMaterial): JsonValue.JsonObject {
        return JsonValue.JsonObject(
            mapOf(
                "name" to JsonValue.JsonString(material.name),
                "nd" to JsonValue.JsonNumber(material.nd),
                "vd" to JsonValue.JsonNumber(material.vd),
                "surfaceReflectance" to JsonValue.JsonNumber(material.surfaceReflectance),
                "internalTransmissionPct" to JsonValue.JsonNumber(material.internalTransmissionPct)
            )
        )
    }

    private fun parseProject(obj: JsonValue.JsonObject): OpticalProject {
        val id = obj.getString("id", generateUuid())
        val name = obj.getString("name", "Untitled Project")
        val updatedAt = obj.getLong("updatedAt", currentTimeMillis())
        val sourceObj = obj.getObject("source")
        val source = if (sourceObj != null) parseLightSource(sourceObj) else OpticalPresets.LED_COLLIMATOR_1.source
        val elementsArray = obj.getArray("elements")
        val elements = elementsArray?.items?.mapNotNull {
            if (it is JsonValue.JsonObject) parseOpticalElement(it) else null
        } ?: OpticalPresets.LED_COLLIMATOR_1.elements

        return OpticalProject(
            id = id,
            name = name,
            updatedAt = updatedAt,
            source = source,
            elements = elements
        )
    }

    private fun parseLightSource(obj: JsonValue.JsonObject): LightSource {
        val sourceTypeStr = obj.getString("sourceType", SourceType.LED.name)
        val sourceType = try {
            SourceType.valueOf(sourceTypeStr)
        } catch (_: Exception) {
            SourceType.LED
        }

        return LightSource(
            id = obj.getString("id", "light_source"),
            name = obj.getString("name", "Light Source"),
            sourceType = sourceType,
            cwlNm = obj.getDouble("cwlNm", 520.0),
            opticalPowerW = obj.getDouble("opticalPowerW", 1.0),
            divergenceXDeg = obj.getDouble("divergenceXDeg", 30.0),
            divergenceYDeg = obj.getDouble("divergenceYDeg", 30.0),
            emitterWidthMm = obj.getDouble("emitterWidthMm", 1.0),
            emitterHeightMm = obj.getDouble("emitterHeightMm", 1.0),
            positionZMm = obj.getDouble("positionZMm", 0.0)
        )
    }

    private fun parseOpticalElement(obj: JsonValue.JsonObject): OpticalElement {
        val typeStr = obj.getString("type", OpticalElementType.PLANO_CONVEX.name)
        val type = try {
            OpticalElementType.valueOf(typeStr)
        } catch (_: Exception) {
            OpticalElementType.PLANO_CONVEX
        }

        val matObj = obj.getObject("material")
        val material = if (matObj != null) parseGlassMaterial(matObj) else GlassMaterial.N_BK7

        val orientStr = (obj.fields["orientation"] as? JsonValue.JsonString)?.value
        val orientation = if (orientStr != null) {
            try {
                com.example.optical_designer.model.LensOrientation.valueOf(orientStr)
            } catch (_: Exception) {
                com.example.optical_designer.model.LensOrientation.fromBoolean(obj.getBoolean("isFlipped", false))
            }
        } else {
            com.example.optical_designer.model.LensOrientation.fromBoolean(obj.getBoolean("isFlipped", false))
        }

        return OpticalElement(
            id = obj.getString("id", generateUuid()),
            name = obj.getString("name", "Lens"),
            type = type,
            positionZMm = obj.getDouble("positionZMm", 50.0),
            clearApertureMm = obj.getDouble("clearApertureMm", 30.0),
            focalLengthMm = obj.getDouble("focalLengthMm", 50.0),
            explicitThicknessMm = obj.getNullableDouble("explicitThicknessMm"),
            material = material,
            isCoated = obj.getBoolean("isCoated", false),
            customTransmissionPct = obj.getDouble("customTransmissionPct", 99.0),
            orientation = orientation,
            enabled = obj.getBoolean("enabled", true)
        )
    }

    private fun parseGlassMaterial(obj: JsonValue.JsonObject): GlassMaterial {
        val name = obj.getString("name", GlassMaterial.N_BK7.name)
        val nd = obj.getDouble("nd", GlassMaterial.N_BK7.nd)
        val vd = obj.getDouble("vd", GlassMaterial.N_BK7.vd)
        val surfaceReflectance = obj.getDouble("surfaceReflectance", GlassMaterial.N_BK7.surfaceReflectance)
        val internalTransmissionPct = obj.getDouble("internalTransmissionPct", GlassMaterial.N_BK7.internalTransmissionPct)

        return GlassMaterial(
            name = name,
            nd = nd,
            vd = vd,
            surfaceReflectance = surfaceReflectance,
            internalTransmissionPct = internalTransmissionPct
        )
    }
}
