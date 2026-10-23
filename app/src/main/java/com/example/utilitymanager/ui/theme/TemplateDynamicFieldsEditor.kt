package com.example.utilitymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.utilitymanager.data.TemplateFieldDefinition

/**
 * Универсальный редактор: показывает поля, перечисленные в template_fields.json.
 * Данные должны храниться в состоянии родительского TemplateScreen.
 */
@Composable
fun TemplateDynamicFieldsEditor(
    definitions: List<TemplateFieldDefinition>,
    values: Map<String, String>,
    onValueChange: (fieldId: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        definitions.groupBy { it.section }.forEach { (section, group) ->
            Text(
                text = section,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            group.forEach { definition ->
                TemplateDynamicField(
                    definition = definition,
                    value = values[definition.id] ?: definition.defaultValue,
                    onValueChange = { onValueChange(definition.id, it) }
                )
            }
        }
    }
}

@Composable
private fun TemplateDynamicField(
    definition: TemplateFieldDefinition,
    value: String,
    onValueChange: (String) -> Unit
) {
    if (definition.type == "boolean") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = definition.label,
                modifier = Modifier.fillMaxWidth(0.78f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = value.equals("true", ignoreCase = true),
                onCheckedChange = { onValueChange(it.toString()) }
            )
        }
    } else {
        val keyboardType = when (definition.type) {
            "decimal" -> KeyboardType.Decimal
            "integer" -> KeyboardType.Number
            else -> KeyboardType.Text
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(definition.label + if (definition.required) " *" else "")
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true
            )
            if (definition.unit.isNotBlank()) {
                Text(
                    definition.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
