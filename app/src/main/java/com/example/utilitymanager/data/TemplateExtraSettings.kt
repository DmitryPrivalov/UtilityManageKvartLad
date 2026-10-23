package com.example.utilitymanager.data

import org.json.JSONObject

/**
 * Дополнительные поля сохраняются в СУЩЕСТВУЮЩЕМ столбце
 * calculation_templates.extraSettingsJson внутри объекта dynamicFields.
 * Другие ключи JSON, включая неизвестные будущие настройки, сохраняются.
 */
object TemplateExtraSettings {
    private const val DYNAMIC_FIELDS = "dynamicFields"

    fun read(extraSettingsJson: String): Map<String, String> {
        val original = parseObject(extraSettingsJson)
        val rawFields = original.opt(DYNAMIC_FIELDS)
        require(rawFields == null || rawFields == JSONObject.NULL || rawFields is JSONObject) {
            "dynamicFields должен быть JSON-объектом"
        }
        val fields = rawFields as? JSONObject ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        val keys = fields.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = fields.opt(key)
            if (value != null && value != JSONObject.NULL) {
                result[key] = value.toString()
            }
        }
        return result
    }

    /** Обновляем известные значения без потери старых и посторонних настроек. */
    fun write(extraSettingsJson: String, changedValues: Map<String, String>): String {
        val root = parseObject(extraSettingsJson)
        val previousValues = root.opt(DYNAMIC_FIELDS)
        require(previousValues == null || previousValues == JSONObject.NULL || previousValues is JSONObject) {
            "dynamicFields должен быть JSON-объектом"
        }
        val values = previousValues as? JSONObject ?: JSONObject()
        changedValues.forEach { (id, value) ->
            values.put(id, value)
        }
        root.put(DYNAMIC_FIELDS, values)
        return root.toString()
    }

    fun validate(
        fields: List<TemplateFieldDefinition>,
        input: Map<String, String>
    ): List<String> = fields.mapNotNull { field ->
        val value = (input[field.id] ?: field.defaultValue).trim()
        when {
            field.required && value.isBlank() -> "Заполни поле «${field.label}»"
            value.isBlank() -> null
            field.type == "decimal" && value.replace(',', '.').toBigDecimalOrNull() == null ->
                "«${field.label}»: нужно число"
            field.type == "integer" && value.toLongOrNull() == null ->
                "«${field.label}»: нужно целое число"
            field.type == "boolean" && value !in setOf("true", "false") ->
                "«${field.label}»: нужно true или false"
            else -> null
        }
    }

    private fun parseObject(source: String): JSONObject {
        if (source.isBlank()) return JSONObject()
        // Не игнорируем ошибку: иначе можно было бы затереть старые данные.
        return JSONObject(source)
    }
}
