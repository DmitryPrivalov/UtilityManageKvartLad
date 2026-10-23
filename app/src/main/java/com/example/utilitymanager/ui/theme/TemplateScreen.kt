package com.example.utilitymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.utilitymanager.data.TemplateDao
import com.example.utilitymanager.data.TemplateEntity
import com.example.utilitymanager.data.TemplateExtraSettings
import com.example.utilitymanager.data.TemplateFieldDefinition
import com.example.utilitymanager.data.TemplateFieldsCatalog
import kotlinx.coroutines.launch

/**
 * Шаблоны расчётов. Определения динамических полей: assets/template_fields.json.
 * Значения каждого шаблона: calculation_templates.extraSettingsJson / dynamicFields.
 */
@Composable
fun TemplateScreen(dao: TemplateDao) {
    val templates by dao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Ошибку каталога показываем на экране вместо падения при отсутствии JSON.
    val catalogResult = remember(context) {
        runCatching { TemplateFieldsCatalog.load(context) }
    }
    val catalog = catalogResult.getOrNull()

    var showEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TemplateEntity?>(null) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Шаблоны расчётов",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Настрой название, начало периода и параметры услуг. " +
                "Дополнительные поля описываются в template_fields.json."
        )
        Spacer(Modifier.height(12.dp))

        if (catalog == null) {
            Text(
                text = "Не удалось открыть template_fields.json: " +
                    (catalogResult.exceptionOrNull()?.message ?: "неизвестная ошибка"),
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                editingTemplate = null
                editorError = null
                showEditor = true
            },
            enabled = catalog != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Создать шаблон")
        }
        Spacer(Modifier.height(12.dp))

        if (templates.isEmpty()) {
            Text("Шаблонов пока нет. Создай первый шаблон.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Начало расчётного периода: ${template.startDay}-е число")
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    enabled = catalog != null,
                                    onClick = {
                                        editingTemplate = template
                                        editorError = null
                                        showEditor = true
                                    }
                                ) {
                                    Text("Изменить")
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch { dao.deleteById(template.id) }
                                    }
                                ) {
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor && catalog != null) {
        TemplateEditorDialog(
            template = editingTemplate,
            definitions = catalog.fields,
            saving = saving,
            saveError = editorError,
            onDismiss = { if (!saving) showEditor = false },
            onSave = { name, startDay, fieldValues ->
                val old = editingTemplate
                try {
                    // write сохраняет остальные ключи extraSettingsJson и старые неизвестные поля.
                    val updatedJson = TemplateExtraSettings.write(
                        extraSettingsJson = old?.extraSettingsJson ?: "{}",
                        changedValues = fieldValues
                    )
                    scope.launch {
                        saving = true
                        editorError = null
                        try {
                            dao.save(
                                TemplateEntity(
                                    id = old?.id ?: 0L,
                                    name = name,
                                    startDay = startDay,
                                    extraSettingsJson = updatedJson
                                )
                            )
                            showEditor = false
                        } catch (error: Exception) {
                            editorError = "Не удалось сохранить шаблон: " +
                                (error.message ?: "ошибка базы данных")
                        } finally {
                            saving = false
                        }
                    }
                } catch (error: Exception) {
                    editorError = "Не удалось обновить настройки: " +
                        (error.message ?: "некорректный JSON")
                }
            }
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    template: TemplateEntity?,
    definitions: List<TemplateFieldDefinition>,
    saving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, startDay: Int, fieldValues: Map<String, String>) -> Unit
) {
    var name by remember(template?.id) {
        mutableStateOf(template?.name.orEmpty())
    }
    var startDayText by remember(template?.id) {
        mutableStateOf((template?.startDay ?: 22).toString())
    }

    // Если старый JSON повреждён, не перезаписываем его молча.
    val readResult = remember(template?.id, template?.extraSettingsJson) {
        runCatching {
            TemplateExtraSettings.read(template?.extraSettingsJson ?: "{}")
        }
    }
    var values by remember(template?.id, template?.extraSettingsJson) {
        mutableStateOf(readResult.getOrDefault(emptyMap()))
    }

    // Даже не изменённые пользователем значения по умолчанию сохраняем в Room.
    val completeValues = definitions.associate { definition ->
        definition.id to (values[definition.id] ?: definition.defaultValue)
    }
    val fieldErrors = TemplateExtraSettings.validate(definitions, completeValues)
    val startDay = startDayText.toIntOrNull()
    val valid = name.isNotBlank() &&
        startDay != null && startDay in 1..31 &&
        fieldErrors.isEmpty() && readResult.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (template == null) "Новый шаблон" else "Редактирование шаблона")
        },
        text = {
            // Большое число дополнительных полей прокручивается ВНУТРИ диалога.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название шаблона") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startDayText,
                    onValueChange = { text ->
                        if (text.length <= 2 && text.all(Char::isDigit)) {
                            startDayText = text
                        }
                    },
                    label = { Text("День начала периода (1–31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Настройки услуг и тарифов",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TemplateDynamicFieldsEditor(
                    definitions = definitions,
                    values = values,
                    onValueChange = { id, value -> values = values + (id to value) }
                )
                if (readResult.isFailure) {
                    Text(
                        text = "Старые настройки шаблона не читаются: " +
                            (readResult.exceptionOrNull()?.message ?: "ошибка JSON"),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                fieldErrors.take(3).forEach { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                if (saveError != null) {
                    Text(saveError, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid && !saving,
                onClick = { onSave(name.trim(), startDay!!, completeValues) }
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Сохранить")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Отмена") }
        }
    )
}
