package com.example.utilitymanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.example.utilitymanager.data.UtilityDatabase
import kotlinx.coroutines.launch
import com.example.utilitymanager.ui.TemplateScreen
import com.example.utilitymanager.data.ApartmentEntity
import com.example.utilitymanager.data.MonthlyChargeEntity
import com.example.utilitymanager.data.TemplateEntity
import com.example.utilitymanager.data.TemplateFieldsCatalog
import com.example.utilitymanager.data.TemplateBilling
import com.example.utilitymanager.data.ResolvedTemplateService
import com.example.utilitymanager.data.HistoryRetentionSettings
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.draw.clip

private val AppBackground = Color(0xFF070E19)
private val CardBackground = Color(0xFF111D30)
private val FieldBackground = Color(0xFF192941)
private val AccentBlue = Color(0xFF2878FF)
private val MainText = Color(0xFFF4F7FF)
private val SecondaryText = Color(0xFFB1BED2)
private val Green = Color(0xFF00C78A)
private val BorderColor = Color(0xFF293B55)

private val AppColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = AppBackground,
    onBackground = MainText,
    surface = CardBackground,
    onSurface = MainText,
    secondary = SecondaryText
)

data class Apartment(
    val id: Long,
    val number: String,
    val address: String,
    val tenant: String,
    val templateId: Long? = null
)

data class MeterReading(
    val current: String = "",
    val previous: String = ""
)

private enum class AppScreen {
    APARTMENTS,
    TEMPLATES,
    HISTORY,
    HISTORY_DETAILS,
    STATISTICS,
    ADD_APARTMENT,
    DETAILS,
    METERS
}

private val meterNames = listOf(
    "Холодная вода",
    "Электроэнергия",
    "Газ"
)

private val meterKeys = listOf("cold_water", "electricity", "gas")
private val meterIcons = listOf("💧", "⚡", "🔥")
private val meterUnits = listOf("м³", "кВт·ч", "м³")

private fun monthTitle(isoMonth: String): String =
    YearMonth.parse(isoMonth).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru", "RU")))

private fun parseReadingValue(text: String): BigDecimal? =
    text.trim().replace(',', '.').toBigDecimalOrNull()

private fun checkReadings(values: List<MeterReading>, allowEmpty: Boolean = false): String? {
    if (values.none { it.previous.isNotBlank() || it.current.isNotBlank() }) {
        return if (allowEmpty) null else "Введите хотя бы одну пару показаний"
    }
    values.forEachIndexed { index, reading ->
        val prevText = reading.previous.trim()
        val currText = reading.current.trim()
        if (prevText.isEmpty() && currText.isEmpty()) return@forEachIndexed
        val previous = parseReadingValue(prevText)
        val current = parseReadingValue(currText)
        if (previous == null || current == null || previous < BigDecimal.ZERO || current < BigDecimal.ZERO) {
            return "Услуга «${meterNames[index]}»: укажи два корректных неотрицательных числа"
        }
        if (current < previous) {
            return "Услуга «${meterNames[index]}»: текущее показание меньше предыдущего"
        }
    }
    return null
}

private fun decimalString(value: BigDecimal): String =
    value.stripTrailingZeros().toPlainString()

private fun persistedReadings(charges: List<MonthlyChargeEntity>, month: String): List<MeterReading> =
    meterKeys.map { key ->
        val charge = charges.firstOrNull { it.yearMonth == month && it.serviceKey == key }
        MeterReading(
            current = charge?.currentReading.orEmpty(),
            previous = charge?.previousReading.orEmpty()
        )
    }

/** Снимок тарифов и сумм на момент сохранения начислений за месяц. */
private fun chargesForMonth(
    apartmentId: Long,
    month: String,
    values: List<MeterReading>,
    existing: List<MonthlyChargeEntity>,
    services: List<ResolvedTemplateService>
): List<MonthlyChargeEntity> {
    val previous = existing.filter { it.yearMonth == month }.associateBy { it.serviceKey }
    val byKey = services.associateBy { it.key }
    val meters = meterNames.mapIndexedNotNull { index, name ->
        val reading = values.getOrNull(index) ?: return@mapIndexedNotNull null
        if (reading.previous.isBlank() && reading.current.isBlank()) return@mapIndexedNotNull null
        TemplateBilling.meterRow(
            apartmentId = apartmentId,
            month = month,
            key = meterKeys[index],
            defaultName = name,
            defaultUnit = meterUnits[index],
            previous = parseReadingValue(reading.previous)!!,
            current = parseReadingValue(reading.current)!!,
            previousRow = previous[meterKeys[index]],
            service = byKey[meterKeys[index]]
        )
    }
    val fixed = services.filter { it.isFixed }.map { service ->
        TemplateBilling.fixedRow(apartmentId, month, service, previous[service.key])
    }
    return meters + fixed
}

