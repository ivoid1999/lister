package com.zirohill.lister

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.YearMonth
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.AlertDialog
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.*
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.sqrt
import kotlin.math.pow

enum class CalendarMode { FULL_WEEK, WEEKDAYS }

class ChainViewModel(private val context: Context) : ViewModel() {
    private val dbHelper = DbHelper(context.applicationContext)

    private val _chainEntries = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val chainEntries: StateFlow<Map<String, Boolean>> = _chainEntries

    private val _goalText = MutableStateFlow("Start your new habit")
    val goalText: StateFlow<String> = _goalText

    private val _calendarMode = MutableStateFlow(CalendarMode.WEEKDAYS)
    val calendarMode: StateFlow<CalendarMode> = _calendarMode

    init {
        viewModelScope.launch {
            _chainEntries.value = dbHelper.getChainEntries()
            _goalText.value = dbHelper.loadGoal()
        }
    }

    fun toggleCalendarMode() {
        val newMode = if (_calendarMode.value == CalendarMode.WEEKDAYS)
            CalendarMode.FULL_WEEK else CalendarMode.WEEKDAYS
        _calendarMode.value = newMode
    }

    fun markToday(done: Boolean) {
        val today = LocalDate.now().toString()
        viewModelScope.launch {
            dbHelper.setChainEntry(today, done)
            _chainEntries.value = dbHelper.getChainEntries()
        }
    }

    fun resetStreak() {
        viewModelScope.launch {
            dbHelper.clearChainEntries()  // You must implement this in your DbHelper
            _chainEntries.value = emptyMap()
            _chainEntries.value = dbHelper.getChainEntries()
        }
    }

    fun startNewGoal(text: String) {
        val sanitized = text.trim().takeIf { it.isNotEmpty() } ?: "Start your new habit"
        viewModelScope.launch {
            dbHelper.saveGoal(sanitized)
            dbHelper.clearChainEntries()
            _goalText.value = sanitized
            _chainEntries.value = emptyMap()
            _chainEntries.value = dbHelper.getChainEntries()
        }
    }

    fun setCalendarMode(mode: CalendarMode) {
        _calendarMode.value = mode
    }
}

class ChainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (modelClass.isAssignableFrom(ChainViewModel::class.java))
            ChainViewModel(context) as T
        else throw IllegalArgumentException("Unknown ViewModel class")
}

@Composable
fun ChainPageTopBar(
    onBackClick: () -> Unit,
    onStartNewGoal: () -> Unit,
    onResetStreak: () -> Unit,
    calendarMode: CalendarMode,
    onToggleCalendarMode: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
        Spacer(Modifier.height(3.dp))
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            backgroundColor = MaterialTheme.colors.background,
            elevation = 0.dp,
            title = { Text("Habit", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.Black) },
            actions = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(onClick = {
                        menuExpanded = false
                        onStartNewGoal()
                    }) {
                        Text("Start my new goal")
                    }
                }
            }
        )

        Spacer(Modifier.height(5.dp))
        Divider(color = Color.LightGray, thickness = 0.5.dp)
    }
}

@Composable
fun ChainCalendarScreen(chainVm: ChainViewModel, navController: NavController) {
    val calendarModeState by chainVm.calendarMode.collectAsState()
    var showGoalInput by remember { mutableStateOf(false) }
    var inputGoal by remember { mutableStateOf("") }
    var inputCalendarMode by remember { mutableStateOf(calendarModeState) }

    Scaffold(
        topBar = {
            ChainPageTopBar(
                onBackClick = { navController.popBackStack() },
                onStartNewGoal = {
                    inputGoal = ""           // reset input on new dialog open
                    inputCalendarMode = calendarModeState
                    showGoalInput = true
                },
                onResetStreak = { chainVm.resetStreak() },
                calendarMode = calendarModeState,
                onToggleCalendarMode = { chainVm.toggleCalendarMode() }
            )
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            ChainScreen(vm = chainVm)
            if (showGoalInput) {
                GoalEditDialog(
                    goalText = inputGoal,
                    onValueChange = { inputGoal = it },
                    calendarMode = inputCalendarMode,
                    onCalendarModeChange = { inputCalendarMode = it },
                    onConfirm = {
                        chainVm.startNewGoal(inputGoal)
                        chainVm.setCalendarMode(inputCalendarMode)
                        showGoalInput = false
                    },
                    onDismiss = { showGoalInput = false }
                )
            }
        }
    }
}

