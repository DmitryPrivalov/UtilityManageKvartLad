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
    val tenant: String
)

data class MeterReading(
    val current: String = "",
    val previous: String = ""
)

private enum class AppScreen {
    APARTMENTS,
    TEMPLATES,
    HISTORY,
    STATISTICS,
    ADD_APARTMENT,
    DETAILS,
    METERS,
    CHARGES
}

private val meterNames = listOf(
    "Холодная вода",
    "Электроэнергия",
    "Газ"
)

private val meterIcons = listOf("💧", "⚡", "🔥")
private val meterUnits = listOf("м³", "кВт·ч", "м³")

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
    val scope = rememberCoroutineScope()

    val apartmentsFromDb by dao.getAll()
        .collectAsState(initial = emptyList())

    val apartments = apartmentsFromDb.map { entity ->
        Apartment(
            id = entity.id,
            number = entity.number,
            address = entity.address,
            tenant = entity.tenant
        )
    }

    var screen by remember { mutableStateOf(AppScreen.APARTMENTS) }
    var selectedId by remember { mutableStateOf(-1L) }
    var editingId by remember { mutableStateOf(-1L) }

    var readings by remember {
        mutableStateOf<Map<Long, List<MeterReading>>>(emptyMap())
    }

    val selectedApartment = apartments.find { it.id == selectedId }

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
                        onAdd = {
                            editingId = -1L
                            screen = AppScreen.ADD_APARTMENT
                        },
                        onOpen = {
                            selectedId = it.id
                            screen = AppScreen.DETAILS
                        }
                    )
                }


                AppScreen.TEMPLATES -> TemplateScreen(
                    dao = templateDao
                )


                AppScreen.HISTORY -> {
                    RootPlaceholderScreen(
                        title = "История",
                        description = "История показаний и начислений появится позже.",
                        icon = "◷"
                    )
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
                                val savedId = dao.insert(
                                    ApartmentEntity(
                                        id = if (editingId == -1L) {
                                            0L
                                        } else {
                                            editingId
                                        },
                                        number = number,
                                        address = address,
                                        tenant = tenant
                                    )
                                )

                                selectedId = savedId
                                editingId = -1L
                                screen = AppScreen.DETAILS
                            }
                        }
                    )
                }

                AppScreen.DETAILS -> {
                    if (selectedApartment == null) {
                        screen = AppScreen.APARTMENTS
                    } else {
                        ApartmentDetails(
                            apartment = selectedApartment,
                            onBack = {
                                screen = AppScreen.APARTMENTS
                            },
                            onEdit = {
                                editingId = selectedApartment.id
                                screen = AppScreen.ADD_APARTMENT
                            },
                            onDelete = {
                                scope.launch {
                                    dao.deleteById(selectedId)
                                    readings = readings - selectedId
                                    selectedId = -1L
                                    screen = AppScreen.APARTMENTS
                                }
                            },
                            onMeters = {
                                screen = AppScreen.METERS
                            },
                            onCharges = {
                                screen = AppScreen.CHARGES
                            }
                        )
                    }
                }

                AppScreen.METERS -> {
                    if (selectedApartment == null) {
                        screen = AppScreen.APARTMENTS
                    } else {
                        MeterScreen(
                            apartment = selectedApartment,
                            values = readings[selectedId]
                                ?: List(4) { MeterReading() },
                            onBack = {
                                screen = AppScreen.DETAILS
                            },
                            onChange = { index, reading ->
                                val current = (
                                        readings[selectedId]
                                            ?: List(4) { MeterReading() }
                                        ).toMutableList()

                                current[index] = reading
                                readings = readings + (
                                        selectedId to current
                                        )
                            }
                        )
                    }
                }

                AppScreen.CHARGES -> {
                    PlaceholderScreen(
                        title = "Начисления",
                        subtitle = "Квартира № ${selectedApartment?.number.orEmpty()}",
                        description = "Здесь появятся коммунальные услуги, тарифы и итоговая сумма.",
                        onBack = {
                            screen = AppScreen.DETAILS
                        }
                    )
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
                    ApartmentCard(apartment, onClick = { onOpen(apartment) })
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
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMeters: () -> Unit,
    onCharges: () -> Unit,
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
                "Квартира № ${apartment.number}",
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
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        apartment.address,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Жилец", color = SecondaryText, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                apartment.tenant.ifBlank { "Не указан" },
                                fontSize = 16.sp
                            )


                        }

                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = FieldBackground)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("К оплате", fontSize = 16.sp, color = SecondaryText)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "—",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MainText
                    )
                    Text(
                        "Расчёты пока не подключены",
                        fontSize = 14.sp,
                        color = SecondaryText
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMeters),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF153C73)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    AccentBlue
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("◉", fontSize = 32.sp, color = Color.White)

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "ВВЕСТИ ПОКАЗАНИЯ СЧЁТЧИКОВ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "Вода · Электричество · Газ",
                        fontSize = 14.sp,
                        color = Color(0xFFD4E4FF)
                    )
                }
            }

            SecondaryButton("Начисления и услуги", onCharges)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallActionCard("История", "◷", Modifier.weight(1f)) {}
                SmallActionCard("PDF-отчёт", "▤", Modifier.weight(1f)) {}
            }
            Text(
                "История и PDF пока не реализованы",
                color = SecondaryText,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
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
    values: List<MeterReading>,
    onBack: () -> Unit,
    onChange: (Int, MeterReading) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Показания счётчиков", onBack)

        Text(
            "Квартира № ${apartment.number}",
            color = SecondaryText,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(14.dp))

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
                            "Расчёт расхода пока отключён",
                            color = SecondaryText,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Text(
            "Показания хранятся временно до подключения базы данных.",
            color = SecondaryText,
            fontSize = 13.sp
        )
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