private fun chargeRowUi(row: MonthlyChargeEntity): ApartmentServiceChargeUi =
    ApartmentServiceChargeUi(
        monthTitle = monthTitle(row.yearMonth),
        serviceName = row.serviceName,
        monthlyUsage = row.usage?.let { "${it.replace('.', ',')} ${row.unit}" } ?: "—",
        unitTariff = row.unitPrice?.let { "${it.replace('.', ',')} ₽/${row.unit}" } ?: "—",
        monthlyAmount = row.amount?.let { "${it.replace('.', ',')} ₽" } ?: "—"
    )

/** Показываем не сохранённые ещё услуги текущего месяца из выбранного шаблона. */
private fun previewMonth(
    month: String,
    saved: List<MonthlyChargeEntity>,
    services: List<ResolvedTemplateService>
): List<ApartmentServiceChargeUi> {
    val existing = saved.associateBy { it.serviceKey }
    val defined = services.map { service ->
        val row = existing[service.key]
        val price = row?.unitPrice?.toBigDecimalOrNull() ?: service.price
        val usage = row?.usage?.toBigDecimalOrNull() ?: if (service.isFixed) BigDecimal.ONE else null
        val sum = row?.amount ?: TemplateBilling.amount(usage, price)
        ApartmentServiceChargeUi(
            monthTitle = monthTitle(month),
            serviceName = row?.serviceName ?: service.name,
            monthlyUsage = usage?.let { "${TemplateBilling.normalized(it).replace('.', ',')} ${row?.unit ?: service.unit}" } ?: "—",
            unitTariff = price?.let { "${TemplateBilling.normalized(it).replace('.', ',')} ₽/${row?.unit ?: service.unit}" } ?: "—",
            monthlyAmount = sum?.let { "${it.replace('.', ',')} ₽" } ?: "—"
        )
    }
    return defined + saved.filter { row -> services.none { it.key == row.serviceKey } }.map(::chargeRowUi)
}

/** Расход за месяц показываем только для корректной пары показаний. */
private fun monthlyConsumption(reading: MeterReading?, unit: String): String {
    if (reading == null) return "—"

    val previous = reading.previous.trim().replace(',', '.').toBigDecimalOrNull()
        ?: return "—"
    val current = reading.current.trim().replace(',', '.').toBigDecimalOrNull()
        ?: return "—"
    if (current < previous) return "—"

    val consumption = current.subtract(previous)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')

    return "$consumption $unit"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(7, 14, 25)
        window.navigationBarColor = android.graphics.Color.rgb(7, 14, 25)
        window.decorView.systemUiVisibility = 0

        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground),
                    color = AppBackground
                ) {
                    UtilityApp()
                }
            }
        }
    }
}

