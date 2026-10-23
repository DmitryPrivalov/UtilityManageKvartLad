package com.example.utilitymanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Определения полей читаются из app/src/main/assets/template_fields.json. */
data class TemplateFieldDefinition(
    val id: String,
    val label: String,
    val type: String, // text, decimal, integer, boolean
    val section: String,
    val unit: String = "",
    val defaultValue: String = "",
    val required: Boolean = false
)

/** Ключ услуги должен оставаться неизменным после сохранения начислений. */
data class TemplateServiceDefinition(
    val key: String,
    val name: String,
    val unit: String,
    val tariffField: String,
    val billingType: String = "meter",
    val enabledField: String = ""
)

data class TemplateFieldsCatalog(
    val schemaVersion: Int,
    val fields: List<TemplateFieldDefinition>,
    val services: List<TemplateServiceDefinition>
) {
    companion object {
        fun load(context: Context): TemplateFieldsCatalog {
            val json = context.assets.open("template_fields.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return fromJson(json)
        }

        fun fromJson(json: String): TemplateFieldsCatalog {
            val root = JSONObject(json)
            val fieldArray = root.getJSONArray("fields")
            val fields = (0 until fieldArray.length()).map { index ->
                val item = fieldArray.getJSONObject(index)
                TemplateFieldDefinition(
                    id = item.getString("id").trim(),
                    label = item.getString("label"),
                    type = item.optString("type", "text"),
                    section = item.optString("section", "Дополнительно"),
                    unit = item.optString("unit", ""),
                    defaultValue = item.optString("default", ""),
                    required = item.optBoolean("required", false)
                )
            }
            require(fields.all { it.id.matches(Regex("[a-z][a-z0-9_]*")) }) {
                "Неверный id поля (используй a-z, 0-9 и _; сначала буква)"
            }
            require(fields.map { it.id }.distinct().size == fields.size) {
                "В template_fields.json есть повторяющиеся id полей"
            }
            require(fields.all { it.type in setOf("text", "decimal", "integer", "boolean") }) {
                "Неизвестный тип поля; доступны text, decimal, integer, boolean"
            }

            val serviceArray = root.optJSONArray("services") ?: JSONArray()
            val services = (0 until serviceArray.length()).map { index ->
                val item = serviceArray.getJSONObject(index)
                TemplateServiceDefinition(
                    key = item.getString("key"),
                    name = item.getString("name"),
                    unit = item.getString("unit"),
                    tariffField = item.getString("tariffField"),
                    billingType = item.optString("billingType", "meter"),
                    enabledField = item.optString("enabledField", "")
                )
            }
            require(services.map { it.key }.distinct().size == services.size) {
                "В template_fields.json есть повторяющиеся key услуг"
            }
            require(services.all { service -> fields.any { it.id == service.tariffField } }) {
                "У услуги tariffField должен ссылаться на id существующего поля"
            }
            require(services.all { it.billingType in setOf("meter", "fixed") }) {
                "Для услуги billingType должен быть meter или fixed"
            }
            require(services.all { it.enabledField.isBlank() || fields.any { field ->
                field.id == it.enabledField && field.type == "boolean"
            } }) { "enabledField должен ссылаться на логическое поле" }
            return TemplateFieldsCatalog(root.optInt("schemaVersion", 1), fields, services)
        }
    }
}
