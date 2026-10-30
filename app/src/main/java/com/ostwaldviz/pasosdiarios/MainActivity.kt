package com.ostwaldviz.pasosdiarios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Background = Color(0xFF0B1115)
private val CardBackground = Color(0xFF162128)
private val TileBackground = Color(0xFF111B21)
private val TileCompleted = Color(0xFF143129)
private val Mint = Color(0xFF42D69A)
private val MainText = Color(0xFFF4F8F7)
private val SecondaryText = Color(0xFF9CABAF)
private val BorderColor = Color(0xFF273841)
private val mexicanLocale = Locale("es", "MX")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val repository = DailyRepository(StepPreferences(applicationContext))
        setContent { DailyTrackerApp(repository, lifecycle) }
    }
}

@Composable
private fun DailyTrackerApp(repository: DailyRepository, lifecycle: Lifecycle) {
    var current by remember { mutableStateOf(repository.current()) }
    var history by remember { mutableStateOf(repository.history()) }
    var tab by rememberSaveable { mutableStateOf("today") }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }

    fun update(record: DailyProgress) {
        current = record
        history = repository.history()
    }

    // A closed app needs no midnight job: every tap is already persisted under its date.
    // Refresh immediately on returning from sleep/background, including clock/time-zone changes.
    DisposableEffect(lifecycle, repository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) update(repository.current())
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(repository) {
        while (true) {
            val record = repository.current()
            if (record != current) update(record)
            val now = ZonedDateTime.now()
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, midnight).toMillis().coerceIn(1L, 60_000L))
        }
    }

    BackHandler(enabled = selectedDate != null || tab != "today") {
        if (selectedDate != null) selectedDate = null else tab = "today"
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, onPrimary = Background, background = Background,
        secondary = Mint, onSecondary = Background,
        secondaryContainer = TileCompleted, onSecondaryContainer = Mint,
        onBackground = MainText, surface = CardBackground, onSurface = MainText,
        surfaceVariant = TileBackground, onSurfaceVariant = SecondaryText
    )) {
        Scaffold(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            containerColor = Background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = CardBackground, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    NavigationBarItem(
                        selected = tab == "today", onClick = { tab = "today"; selectedDate = null },
                        icon = { Icon(Icons.Outlined.DirectionsWalk, null) }, label = { Text("Hoy") }
                    )
                    NavigationBarItem(
                        selected = tab == "history", onClick = {
                            update(repository.current()); tab = "history"; selectedDate = null
                        },
                        icon = { Icon(Icons.Outlined.History, null) }, label = { Text("Historial") }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val selected = history.find { it.date.toString() == selectedDate }
                when {
                    tab == "today" -> key(current.date) {
                        val displayed = current
                        DailyHome(
                            record = displayed,
                            onToggle = { update(repository.toggle(displayed.date, it)) },
                            onReset = { update(repository.reset(displayed.date)) },
                            onPlanChange = { update(repository.changePlan(displayed.date, it)) }
                        )
                    }
                    selected != null -> HistoryDetail(selected, current.date) { selectedDate = null }
                    else -> HistoryScreen(history, current.date) { selectedDate = it.date.toString() }
                }
            }
        }
    }
}