@Composable
private fun UtilityApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember {
        UtilityDatabase.getDatabase(context).apartmentDao()
    }
    val templateDao = remember {
        UtilityDatabase.getDatabase(context).templateDao()
    }
    val monthlyChargeDao = remember {
        UtilityDatabase.getDatabase(context).monthlyChargeDao()
    }
    val scope = rememberCoroutineScope()

    val apartmentsFromDb by dao.getAll()
        .collectAsState(initial = emptyList())
    val templatesFromDb by templateDao.getAll()
        .collectAsState(initial = emptyList())
    val templateOptions = templatesFromDb.map { ApartmentTemplateOptionUi(it.id, it.name) }
    val catalogResult = remember(context) { runCatching { TemplateFieldsCatalog.load(context) } }
    val catalog = catalogResult.getOrNull()
    var selectionError by remember { mutableStateOf<String?>(null) }

    val apartments = apartmentsFromDb.map { entity ->
        Apartment(
            id = entity.id,
            number = entity.number,
            address = entity.address,
            tenant = entity.tenant,
            templateId = entity.templateId
        )
    }

    var screen by remember { mutableStateOf(AppScreen.APARTMENTS) }
    var selectedId by remember { mutableStateOf(-1L) }
    var editingId by remember { mutableStateOf(-1L) }

    // Несохранённые изменения разделены по квартире и расчётному месяцу.
    var readings by remember {
        mutableStateOf<Map<Pair<Long, String>, List<MeterReading>>>(emptyMap())
    }
    var selectedMonth by remember { mutableStateOf(YearMonth.now().toString()) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var historyBackScreen by remember { mutableStateOf(AppScreen.DETAILS) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var retentionDisabled by remember {
        mutableStateOf(HistoryRetentionSettings.isDisabled(context))
    }

    val savedCharges by remember(selectedId) {
        monthlyChargeDao.observeByApartment(selectedId)
    }.collectAsState(initial = emptyList())

    val selectedApartment = apartments.find { it.id == selectedId }
    val selectedTemplate = templatesFromDb.firstOrNull { it.id == selectedApartment?.templateId }
    val servicesResult = remember(selectedTemplate, catalog) {
        runCatching {
            if (selectedTemplate == null || catalog == null) emptyList()
            else TemplateBilling.activeServices(selectedTemplate, catalog)
        }
    }
    val activeServices = servicesResult.getOrDefault(emptyList())
    val templateError = selectionError
        ?: catalogResult.exceptionOrNull()?.message?.let { "Не удалось прочитать каталог услуг: $it" }
        ?: servicesResult.exceptionOrNull()?.message?.let { "Ошибка шаблона: $it" }

    val mainTab = screen in setOf(
        AppScreen.APARTMENTS,
        AppScreen.TEMPLATES,
        AppScreen.HISTORY,
        AppScreen.STATISTICS
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(AppBackground)
            .padding(horizontal = 18.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (screen) {
                AppScreen.APARTMENTS -> {
                    ApartmentList(
                        apartments = apartments,
                        templates = templateOptions,
                        onAdd = {
                            editingId = -1L
                            screen = AppScreen.ADD_APARTMENT
                        },
                        onOpen = {
                            selectedId = it.id
                            selectedMonth = YearMonth.now().toString()
                            saveError = null
                            screen = AppScreen.DETAILS
                        }
                    )
                }


                AppScreen.TEMPLATES -> TemplateScreen(
                    dao = templateDao
                )


                AppScreen.HISTORY -> {
                    HistoryApartmentsScreen(
                        apartments = apartments,
                        onOpenApartment = { apartmentId ->
                            selectedId = apartmentId
                            historyBackScreen = AppScreen.HISTORY
                            historyError = null
                            screen = AppScreen.HISTORY_DETAILS
                        }
                    )
                }

                AppScreen.HISTORY_DETAILS -> {
                    val apartment = selectedApartment
                    if (apartment == null) {
                        // Квартира могла быть удалена из другого экрана.
                        screen = AppScreen.HISTORY
                    } else {
                        MonthlyHistoryScreen(
                            apartmentNumber = apartment.number,
                            savedCharges = savedCharges,
                            currentMonthPreview = if (selectedTemplate != null && servicesResult.isSuccess) {
                                val month = YearMonth.now().toString()
                                previewMonth(
                                    month = month,
                                    saved = savedCharges.filter { it.yearMonth == month },
                                    services = activeServices
                                )
                            } else emptyList(),
                            retentionDisabled = retentionDisabled,
                            errorMessage = historyError,
                            onBack = { screen = historyBackScreen },
                            onRetentionChanged = { disabled ->
                                HistoryRetentionSettings.setDisabled(context, disabled)
                                retentionDisabled = disabled
                                historyError = null
                            },
                            onDeleteExpired = { cutoff ->
                                // Удаление возможно только после нажатия «Удалить» в диалоге.
                                if (!retentionDisabled) {
                                    scope.launch {
                                        try {
                                            monthlyChargeDao.deleteBeforeMonth(apartment.id, cutoff)
                                            historyError = null
                                        } catch (e: Exception) {
                                            historyError = "Ошибка очистки: ${e.localizedMessage ?: "попробуй ещё раз"}"
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                AppScreen.STATISTICS -> {
                    RootPlaceholderScreen(
                        title = "Статистика",
                        description = "Отчёты и аналитика появятся позже.",
                        icon = "▥"
                    )
                }

                AppScreen.ADD_APARTMENT -> {
                    ApartmentForm(
                        initial = apartments.find {
                            it.id == editingId
                        },
                        onBack = {
                            screen = if (editingId == -1L) {
                                AppScreen.APARTMENTS
                            } else {
                                AppScreen.DETAILS
                            }
                        },
                        onSave = { number, address, tenant ->
                            scope.launch {
                                // При редактировании сохраняем существующий templateId
                                // и другие поля сущности, не меняя их значения.
                                val existing = apartmentsFromDb.firstOrNull { it.id == editingId }
                                val entity = existing?.copy(
                                    number = number,
                                    address = address,
                                    tenant = tenant
                                ) ?: ApartmentEntity(
                                    id = if (editingId == -1L) 0L else editingId,
                                    number = number,
                                    address = address,
                                    tenant = tenant
                                )
                                val savedId = dao.insert(entity)

                                selectedId = savedId
                                editingId = -1L
                                screen = AppScreen.DETAILS
                            }
                        }
                    )
                }

                AppScreen.DETAILS -> {
                    if (selectedApartment == null) {
                        // После сохранения Room может ещё не прислать обновлённый список.
                        // Показываем индикатор, не сбрасывая навигацию на экран квартир.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentBlue)
                        }
                    } else {
                        // Последний сохранённый месяц для краткого списка показаний.
                        val latestMonth = savedCharges
                            .filter { !it.currentReading.isNullOrBlank() }
                            .maxOfOrNull { it.yearMonth }
                        val values = if (latestMonth == null) emptyList()
                            else persistedReadings(savedCharges, latestMonth)
                        val recentReadings = meterNames.mapIndexedNotNull { index, name ->
                            val current = values.getOrNull(index)?.current.orEmpty()
                            if (current.isBlank()) null else ApartmentMeterReadingUi(
                                meterName = name,
                                reading = "$current ${meterUnits[index]}",
                                period = latestMonth?.let(::monthTitle).orEmpty()
                            )
                        }

                        // Сохранённые месяцы — исторические снимки. Для текущего месяца
                        // показываем все услуги шаблона даже до ввода показаний.
                        val currentMonth = YearMonth.now().toString()
                        val savedMonths = savedCharges.groupBy { it.yearMonth }
                        // В карточке всегда только текущий и два предыдущих месяца.
                        // Остальные месяцы остаются в Room и открываются через Историю.
                        val visibleMonths = (0..2).map {
                            YearMonth.now().minusMonths(it.toLong()).toString()
                        }
                        val charges = visibleMonths.flatMap { month ->
                            val rows = savedMonths[month].orEmpty()
                            if (month == currentMonth && selectedTemplate != null && servicesResult.isSuccess) {
                                previewMonth(month, rows, activeServices)
                            } else {
                                rows.sortedWith(compareBy { row ->
                                    catalog?.services?.indexOfFirst { it.key == row.serviceKey }
                                        ?.takeIf { it >= 0 } ?: Int.MAX_VALUE
                                }).map(::chargeRowUi)
                            }
                        }

                        ApartmentDetails(
                            apartment = selectedApartment,
                            meterReadings = recentReadings,
                            serviceCharges = charges,
                            visibleMonthTitles = visibleMonths.map(::monthTitle),
                            templates = templateOptions,
                            templateError = templateError,
                            onTemplateSelected = { newId ->
                                if (newId != selectedApartment.templateId) {
                                    selectionError = null
                                    scope.launch {
                                        try {
                                            val currentEntity = apartmentsFromDb.firstOrNull {
                                                it.id == selectedApartment.id
                                            } ?: error("Квартира не найдена")
                                            val template = templatesFromDb.firstOrNull { it.id == newId }
                                            val mapped = if (template != null && catalog != null) {
                                                TemplateBilling.activeServices(template, catalog)
                                            } else emptyList()
                                            dao.insert(currentEntity.copy(templateId = newId))
                                            if (mapped.isNotEmpty()) {
                                                val corrections = TemplateBilling.fillMissingPrices(savedCharges, mapped)
                                                if (corrections.isNotEmpty()) monthlyChargeDao.insertAll(corrections)
                                            }
                                        } catch (e: Exception) {
                                            selectionError = "Не удалось выбрать шаблон: ${e.localizedMessage ?: "ошибка"}"
                                        }
                                    }
                                }
                            },
                            onBack = {
                                screen = AppScreen.APARTMENTS
                            },
                            onEdit = {
                                editingId = selectedApartment.id
                                screen = AppScreen.ADD_APARTMENT
                            },
                            onDelete = {
                                scope.launch {
                                    val apartmentId = selectedId
                                    monthlyChargeDao.deleteByApartment(apartmentId)
                                    dao.deleteById(apartmentId)
                                    readings = readings.filterKeys { it.first != apartmentId }
                                    selectedId = -1L
                                    screen = AppScreen.APARTMENTS
                                }
                            },
                            onMeters = {
                                screen = AppScreen.METERS
                            },
                            onHistory = {
                                historyBackScreen = AppScreen.DETAILS
                                historyError = null
                                screen = AppScreen.HISTORY_DETAILS
                            }
                        )
                    }
                }

                AppScreen.METERS -> {
                    if (selectedApartment == null) {
                        screen = AppScreen.APARTMENTS
                    } else {
                        val apartmentId = selectedId
                        val draftKey = apartmentId to selectedMonth
                        val values = readings[draftKey]
                            ?: persistedReadings(savedCharges, selectedMonth)
                        MeterScreen(
                            apartment = selectedApartment,
                            selectedMonth = selectedMonth,
                            values = values,
                            saveError = saveError,
                            saving = saving,
                            onMonthChange = { delta ->
                                selectedMonth = YearMonth.parse(selectedMonth).plusMonths(delta.toLong()).toString()
                                saveError = null
                            },
                            onBack = {
                                saveError = null
                                screen = AppScreen.DETAILS
                            },
                            onChange = { index, reading ->
                                val current = values.toMutableList()
                                current[index] = reading
                                readings = readings + (draftKey to current)
                                saveError = null
                            },
                            onSave = {
                                val message = checkReadings(values, allowEmpty = activeServices.any { it.isFixed })
                                if (message != null) {
                                    saveError = message
                                } else if (!saving) {
                                    saving = true
                                    val savingMonth = selectedMonth
                                    val savingValues = values.toList()
                                    scope.launch {
                                        try {
                                            val rows = chargesForMonth(
                                                apartmentId, savingMonth, savingValues, savedCharges, activeServices
                                            )
                                            val managed = meterKeys +
                                                (catalog?.services?.filter { it.billingType == "fixed" }
                                                    ?.map { it.key } ?: emptyList())
                                            monthlyChargeDao.replaceCalculatedMonth(
                                                apartmentId, savingMonth, managed.distinct(), rows
                                            )
                                            readings = readings - (apartmentId to savingMonth)
                                            saveError = null
                                            screen = AppScreen.DETAILS
                                        } catch (e: Exception) {
                                            saveError = "Не удалось сохранить начисления: ${e.localizedMessage ?: "ошибка базы данных"}"
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

            }
        }

        if (mainTab) {
            BottomNavigationBar(
                selectedScreen = screen,
                onSelect = { screen = it }
            )
        }
    }
}

@Composable
private fun ApartmentList(
    apartments: List<Apartment>,
    templates: List<ApartmentTemplateOptionUi>,
    onAdd: () -> Unit,
    onOpen: (Apartment) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Мои квартиры",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MainText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Всего квартир: ${apartments.size}",
                    fontSize = 16.sp,
                    color = SecondaryText
                )
            }

            Button(
                onClick = onAdd,
                modifier = Modifier.size(54.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(17.dp)
            ) {
                Text("＋", fontSize = 30.sp)
            }
        }

        Spacer(Modifier.height(22.dp))

        if (apartments.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⌂", fontSize = 58.sp, color = AccentBlue)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Пока нет квартир",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Добавь первую квартиру",
                        color = SecondaryText,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(apartments, key = { it.id }) { apartment ->
                    ApartmentCard(
                        apartment = apartment,
                        templateName = templates.firstOrNull { it.id == apartment.templateId }?.name,
                        onClick = { onOpen(apartment) }
                    )
                }
            }
        }

        PrimaryButton(
            text = "＋   Добавить квартиру",
            onClick = onAdd
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ApartmentCard(
    apartment: Apartment,
    templateName: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(110.dp)
                    .background(AccentBlue, RoundedCornerShape(5.dp))
            )

            Spacer(Modifier.width(13.dp))

            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        Color(0xFF183E70),
                        RoundedCornerShape(17.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("⌂", fontSize = 34.sp, color = Color.White)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "Квартира № ${apartment.number}",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MainText
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    apartment.address,
                    fontSize = 15.sp,
                    color = SecondaryText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Шаблон: " + (templateName ?: if (apartment.templateId == null) "Не выбран" else "Не найден"),
                    fontSize = 13.sp,
                    color = if (templateName == null) SecondaryText else AccentBlue
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    apartment.tenant.ifBlank { "Жилец не указан" },
                    fontSize = 15.sp,
                    color = MainText
                )
                Spacer(Modifier.height(9.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFF064D3C),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "Добавлена",
                            color = Color(0xFF70F0B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("›", fontSize = 30.sp, color = SecondaryText)
                }
            }
        }
    }
}

@Composable
private fun ApartmentDetails(
    apartment: Apartment,
    meterReadings: List<ApartmentMeterReadingUi>,
    serviceCharges: List<ApartmentServiceChargeUi>,
    visibleMonthTitles: List<String>,
    templates: List<ApartmentTemplateOptionUi>,
    templateError: String?,
    onTemplateSelected: (Long?) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMeters: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember {
        mutableStateOf(false)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = 12.dp),
                fontSize = 38.sp,
                color = MainText
            )

            Text(
                "Карточка квартиры",
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MainText
            )

            TextButton(onClick = onEdit) {
                Text("Изменить")
            }

            TextButton(onClick = { showDeleteDialog = true }) {
                Text("Удалить", color = Color(0xFFFF7777))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ApartmentCardSections(
                apartmentNumber = apartment.number,
                resident = apartment.tenant,
                meterReadings = meterReadings,
                serviceCharges = serviceCharges,
                visibleMonthTitles = visibleMonthTitles,
                templates = templates,
                selectedTemplateId = apartment.templateId,
                templateError = templateError,
                onTemplateSelected = onTemplateSelected,
                onEnterReadings = onMeters
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallActionCard("История", "◷", Modifier.weight(1f), onClick = onHistory)
                SmallActionCard("PDF-отчёт", "▤", Modifier.weight(1f)) {}
            }
            Text(
                "PDF-отчёт пока не реализован",
                color = SecondaryText,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp)) // отступ от нижней системной панели
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text("Удалить квартиру?")
            },
            text = {
                Text(
                    "Квартира № ${apartment.number} будет удалена из списка. " +
                            "Это действие нельзя отменить."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Удалить", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Отмена")
                }
            },
            containerColor = CardBackground,
            titleContentColor = MainText,
            textContentColor = SecondaryText
        )
    }
}

@Composable
private fun MeterScreen(
    apartment: Apartment,
    selectedMonth: String,
    values: List<MeterReading>,
    saveError: String?,
    saving: Boolean,
    onMonthChange: (Int) -> Unit,
    onBack: () -> Unit,
    onChange: (Int, MeterReading) -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Показания счётчиков", onBack)

        Text(
            "Квартира № ${apartment.number}",
            color = SecondaryText,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { onMonthChange(-1) }, enabled = !saving) {
                Text("‹", fontSize = 28.sp)
            }
            Text(
                monthTitle(selectedMonth).replaceFirstChar { it.uppercaseChar() },
                color = MainText,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            TextButton(onClick = { onMonthChange(1) }, enabled = !saving) {
                Text("›", fontSize = 28.sp)
            }
        }
        Text(
            "Выбери расчётный месяц и введи показания за него",
            color = SecondaryText,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(meterNames.size) { index ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardBackground
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, BorderColor
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        when (index) {
                                            0 -> Color(0xFF0879C9)
                                            1 -> Color(0xFFB52F43)
                                            2 -> Color(0xFFB77800)
                                            else -> Color(0xFF7135C7)
                                        },
                                        RoundedCornerShape(15.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(meterIcons[index], fontSize = 24.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    meterNames[index],
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Единица: ${meterUnits[index]}",
                                    color = SecondaryText,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        OutlinedInput(
                            label = "Предыдущее показание (${meterUnits[index]})",
                            value = values[index].previous,
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = {
                                onChange(index, values[index].copy(previous = it))
                            }
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedInput(
                            label = "Текущее показание (${meterUnits[index]})",
                            value = values[index].current,
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = {
                                onChange(index, values[index].copy(current = it))
                            }
                        )

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Расход за месяц: ${monthlyConsumption(values[index], meterUnits[index])}",
                            color = SecondaryText,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (saveError != null) {
            Text(
                saveError,
                color = Color(0xFFFF8888),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (saving) "Сохранение…" else "Сохранить за ${monthTitle(selectedMonth)}")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ApartmentForm(
    initial: Apartment?,
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var number by remember(initial?.id) { mutableStateOf(initial?.number.orEmpty()) }
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    var tenant by remember(initial?.id) { mutableStateOf(initial?.tenant.orEmpty()) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            if (initial == null) "Добавить квартиру" else "Редактировать квартиру",
            onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Данные квартиры",
                color = SecondaryText,
                fontSize = 16.sp
            )

            OutlinedInput("Номер квартиры *", number, { number = it })
            OutlinedInput("Адрес *", address, { address = it })
            OutlinedInput("Имя жильца / арендатора", tenant, { tenant = it })

            Text(
                "* Обязательные поля",
                color = SecondaryText,
                fontSize = 14.sp
            )
        }

        PrimaryButton(
            "Сохранить",
            onClick = {
                onSave(
                    number.trim(),
                    address.trim(),
                    tenant.trim(),

                )
            },
            enabled = number.isNotBlank() && address.isNotBlank()
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Отмена", onBack)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    description: String,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(subtitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text(description, color = SecondaryText, fontSize = 16.sp)
                Spacer(Modifier.height(18.dp))
                Text(
                    "Этот раздел добавим на следующем этапе.",
                    color = AccentBlue,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "‹",
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 14.dp),
            fontSize = 38.sp,
            color = MainText
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = MainText
        )
        if (onEdit != null) {
            TextButton(onClick = onEdit) {
                Text("Изменить", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 7.dp)) {
        Text(label, color = SecondaryText, fontSize = 14.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = MainText, fontSize = 17.sp)
    }
}

@Composable
private fun OutlinedInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(
            label,
            color = SecondaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FieldBackground,
                unfocusedContainerColor = FieldBackground,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = MainText,
                unfocusedTextColor = MainText,
                cursorColor = AccentBlue,
                focusedLabelColor = SecondaryText,
                unfocusedLabelColor = SecondaryText
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp,
                color = MainText
            )
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(
            text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MainText
        )
    ) {
        Text(text, fontSize = 16.sp)
    }
}

@Composable
private fun SmallActionCard(
    title: String,
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(symbol, fontSize = 28.sp, color = AccentBlue)
            Spacer(Modifier.height(7.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable
private fun BottomNavigationBar(
    selectedScreen: AppScreen,
    onSelect: (AppScreen) -> Unit
) {
    val items = listOf(
        Triple(AppScreen.APARTMENTS, "⌂", "Квартиры"),
        Triple(AppScreen.TEMPLATES, "▤", "Шаблоны"),
        Triple(AppScreen.HISTORY, "◷", "История"),
        Triple(AppScreen.STATISTICS, "▥", "Статистика")
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, BorderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (screen, icon, label) ->
                val selected = screen == selectedScreen

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) Color(0xFF183E70)
                            else Color.Transparent
                        )
                        .clickable { onSelect(screen) }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = icon,
                        fontSize = 24.sp,
                        color = if (selected) AccentBlue
                        else SecondaryText
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold
                        else FontWeight.Normal,
                        color = if (selected) MainText
                        else SecondaryText,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
@Composable
private fun RootPlaceholderScreen(
    title: String,
    description: String,
    icon: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MainText
        )

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardBackground
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, BorderColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = icon,
                    fontSize = 52.sp,
                    color = AccentBlue
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Раздел в разработке",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MainText
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = description,
                    fontSize = 15.sp,
                    color = SecondaryText
                )
            }
        }
    }
}
