package com.example.utilitymanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Элемент списка показаний; получаем его из состояния MainActivity. */
data class ApartmentTemplateOptionUi(val id: Long, val name: String)

data class ApartmentMeterReadingUi(
    val meterName: String,
    val reading: String,
    val period: String = ""
)

/**
 * Одна строка начисления.
 * monthTitle нужен для группировки в отдельные контейнеры: "Октябрь 2026", "Ноябрь 2026" и т.д.
 */
data class ApartmentServiceChargeUi(
    val serviceName: String,
    val monthlyUsage: String,
    val unitTariff: String,
    val monthlyAmount: String,
    val monthTitle: String = "Текущий месяц"
)

private val CardSurface = Color(0xFF122034)
private val CardBorder = Color(0xFF314258)
private val TextPrimary = Color(0xFFF5F7FC)
private val TextSecondary = Color(0xFFADBACB)
private val ButtonBlue = Color(0xFF204F93)
private val AmountBackground = Color(0xFF214C89)
private val ServiceNameBackground = Color(0xFF173E69)
private val ServiceItemBackground = Color(0xFF15253A)
private val GridLine = Color(0xFF2B3B51)
private val MonthBackground = Color(0xFF183251)
private val MonthTitleColor = Color(0xFF9FD0FF)

/**
 * Основные блоки деталей квартиры. Верхняя панель и История/PDF остаются в MainActivity.
 * Скролл остаётся у родителя ApartmentDetails.
 */
@Composable
fun ApartmentCardSections(
    apartmentNumber: String,
    resident: String?,
    meterReadings: List<ApartmentMeterReadingUi>,
    serviceCharges: List<ApartmentServiceChargeUi>,
    visibleMonthTitles: List<String>,
    templates: List<ApartmentTemplateOptionUi>,
    selectedTemplateId: Long?,
    templateError: String?,
    onTemplateSelected: (Long?) -> Unit,
    onEnterReadings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Номер/жилец и прикреплённый к квартире шаблон.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.width(115.dp)) {
                        Text("Квартира №", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            apartmentNumber.ifBlank { "—" },
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.width(1.dp).height(40.dp).background(CardBorder))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Жилец", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            resident?.takeIf { it.isNotBlank() } ?: "Не указан",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                ThinDivider()
                Spacer(Modifier.height(12.dp))
                Text("Шаблон", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                var expanded by remember { mutableStateOf(false) }
                val chosen = templates.firstOrNull { it.id == selectedTemplateId }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = when {
                                chosen != null -> chosen.name
                                selectedTemplateId != null -> "Шаблон удалён — выбери другой"
                                else -> "Выбрать шаблон"
                            },
                            modifier = Modifier.weight(1f),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("▾", color = TextPrimary)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Без шаблона") },
                            onClick = {
                                expanded = false
                                onTemplateSelected(null)
                            }
                        )
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.name) },
                                onClick = {
                                    expanded = false
                                    onTemplateSelected(template.id)
                                }
                            )
                        }
                    }
                }
                if (templateError != null) {
                    Spacer(Modifier.height(7.dp))
                    Text(templateError, color = Color(0xFFFF8D8D), fontSize = 12.sp)
                }
            }
        }

        // 2. Показания + кнопка перехода в уже существующий экран.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Показания счётчиков",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onEnterReadings,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue)
                ) {
                    Text("ВВЕСТИ ПОКАЗАНИЯ", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text("Последние показания", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                if (meterReadings.isEmpty()) {
                    Text("Показаний пока нет", color = TextSecondary, fontSize = 14.sp)
                } else {
                    meterReadings.forEachIndexed { index, reading ->
                        if (index > 0) ThinDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(reading.meterName, color = TextPrimary, fontSize = 14.sp)
                                if (reading.period.isNotBlank()) {
                                    Text(reading.period, color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                reading.reading,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        // 3. Группы начислений по месяцам.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                Text(
                    "Начисления и услуги",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                val grouped = serviceCharges.groupBy { it.monthTitle }
                visibleMonthTitles.forEachIndexed { index, title ->
                    MonthlyChargesBlock(
                        monthTitle = title,
                        charges = grouped[title].orEmpty(),
                        initiallyExpanded = index == 0
                    )
                    if (index != visibleMonthTitles.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Переиспользуемая карточка месяца для квартиры и истории.
 * В свернутом виде остаётся только название месяца и стрелка.
 */
@Composable
fun MonthlyChargesBlock(
    monthTitle: String,
    charges: List<ApartmentServiceChargeUi>,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember(monthTitle) { mutableStateOf(initiallyExpanded) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ServiceItemBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, GridLine)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MonthBackground, RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = monthTitle.replaceFirstChar { it.uppercaseChar() },
                    modifier = Modifier.fillMaxWidth(0.88f),
                    color = MonthTitleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (expanded) "⌃" else "⌄",
                    modifier = Modifier.fillMaxWidth(),
                    color = MonthTitleColor,
                    fontSize = 22.sp,
                    textAlign = TextAlign.End
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                if (charges.isEmpty()) {
                    Text(
                        "Начислений за этот месяц пока нет",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Услуга",
                            modifier = Modifier.fillMaxWidth(0.55f),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Сумма за месяц",
                            modifier = Modifier.fillMaxWidth(),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    charges.forEachIndexed { index, charge ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        ServiceChargeRow(charge)
                    }
                    if (charges.all { it.unitTariff == "—" && it.monthlyAmount == "—" }) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Тарифы и суммы появятся после настройки шаблона.",
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Одна услуга: название и итог заметные, расход и тариф ниже.
 * Всё без горизонтальной прокрутки — под ширину телефона.
 */
@Composable
private fun ServiceChargeRow(service: ApartmentServiceChargeUi) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GridLine)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .heightIn(min = 48.dp)
                        .background(ServiceNameBackground, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        service.serviceName,
                        color = Color(0xFF8DC6FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.width(7.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(AmountBackground, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        service.monthlyAmount,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(11.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                    Text(
                        "Расход за месяц",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        service.monthlyUsage,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.width(7.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Цена за ед.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        service.unitTariff,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GridLine))
}