@Composable
private fun DailyHome(
    record: DailyProgress,
    onToggle: (Int) -> Unit,
    onReset: () -> Unit,
    onPlanChange: (StepPlan) -> Unit
) {
    var resetDialog by rememberSaveable { mutableStateOf(false) }
    var planDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("daily-list"),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Header(record) }
        item { ProgressCard(record) }
        item {
            OutlinedButton(onClick = { planDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Ajustar bloques y pasos")
            }
        }
        if (record.completedCount == record.plan.blockCount) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = TileCompleted) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = Mint)
                        Column {
                            Text("¡Meta cumplida!", fontWeight = FontWeight.Bold)
                            Text("Completaste tus ${record.plan.blockCount} bloques de hoy.",
                                fontSize = 13.sp, color = SecondaryText)
                        }
                    }
                }
            }
        }
        item { BlockHeading(record.plan.blockCount) }
        items(record.plan.blockCount, key = { it }) { index ->
            SessionTile(record, index) { onToggle(index) }
        }
        item {
            OutlinedButton(
                onClick = { resetDialog = true }, enabled = record.completedCount > 0,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor)
            ) {
                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Reiniciar progreso de hoy")
            }
        }
        item {
            Text(
                "Consulta STEP en tu TriSport, no TOTAL STEP. Marca los bloques manualmente. " +
                    "Los horarios son sugeridos. Tu progreso se guarda al marcar y a las 00:00 comienza otro día.",
                color = SecondaryText, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
    if (planDialog) PlanDialog(record, onDismiss = { planDialog = false }) {
        onPlanChange(it); planDialog = false
    }
    if (resetDialog) AlertDialog(
        onDismissRequest = { resetDialog = false },
        containerColor = CardBackground,
        title = { Text("¿Reiniciar el progreso?") },
        text = { Text("Se desmarcarán los bloques de hoy. Los días anteriores conservarán su progreso.") },
        confirmButton = { TextButton(onClick = { onReset(); resetDialog = false }) { Text("Reiniciar") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun Header(record: DailyProgress) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(54.dp).background(TileCompleted, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.DirectionsWalk, null, Modifier.size(30.dp), tint = Mint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("PASOS DIARIOS", fontSize = 11.sp, letterSpacing = 1.8.sp,
                fontWeight = FontWeight.Bold, color = Mint)
            Text("${formatNumber(record.plan.targetSteps)} pasos", fontSize = 29.sp,
                lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text(dateLabel(record.date), fontSize = 13.sp, color = SecondaryText)
        }
    }
}

@Composable
private fun ProgressCard(record: DailyProgress) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PASOS COMPLETADOS", color = SecondaryText, fontSize = 11.sp,
                letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatNumber(record.totalSteps), modifier = Modifier.weight(1f).testTag("total-steps"), fontSize = 38.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${record.percent}%", fontSize = 23.sp, fontWeight = FontWeight.Bold,
                    color = if (record.percent == 100) Mint else MainText)
            }
            Text("Meta: ${formatNumber(record.plan.targetSteps)} pasos", fontSize = 13.sp, color = SecondaryText)
            val columns = minOf(record.plan.blockCount, 10)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat((record.plan.blockCount + columns - 1) / columns) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(columns) { column ->
                            val index = row * columns + column
                            if (index < record.plan.blockCount) {
                                Box(Modifier.weight(1f).height(12.dp).background(
                                    if (index in record.completed) Mint else BorderColor, RoundedCornerShape(5.dp)))
                            } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Text("${record.completedCount} de ${record.plan.blockCount} bloques · " +
                "${record.plan.blockCount - record.completedCount} pendientes", fontSize = 12.sp, color = SecondaryText)
        }
    }
}

@Composable
private fun BlockHeading(count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text("TUS $count BLOQUES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SecondaryText)
        Text("Meta acumulada", fontSize = 12.sp, color = SecondaryText)
    }
}

