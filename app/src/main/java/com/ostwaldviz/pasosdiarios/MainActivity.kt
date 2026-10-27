package com.ostwaldviz.pasosdiarios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDate
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

private val blockTimes = listOf(
    "8:00 a. m.",
    "9:30 a. m.",
    "11:00 a. m.",
    "12:30 p. m.",
    "2:00 p. m.",
    "3:30 p. m.",
    "5:00 p. m.",
    "6:00 p. m.",
    "7:00 p. m.",
    "8:00 p. m."
)

private val mexicanLocale = Locale("es", "MX")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val repository = StepPreferences(applicationContext)
        setContent { DailyTrackerApp(repository) }
    }
}

@Composable
private fun DailyTrackerApp(repository: StepPreferences) {
    var currentDay by remember { mutableStateOf(LocalDate.now()) }
    var checkedMask by remember { mutableIntStateOf(repository.load(currentDay)) }

    // When the calendar day changes, display the new day's independent progress.
    // It also covers the case where the app remains open across midnight.
    LaunchedEffect(repository) {
        while (true) {
            delay(15_000)
            val deviceDay = LocalDate.now()
            if (deviceDay != currentDay) {
                currentDay = deviceDay
                checkedMask = repository.load(deviceDay)
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Background,
            background = Background,
            onBackground = MainText,
            surface = CardBackground,
            onSurface = MainText
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            DailyHome(
                date = currentDay,
                checkedMask = checkedMask,
                onToggle = { index ->
                    val today = LocalDate.now()
                    val baseMask = if (today == currentDay) checkedMask else repository.load(today)
                    val updated = StepTracker.toggle(baseMask, index)
                    currentDay = today
                    checkedMask = updated
                    repository.save(today, updated)
                },
                onReset = {
                    val today = LocalDate.now()
                    currentDay = today
                    checkedMask = 0
                    repository.save(today, 0)
                }
            )
        }
    }
}

@Composable
private fun DailyHome(
    date: LocalDate,
    checkedMask: Int,
    onToggle: (Int) -> Unit,
    onReset: () -> Unit
) {
    val completed = StepTracker.completedCount(checkedMask)
    val totalSteps = StepTracker.steps(checkedMask)
    var showResetConfirmation by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(Background),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 38.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Header(date)
        }
        item {
            ProgressCard(completed = completed, totalSteps = totalSteps, mask = checkedMask)
        }
        if (completed == StepTracker.BLOCK_COUNT) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = TileCompleted,
                    border = BorderStroke(1.dp, Mint.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = Mint, modifier = Modifier.size(28.dp))
                        Column {
                            Text("¡Meta cumplida!", fontWeight = FontWeight.Bold, color = MainText)
                            Text("Completaste tus 10 caminatas de hoy.", fontSize = 13.sp, color = SecondaryText)
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TUS 10 BLOQUES",
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText
                )
                Text(
                    "Meta acumulada",
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }
        }
        items(blockTimes.size, key = { it }) { index ->
            SessionTile(
                index = index,
                time = blockTimes[index],
                completed = StepTracker.isCompleted(checkedMask, index),
                onToggle = { onToggle(index) }
            )
        }
        item {
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                enabled = completed > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(54.dp),
                border = BorderStroke(1.dp, BorderColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MainText,
                    disabledContentColor = SecondaryText.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Reiniciar progreso", fontWeight = FontWeight.SemiBold)
            }
        }
        item { TriSportNote() }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("¿Reiniciar el progreso?") },
            text = {
                Text("Se desmarcarán los 10 bloques de hoy. Tu progreso de otros días no se modificará.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetConfirmation = false
                }) { Text("Reiniciar", color = Mint) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancelar", color = SecondaryText)
                }
            },
            containerColor = CardBackground,
            titleContentColor = MainText,
            textContentColor = SecondaryText
        )
    }
}

@Composable
private fun Header(date: LocalDate) {
    val titleDate = date.format(
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", mexicanLocale)
    ).replaceFirstChar { it.uppercaseChar() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(TileCompleted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = Mint
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "PASOS DIARIOS",
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.Bold,
                color = Mint
            )
            Text(
                "10,000 pasos",
                fontSize = 29.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MainText
            )
            Text(titleDate, fontSize = 13.sp, color = SecondaryText)
        }
    }
}

@Composable
private fun ProgressCard(completed: Int, totalSteps: Int, mask: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "PASOS COMPLETADOS",
                        color = SecondaryText,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatNumber(totalSteps),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 46.sp,
                            color = MainText
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "/ 10,000",
                            fontSize = 14.sp,
                            color = SecondaryText,
                            modifier = Modifier.padding(bottom = 7.dp)
                        )
                    }
                }
                Text(
                    "${completed * 10}%",
                    color = if (completed == 10) Mint else MainText,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(StepTracker.BLOCK_COUNT) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .background(
                                color = if (StepTracker.isCompleted(mask, index)) Mint else BorderColor,
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$completed de 10 bloques", fontSize = 12.sp, color = SecondaryText)
                Text("${10 - completed} pendientes", fontSize = 12.sp, color = SecondaryText)
            }
        }
    }
}

@Composable
private fun SessionTile(index: Int, time: String, completed: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = completed, role = Role.Checkbox, onValueChange = { onToggle() }),
        shape = RoundedCornerShape(18.dp),
        color = if (completed) TileCompleted else TileBackground,
        border = BorderStroke(
            1.dp,
            if (completed) Mint.copy(alpha = 0.40f) else BorderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (completed) Mint else SecondaryText,
                modifier = Modifier.size(26.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    time,
                    color = if (completed) Mint else MainText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Bloque ${index + 1} · +1,000 pasos",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatNumber((index + 1) * 1_000),
                    color = MainText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (completed) "Completado" else "Pendiente",
                    color = if (completed) Mint else SecondaryText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun TriSportNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.Info, null, tint = SecondaryText, modifier = Modifier.size(19.dp))
        Text(
            "Consulta STEP en tu TriSport, no TOTAL STEP. Marca los bloques manualmente. Los horarios son sugeridos y la app inicia un registro nuevo cada día.",
            color = SecondaryText,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

private fun formatNumber(number: Int): String =
    NumberFormat.getIntegerInstance(mexicanLocale).format(number)