@Composable
fun FooterTip() {
    val PaleGray = Color(0xFFCACACA)
    Text(
        "Tap circle to mark your today's progress.",
        fontSize = 10.sp,
        color = PaleGray,
        modifier = Modifier.padding(horizontal = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
fun GoalEditDialog(
    goalText: String,
    onValueChange: (String) -> Unit,
    calendarMode: CalendarMode,
    onCalendarModeChange: (CalendarMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(bottom = 8.dp),
        text = {
            Column {
                Text(
                    "Decide what to do", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray, modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = goalText,
                    onValueChange = onValueChange,
                    label = { Text("Goal description", color = Color.LightGray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.Black),
                )

                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onCalendarModeChange(CalendarMode.FULL_WEEK) }
                            .padding(4.dp)
                    ) {
                        RadioButton(
                            selected = calendarMode == CalendarMode.FULL_WEEK,
                            onClick = null
                        )
                        Text(
                            "Full week",
                            modifier = Modifier.padding(start = 4.dp),
                            fontSize = 14.sp,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onCalendarModeChange(CalendarMode.WEEKDAYS) }
                            .padding(4.dp)
                    ) {
                        RadioButton(
                            selected = calendarMode == CalendarMode.WEEKDAYS,
                            onClick = null
                        )
                        Text(
                            "Only weekdays",
                            modifier = Modifier.padding(start = 4.dp),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = goalText.trim().isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(bottom = 0.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

fun generateMonthlyDates(startDate: String): List<String> {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val date = LocalDate.parse(startDate, formatter)
    val yearMonth = YearMonth.of(date.year, date.month)
    return (1..yearMonth.lengthOfMonth()).map {
        yearMonth.atDay(it).format(formatter)
    }
}

fun filterWeekends(dates: List<String>, skipWeekends: Boolean): List<String> {
    if (!skipWeekends) return dates
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    return dates.filter {
        val dayOfWeek = LocalDate.parse(it, formatter).dayOfWeek.value
        dayOfWeek in 1..5  // Monday=1 to Friday=5
    }
}

fun getLogStatusMessage(
    completedDays: Set<String>,
    todayDate: String,
    skipWeekends: Boolean,
): String {
    val today = LocalDate.parse(todayDate, DateTimeFormatter.ISO_LOCAL_DATE)
    if (skipWeekends && (today.dayOfWeek.value == 6 || today.dayOfWeek.value == 7)) {
        return "No goal scheduled: Today"
    }
    return if (completedDays.contains(todayDate)) {
        "Done!"
    } else {
        "Not Entered: Today"
    }
}

@Composable
fun GoalProgressCircle(
    startDate: String,
    completedDays: Set<String>,
    goalText: String,
    skipWeekends: Boolean,
    onMarkDay: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    circleDiameter: Dp = 300.dp,
    circleStrokeWidth: Dp = 12.dp,
) {
    val allDates = generateMonthlyDates(startDate)
    val filteredDates = filterWeekends(allDates, skipWeekends)
    val totalDays = filteredDates.size.coerceAtLeast(1)
    val completedCount = completedDays.count { filteredDates.contains(it) }.coerceAtMost(totalDays)

    val progress = completedCount.toFloat() / totalDays.toFloat()
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f))

    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val isTodayCompleted = completedDays.contains(today) && filteredDates.contains(today)

    val density = LocalDensity.current
    val circleRadiusPx = with(density) { (circleDiameter / 2).toPx() }
    val statusMessage = getLogStatusMessage(completedDays, today, skipWeekends)
    val isGoalEnable = goalText != "Start your new habit"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(circleDiameter)
            .offset(y = (-50).dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val center = this.size.center
                    val distanceFromCenter = sqrt(
                        (offset.x - center.x).pow(2) + (offset.y - center.y).pow(2)
                    )
                    if (distanceFromCenter <= circleRadiusPx) {
                        if (filteredDates.contains(today) && isGoalEnable) {
                            onMarkDay(today)
                        }
                    }
                }
            }
    ) {
        CircularProgressIndicator(
            progress = animatedProgress,
            strokeWidth = circleStrokeWidth,
            color = if (isTodayCompleted) MaterialTheme.colors.primary else Color.Gray,
            backgroundColor = Color.LightGray.copy(alpha = 0.3f),
            modifier = Modifier.size(circleDiameter)
        )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(circleDiameter)
                    .padding(30.dp)
            ) {
                Text(
                    text = goalText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )
                if (isGoalEnable) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$completedCount / $totalDays",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )
                }
        }
    }
}

@Composable
fun ChainScreen(
    vm: ChainViewModel,
) {
    val goalText by vm.goalText.collectAsState()
    val entries by vm.chainEntries.collectAsState()
    val calendarMode by vm.calendarMode.collectAsState()
    val startDate = LocalDate.now().withDayOfMonth(1).toString()
    val skipWeekends = calendarMode == CalendarMode.WEEKDAYS
    val completedDays = entries.filter { it.value }.keys.toSet()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GoalProgressCircle(
            startDate = startDate,
            completedDays = completedDays,
            goalText = goalText,
            skipWeekends = skipWeekends,
            circleDiameter = 320.dp,
            circleStrokeWidth = 12.dp,
            onMarkDay = { date ->
                val currentlyDone = entries[date] == true
                if (currentlyDone) {
                    vm.markToday(false)

                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Are you sure to delete progress?",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            vm.markToday(true)
                        }
                    }
                } else {
                    vm.markToday(true)
                }
            }
        )

        Spacer(Modifier.height(30.dp))
        FooterTip()
    }

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