@Composable
private fun SessionTile(record: DailyProgress, index: Int, onToggle: (() -> Unit)? = null) {
    val completed = index in record.completed
    val interaction = if (onToggle != null) {
        Modifier.toggleable(completed, role = Role.Checkbox, onValueChange = { onToggle() })
    } else Modifier.semantics { stateDescription = if (completed) "Completado" else "Pendiente" }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("block-$index").then(interaction), shape = RoundedCornerShape(18.dp),
        color = if (completed) TileCompleted else TileBackground,
        border = BorderStroke(1.dp, if (completed) Mint.copy(alpha = 0.40f) else BorderColor)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (completed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                null, Modifier.size(26.dp), tint = if (completed) Mint else SecondaryText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.plan.suggestedTime(index).format(DateTimeFormatter.ofPattern("h:mm a", mexicanLocale)),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (completed) Mint else MainText)
                Text("Bloque ${index + 1} · +${formatNumber(record.plan.stepsForBlock(index))} pasos",
                    fontSize = 12.sp, color = SecondaryText)
            }
            Column(Modifier.widthIn(max = 120.dp), horizontalAlignment = Alignment.End) {
                Text(formatNumber(record.plan.cumulativeSteps(index)), fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (completed) "Completado" else "Pendiente", fontSize = 11.sp,
                    color = if (completed) Mint else SecondaryText)
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<DailyProgress>, today: LocalDate, onSelect: (DailyProgress) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Historial", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Cada día conserva sus bloques, pasos y meta. A las 00:00 comienza un nuevo registro.",
                    color = SecondaryText, fontSize = 14.sp)
                Text("${history.size} días registrados · Del más reciente al más antiguo",
                    color = SecondaryText, fontSize = 12.sp)
            }
        }
        items(history, key = { it.date.toString() }) { record ->
            Card(onClick = { onSelect(record) }, shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(dateLabel(record.date), fontWeight = FontWeight.SemiBold)
                    Text(if (record.date == today) "Hoy · En curso" else "Día guardado",
                        color = if (record.date == today) Mint else SecondaryText, fontSize = 12.sp)
                    Text("${formatNumber(record.totalSteps)} / ${formatNumber(record.plan.targetSteps)} pasos",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { record.progress }, modifier = Modifier.fillMaxWidth(),
                        color = Mint, trackColor = BorderColor)
                    Text("${record.completedCount} de ${record.plan.blockCount} bloques · " +
                        "${formatStepsPerBlock(record.plan)} pasos por bloque · ${record.percent}%",
                        fontSize = 12.sp, color = SecondaryText)
                    Text("Ver bloques ›", color = Mint, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HistoryDetail(record: DailyProgress, today: LocalDate, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("Volver al historial")
            }
        }
        item { Text(dateLabel(record.date), fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item {
            Text(if (record.date == today) "En curso. Puedes marcar tus bloques en Hoy."
                else "Registro guardado · Solo consulta", color = SecondaryText, fontSize = 13.sp)
        }
        item { ProgressCard(record) }
        item { BlockHeading(record.plan.blockCount) }
        items(record.plan.blockCount, key = { it }) { index -> SessionTile(record, index) }
    }
}

@Composable
private fun PlanDialog(record: DailyProgress, onDismiss: () -> Unit, onSave: (StepPlan) -> Unit) {
    var target by rememberSaveable { mutableStateOf(record.plan.targetSteps.toString()) }
    var blocks by rememberSaveable { mutableStateOf(record.plan.blockCount.toString()) }
    var steps by rememberSaveable { mutableStateOf(record.plan.stepsPerBlock.toString()) }
    var extraSteps by rememberSaveable { mutableIntStateOf(record.plan.extraSteps) }

    // Total is the primary input. Changing the block count redistributes the same goal.
    fun changeTarget(value: String) {
        target = value
        val count = blocks.toIntOrNull()
        val goal = value.toIntOrNull()
        val next = if (count != null && goal != null)
            runCatching { StepPlan.forTarget(goal, count) }.getOrNull() else null
        if (next != null) {
            steps = next.stepsPerBlock.toString()
            extraSteps = next.extraSteps
        }
    }

    fun changeBlocks(value: String) {
        blocks = value
        val count = value.toIntOrNull()
        val goal = target.toIntOrNull()
        val next = if (count != null && goal != null)
            runCatching { StepPlan.forTarget(goal, count) }.getOrNull() else null
        if (next != null) {
            steps = next.stepsPerBlock.toString()
            extraSteps = next.extraSteps
        }
    }

    // Changing steps per block is intentionally different: it updates the goal.
    fun changeSteps(value: String) {
        steps = value
        val count = blocks.toIntOrNull()
        val perBlock = value.toIntOrNull()
        val next = if (count != null && perBlock != null)
            runCatching { StepPlan(count, perBlock) }.getOrNull() else null
        if (next != null) {
            target = next.targetSteps.toString()
            extraSteps = 0
        }
    }

    val plan = runCatching {
        StepPlan(blocks.toInt(), steps.toInt(), extraSteps).also {
            require(it.targetSteps == target.toInt())
        }
    }.getOrNull()
    val minimumTarget = blocks.toIntOrNull()?.takeIf { it in 1..StepPlan.MAX_BLOCKS } ?: 1
    val maximumPerBlock = StepPlan.MAX_TOTAL_STEPS / minimumTarget

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text("Tu meta diaria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Cambia tu meta o la cantidad de bloques y los pasos se repartirán automáticamente. Si cambias los pasos por bloque, se actualizará la meta.")
                NumberSetting("Meta diaria (pasos)", "target-steps", target,
                    StepPlan.MAX_TOTAL_STEPS, 1_000, minimumTarget) { changeTarget(it) }
                NumberSetting("Cantidad de bloques", "block-count", blocks,
                    StepPlan.MAX_BLOCKS, 1) { changeBlocks(it) }
                NumberSetting("Pasos por bloque", "steps-per-block", steps,
                    maximumPerBlock, 100) { changeSteps(it) }
                if (plan != null && plan.extraSteps > 0) {
                    Text(
                        "Para alcanzar la meta exacta: ${plan.extraSteps} bloques de " +
                            "${formatNumber(plan.stepsPerBlock + 1)} pasos y " +
                            "${plan.blockCount - plan.extraSteps} de ${formatNumber(plan.stepsPerBlock)}.",
                        color = SecondaryText, fontSize = 12.sp
                    )
                }
                Surface(color = TileCompleted, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("META TOTAL", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(plan?.let { "${formatNumber(it.targetSteps)} pasos" } ?: "Revisa los valores",
                            fontWeight = FontWeight.Bold, fontSize = 23.sp)
                    }
                }
                if (plan == null) Text(
                    "La meta debe ser válida y alcanzar para dar al menos un paso por bloque.",
                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp
                )
                Text("Se aplicará a hoy y a los próximos días. Los días anteriores conservarán su meta.", fontSize = 13.sp)
                if (plan != null && plan != record.plan) {
                    val remaining = record.withPlan(plan)
                    val removed = record.completedCount - remaining.completedCount
                    if (removed > 0) Text(if (removed == 1) "Al reducir la cantidad se quitará 1 bloque marcado."
                        else "Al reducir la cantidad se quitarán $removed bloques marcados.",
                        color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    if (record.completedCount > 0) Text(
                        "Con este cambio, el progreso de hoy será de ${formatNumber(remaining.totalSteps)} pasos " +
                            "(${remaining.completedCount} bloques marcados).", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { plan?.let(onSave) }, enabled = plan != null) { Text("Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun NumberSetting(
    label: String,
    tag: String,
    value: String,
    maximum: Int,
    increment: Int,
    minimum: Int = 1,
    onChange: (String) -> Unit
) {
    val number = value.toIntOrNull()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(((number ?: minimum) - increment).coerceIn(minimum, maximum).toString()) },
                enabled = number == null || number > minimum) {
                Icon(Icons.Outlined.Remove, contentDescription = "Reducir $label")
            }
            OutlinedTextField(
                value = value,
                onValueChange = {
                    if (it.length <= maximum.toString().length && it.all(Char::isDigit)) onChange(it)
                },
                modifier = Modifier.weight(1f).testTag(tag), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }),
                isError = number == null || number !in minimum..maximum,
                label = { Text(label) }
            )
            IconButton(onClick = { onChange(((number ?: minimum) + increment).coerceIn(minimum, maximum).toString()) },
                enabled = number == null || number < maximum) {
                Icon(Icons.Outlined.Add, contentDescription = "Aumentar $label")
            }
        }
        Text("De ${formatNumber(minimum)} a ${formatNumber(maximum)}", fontSize = 11.sp, color = SecondaryText)
    }
}

private fun dateLabel(date: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", mexicanLocale)
).replaceFirstChar { it.uppercaseChar() }

private fun formatStepsPerBlock(plan: StepPlan): String =
    if (plan.extraSteps == 0) formatNumber(plan.stepsPerBlock)
    else "${formatNumber(plan.stepsPerBlock)}–${formatNumber(plan.stepsPerBlock + 1)}"

private fun formatNumber(number: Int): String = NumberFormat.getIntegerInstance(mexicanLocale).format(number)
