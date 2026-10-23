package com.example.utilitymanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utilitymanager.data.MonthlyChargeEntity
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HistoryCard = Color(0xFF122034)
private val HistoryBorder = Color(0xFF314258)
private val HistoryMuted = Color(0xFFB1BED2)
private val HistoryText = Color(0xFFF4F7FF)

private fun monthName(month: String): String =
    YearMonth.parse(month)
        .format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru", "RU")))
        .replaceFirstChar { it.uppercaseChar() }

private fun toChargeUi(row: MonthlyChargeEntity) = ApartmentServiceChargeUi(
    serviceName = row.serviceName,
    monthlyUsage = row.usage?.let { "${it.replace('.', ',')} ${row.unit}" } ?: "—",
    unitTariff = row.unitPrice?.let { "${it.replace('.', ',')} ₽/${row.unit}" } ?: "—",
    monthlyAmount = row.amount?.let { "${it.replace('.', ',')} ₽" } ?: "—",
    monthTitle = monthName(row.yearMonth)
)

/** Вкладка «История»: выбор квартиры без перехода через основную карточку. */
@Composable
fun HistoryApartmentsScreen(
    apartments: List<Apartment>,
    onOpenApartment: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text("История начислений", color = HistoryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Выбери квартиру для просмотра начислений за все месяцы", color = HistoryMuted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        if (apartments.isEmpty()) {
            Text("Квартир пока нет", color = HistoryMuted)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(apartments, key = { it.id }) { apartment ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenApartment(apartment.id) },
                        colors = CardDefaults.cardColors(containerColor = HistoryCard),
                        border = BorderStroke(1.dp, HistoryBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Квартира № ${apartment.number}", color = HistoryText, fontWeight = FontWeight.Bold)
                            Text(apartment.address, color = HistoryMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Полная история начислений: текущий, предыдущие два и все сохранённые старые месяцы.
 * Последние три месяца показываем даже без записей (с поясняющей заглушкой).
 * Сами начисления берутся из Room. Автоматического удаления в фоне нет.
 */
@Composable
fun MonthlyHistoryScreen(
    apartmentNumber: String,
    savedCharges: List<MonthlyChargeEntity>,
    currentMonthPreview: List<ApartmentServiceChargeUi> = emptyList(),
    retentionDisabled: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetentionChanged: (Boolean) -> Unit,
    onDeleteExpired: (String) -> Unit
) {
    // Календарный месяц, а не 180 дней.
    val now = YearMonth.now()
    val recentMonths = (0..2).map { now.minusMonths(it.toLong()).toString() }
    val cutoff = now.minusMonths(6)
    val chargesByMonth = savedCharges.groupBy { it.yearMonth }
    // Сначала все три последних месяца, затем все более старые сохранённые месяцы.
    // distinct() убирает дубликаты, sortedDescending() выводит свежие месяцы первыми.
    val allMonths = (recentMonths + chargesByMonth.keys).distinct().sortedDescending()
    // Удалять предлагаем только реально сохранённые строки, а не пустые заглушки.
    val expiredMonths = chargesByMonth.keys.filter { YearMonth.parse(it).isBefore(cutoff) }

    var showDeleteDialog by remember(apartmentNumber) { mutableStateOf(false) }
    var disableFromDialog by remember(apartmentNumber) { mutableStateOf(false) }
    var promptedOnThisVisit by remember(apartmentNumber) { mutableStateOf(false) }

    // Сначала только спрашиваем. Без согласия записи никогда не удаляются.
    LaunchedEffect(expiredMonths.size, retentionDisabled) {
        if (expiredMonths.isNotEmpty() && !retentionDisabled && !promptedOnThisVisit) {
            showDeleteDialog = true
            promptedOnThisVisit = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp),
                color = HistoryText,
                fontSize = 38.sp
            )
            Column {
                Text("История начислений", color = HistoryText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Квартира № $apartmentNumber", color = HistoryMuted, fontSize = 13.sp)
            }
        }

        // Настройка сбоку от текста. Сохранена на устройстве через SharedPreferences.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.78f)) {
                Text("Не удалять старые месяцы", color = HistoryText, fontSize = 14.sp)
                Text("Отключает напоминания и очистку", color = HistoryMuted, fontSize = 12.sp)
            }
            Checkbox(
                checked = retentionDisabled,
                onCheckedChange = onRetentionChanged
            )
        }

        if (expiredMonths.isNotEmpty() && !retentionDisabled) {
            OutlinedButton(
                onClick = {
                    disableFromDialog = false
                    showDeleteDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Проверить старые записи (${expiredMonths.size})")
            }
        }

        if (errorMessage != null) {
            Text(errorMessage, color = Color(0xFFFF8E8E), fontSize = 13.sp)
        }

        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            items(allMonths, key = { it }) { month ->
                MonthlyChargesBlock(
                    monthTitle = monthName(month),
                    charges = if (month == now.toString() && currentMonthPreview.isNotEmpty()) {
                        // В истории текущий месяц выглядит так же, как в карточке квартиры:
                        // сохранённые строки + услуги шаблона, даже если месяц ещё не закрыт.
                        currentMonthPreview
                    } else {
                        chargesByMonth[month].orEmpty().map(::toChargeUi)
                    },
                    initiallyExpanded = false
                )
            }
        }
    }

    if (showDeleteDialog && expiredMonths.isNotEmpty() && !retentionDisabled) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Очистка истории") },
            text = {
                Column {
                    Text(
                        "Найдено ${expiredMonths.size} мес. начислений старше шести месяцев. " +
                            "Удалить эти записи из истории? Без твоего подтверждения они сохранятся."
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = disableFromDialog,
                            onCheckedChange = { disableFromDialog = it }
                        )
                        Text("Больше не спрашивать и не удалять", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (disableFromDialog) {
                        onRetentionChanged(true)
                    } else {
                        onDeleteExpired(cutoff.toString())
                    }
                    showDeleteDialog = false
                }) {
                    Text(if (disableFromDialog) "Сохранить настройку" else "Удалить", color = Color(0xFFFF7777))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Оставить") }
            }
        )
    }
}
