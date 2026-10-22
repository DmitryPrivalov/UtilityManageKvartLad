
package com.example.utilitymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import com.example.utilitymanager.data.TemplateDao
import com.example.utilitymanager.data.TemplateEntity
import kotlinx.coroutines.launch

@Composable
fun TemplateScreen(dao: TemplateDao) {
    val templates by dao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember {
        mutableStateOf<TemplateEntity?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Шаблоны расчётов")

        Spacer(Modifier.height(12.dp))

        Text(
            "Шаблон задаёт расчётный период. " +
                    "Позже здесь добавим настройки услуг и формулы."
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                editingTemplate = null
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Создать шаблон")
        }

        Spacer(Modifier.height(12.dp))

        if (templates.isEmpty()) {
            Text("Шаблонов пока нет. Создай первый шаблон.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(template.name)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Начало расчётного периода: " +
                                        "${template.startDay}-е число"
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        editingTemplate = template
                                        showEditor = true
                                    }
                                ) {
                                    Text("Изменить")
                                }

                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            dao.deleteById(template.id)
                                        }
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

    if (showEditor) {
        TemplateEditorDialog(
            template = editingTemplate,
            onDismiss = { showEditor = false },
            onSave = { name, startDay ->
                val old = editingTemplate

                scope.launch {
                    dao.save(
                        TemplateEntity(
                            id = old?.id ?: 0L,
                            name = name,
                            startDay = startDay,
                            extraSettingsJson =
                                old?.extraSettingsJson ?: "{}"
                        )
                    )
                    showEditor = false
                }
            }
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    template: TemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var name by remember(template?.id) {
        mutableStateOf(template?.name.orEmpty())
    }
    var startDayText by remember(template?.id) {
        mutableStateOf(
            (template?.startDay ?: 22).toString()
        )
    }

    val startDay = startDayText.toIntOrNull()
    val valid = name.isNotBlank() &&
            startDay != null && startDay in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (template == null) {
                    "Новый шаблон"
                } else {
                    "Редактирование шаблона"
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название шаблона") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = startDayText,
                    onValueChange = {
                        if (it.length <= 2 && it.all(Char::isDigit)) {
                            startDayText = it
                        }
                    },
                    label = { Text("День начала периода (1–31)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(name.trim(), startDay!!)
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
