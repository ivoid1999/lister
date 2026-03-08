package com.zirohill.lister

import android.app.*
import android.content.*
import android.widget.TimePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import androidx.navigation.compose.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.navigation.NavController

@Composable
fun MoveCategoryDialog(
    categories: List<Category>,
    currentCategory: Category,
    onDismissRequest: () -> Unit,
    onCategorySelected: (Category?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Move task to") },
        text = {
            Column {
                categories
                    .filter { it.id != currentCategory.id }
                    .forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCategorySelected(cat) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.name, fontSize = 16.sp)
                        }
                    }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategorySelected(null) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cancel", fontSize = 16.sp)
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun TodoRow(
    item: TodoItem,
    categories: List<Category>,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onToggleDone: (TodoItem) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Category) -> Unit,
    vm: TodoViewModel,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val currentCategory = categories.find { it.id == item.categoryId }
        ?: Category(id = 0L, name = "Unknown")
    val taskFontSize = 18
    val taskDropMenuHeight = 4
    val taskDropMenuHeightIn = 36

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .pointerInput(item.id) { detectTapGestures(onLongPress = { onShowMenuChange(true) }) }
        ) {
            Text(
                text = item.task,
                modifier = Modifier.weight(1f),
                fontSize = taskFontSize.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (taskFontSize + 6).sp,
                style = MaterialTheme.typography.body1.copy(
                    textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                    color = if (item.isDone) Color.LightGray else Color.Black
                )
            )
        }

        Divider(color = Color.LightGray, thickness = 0.5.dp)

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onShowMenuChange(false) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEdit()
                        onShowMenuChange(false)
                    }
                    .padding(vertical = taskDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = taskDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Edit Task")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showMoveDialog = true
                        onShowMenuChange(false)
                    }
                    .padding(vertical = taskDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = taskDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Move Task")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onToggleDone(item.copy(isDone = !item.isDone))
                        onShowMenuChange(false)
                    }
                    .padding(vertical = taskDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = taskDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(if (item.isDone) "Mark Incomplete" else "Mark Complete")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canMoveUp) {
                        vm.moveTaskUp(item)
                        onShowMenuChange(false)
                    }
                    .padding(vertical = taskDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = taskDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Move Up", color = if (canMoveUp) Color.Unspecified else Color.LightGray)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canMoveDown) {
                        vm.moveTaskDown(item)
                        onShowMenuChange(false)
                    }
                    .padding(vertical = taskDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = taskDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Move Down", color = if (canMoveDown) Color.Unspecified else Color.LightGray)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showDeleteConfirmDialog = true
                        onShowMenuChange(false)
                    }
                    .padding(vertical = taskDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = taskDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Delete Task")
            }
        }


        if (showMoveDialog) {
            MoveCategoryDialog(
                categories = categories,
                currentCategory = currentCategory,
                onDismissRequest = { showMoveDialog = false },
                onCategorySelected = { newCategory ->
                    if (newCategory != null) onMove(newCategory)
                    showMoveDialog = false
                }
            )
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete Task") },
                text = { Text("Are you sure to delete this task?") },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete()
                        showDeleteConfirmDialog = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun CommonColoredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = singleLine,
        modifier = modifier,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.Black,
            cursorColor = Color.Black,
            focusedBorderColor = Color.Black,
            unfocusedBorderColor = Color.Black.copy(alpha = 0.5f),
            focusedLabelColor = Color.Black,
            unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
            placeholderColor = Color.Black.copy(alpha = 0.5f),
            backgroundColor = Color.Transparent
        ),
        textStyle = MaterialTheme.typography.body1.copy(fontSize = 18.sp)
    )
}

@Composable
fun AddTaskDialog(
    taskText: String,
    category: Category,
    onTaskTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    saveEnabled: Boolean,
    title: String,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = null,
        text = {
            Column {
                Text(title, color = Color.Black, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                CommonColoredTextField(
                    value = taskText,
                    onValueChange = onTaskTextChange,
                    label = { Text("Task description", color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = saveEnabled) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

@Composable
fun DialogManager(
    dialogState: DialogState,
    onDismiss: () -> Unit,
    vm: TodoViewModel,
    scaffoldState: ScaffoldState,
    coroutineScope: CoroutineScope,
    context: Context,
    onDialogStateChange: (DialogState) -> Unit,
    newTask: String,
    onNewTaskChange: (String) -> Unit,
    newCategory: Category?,  // made nullable to allow no initial category
    onCategoryChange: (Category) -> Unit,
    categories: List<Category>,  // pass full list of categories for defaults
) {
    // Determine default category to use if newCategory is null
    val defaultCategory = categories.firstOrNull() ?: Category(id = 0L, name = "Default")

    when (dialogState) {
        is DialogState.AddTask -> AddTaskDialog(
            taskText = newTask,
            category = newCategory ?: defaultCategory,  // use default if null
            onTaskTextChange = onNewTaskChange,
            saveEnabled = newTask.isNotBlank(),
            title = "New Task",
            onSave = {
                val sanitizedText = newTask.replace("\n", "").replace("\r", "")
                vm.addTask(sanitizedText.trim(), newCategory ?: defaultCategory) { message ->
                    coroutineScope.launch {
                        scaffoldState.snackbarHostState.showSnackbar(message)
                    }
                }
                onDialogStateChange(DialogState.None)
                onNewTaskChange("")
                onCategoryChange(defaultCategory)
            },
            onCancel = {
                onDialogStateChange(DialogState.None)
                onNewTaskChange("")
                onCategoryChange(defaultCategory)
            }
        )

        is DialogState.EditTask -> {
            val todo = dialogState.todo
            var editTaskText by remember { mutableStateOf(todo.task) }
            var editCategory by remember {
                mutableStateOf(
                    categories.find { it.id == todo.categoryId } ?: defaultCategory
                )
            }
            AddTaskDialog(
                taskText = editTaskText,
                category = editCategory,
                onTaskTextChange = { editTaskText = it },
                saveEnabled = editTaskText.isNotBlank(),
                title = "Edit Task",
                onSave = {
                    val sanitizedText = editTaskText.replace("\n", "").replace("\r", "")
                    vm.updateTask(
                        todo.copy(
                            task = sanitizedText.trim(),
                            categoryId = editCategory.id
                        )
                    )
                    onDialogStateChange(DialogState.None)
                },
                onCancel = { onDialogStateChange(DialogState.None) }
            )
        }

        is DialogState.SetReminder -> {
            LaunchedEffect(Unit) {
                val prefs =
                    context.getSharedPreferences(PreferencesManager.FILE, Context.MODE_PRIVATE)
                val hour = prefs.getInt(PreferencesManager.HOUR, 9)
                val minute = prefs.getInt(PreferencesManager.MINUTE, 0)
                TimePickerDialog(
                    context,
                    { _: TimePicker, selectedHour: Int, selectedMinute: Int ->
                        prefs.edit()
                            .putInt(PreferencesManager.HOUR, selectedHour)
                            .putInt(PreferencesManager.MINUTE, selectedMinute)
                            .apply()

                        ReminderApi.scheduleReminder(
                            context,
                            selectedHour,
                            selectedMinute,
                        )

                        onDialogStateChange(DialogState.None)
                    },
                    hour,
                    minute,
                    false
                ).apply {
                    setOnCancelListener { onDialogStateChange(DialogState.None) }
                    show()
                }
            }
        }

        DialogState.None -> Unit
    }
}

@Composable
fun formatTime(hour: Int, minute: Int): String {
    val isAM = hour < 12
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val minuteStr = minute.toString().padStart(2, '0')
    val amPm = if (isAM) "am" else "pm"
    return "$hour12:$minuteStr $amPm"
}

@Composable
fun TasksPageTopBar(
    scaffoldState: ScaffoldState,
    onSetReminderClick: () -> Unit,
    onGotoTodayClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onAddCategoryClick: () -> Unit,
) {
    val context = LocalContext.current
    var reminderEnabled by rememberSaveable { mutableStateOf(PreferencesManager.isReminderEnabled()) }
    var habitEnabled by rememberSaveable { mutableStateOf(PreferencesManager.isHabitEnabled()) }

    val prefs = PreferencesManager.sharedPrefs
    val currentHour = prefs.getInt(PreferencesManager.HOUR, 9)
    val currentMinute = prefs.getInt(PreferencesManager.MINUTE, 0)

    fun toggleReminder(enabled: Boolean) {
        reminderEnabled = enabled
        PreferencesManager.setReminderEnabled(enabled)
        if (enabled) {
            ReminderApi.scheduleReminder(context, currentHour, currentMinute)
        } else {
            ReminderApi.cancelReminder(context, ReminderApi.DEFAULT_TYPE)
        }
    }

    fun toggleHabit(enabled: Boolean) {
        habitEnabled = enabled
        PreferencesManager.setHabitEnabled(enabled)
    }

    Column {
        Spacer(Modifier.height(3.dp))
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            backgroundColor = MaterialTheme.colors.background,
            elevation = 0.dp,
            title = { Text("Lists", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.Black) },
            actions = {
                IconButton(onClick = onAddCategoryClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add List")
                }
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                    modifier = Modifier.width(180.dp)
                ) {
                    Spacer(Modifier.height(6.dp))
                    DropdownMenuItem(onClick = {
                        val newState = !habitEnabled
                        toggleHabit(newState)
                        onMenuExpandedChange(false)
                    }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Habit")
                            Switch(
                                checked = habitEnabled,
                                onCheckedChange = {
                                    toggleHabit(it)
                                }
                            )
                        }
                    }
                    Divider(color = Color.LightGray, thickness = 0.5.dp)
                    DropdownMenuItem(onClick = {
                        onMenuExpandedChange(false)
                        toggleReminder(!reminderEnabled)
                    }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reminder")
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { toggleReminder(it) }
                            )
                        }
                    }
                    DropdownMenuItem(onClick = {
                        onMenuExpandedChange(false)
                        onSetReminderClick()
                    }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Time")
                            Text(formatTime(currentHour, currentMinute))
                        }
                    }
                }
            }
        )
        Spacer(Modifier.height(5.dp))
        Divider(color = Color.LightGray, thickness = 0.5.dp)
    }
}

@Composable
fun TodoPageTopBar(
    title: String,
    scaffoldState: ScaffoldState,
    onAddClick: () -> Unit,
    onClearCompletedClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    clearCompletedEnabled: Boolean = true,
) {
    Column {
        Spacer(Modifier.height(3.dp))
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            backgroundColor = MaterialTheme.colors.background,
            elevation = 0.dp,
            title = { Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.Black) },
            actions = {
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        onClick = {
                            onMenuExpandedChange(false)
                            onClearCompletedClick()
                        },
                        enabled = clearCompletedEnabled
                    ) {
                        Text("Clear Tasks")
                    }
                }
            }
        )

        Spacer(Modifier.height(5.dp))
        Divider(color = Color.LightGray, thickness = 0.5.dp)
    }
}

@Composable
fun AddCategoryDialog(
    categoryName: String,
    onCategoryNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    saveEnabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = null,
        text = {
            Column {
                Text("New List", color = Color.Black, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                CommonColoredTextField(
                    value = categoryName,
                    onValueChange = {
                        if (it.length <= 18) onCategoryNameChange(it)
                    },
                    label = { Text("List name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = saveEnabled) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditCategoryDialog(
    showDialog: Boolean,
    category: Category,
    onDismiss: () -> Unit,
    onUpdateCategory: (Category) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(category.name) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = null,
            text = {
                Column {
                    Text("Edit List", color = Color.Black, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    CommonColoredTextField(
                        value = name,
                        onValueChange = { newVal ->
                            if (newVal.length <= 18) name = newVal
                        },
                        label = { Text("List name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onUpdateCategory(category.copy(name = name.trim()))
                            onDismiss()
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DeleteCategoryConfirmDialog(
    showDialog: Boolean,
    categoryName: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete List") },
            text = { Text("Are you sure to delete \"$categoryName\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmDelete()
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,    // Add move callbacks
    onMoveDown: () -> Unit,
    labelFontSize : Int
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val listDropMenuHeight = 4
    val listDropMenuHeightIn = 36

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(category.id) {
                detectTapGestures(
                    onLongPress = { menuExpanded = true },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Text(
            text = category.name,
            fontSize = labelFontSize.sp,
            color = Color.Black,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.fillMaxWidth()
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.width(160.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEdit()
                        menuExpanded = false
                    }
                    .padding(vertical = listDropMenuHeight.dp, horizontal = 16.dp) // smaller vertical padding
                    .heightIn(min = listDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Edit List")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onMoveUp()
                        menuExpanded = false
                    }
                    .padding(vertical = listDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = listDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Move Up")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onMoveDown()
                        menuExpanded = false
                    }
                    .padding(vertical = listDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = listDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Move Down")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDelete()
                        menuExpanded = false
                    }
                    .padding(vertical = listDropMenuHeight.dp, horizontal = 16.dp)
                    .heightIn(min = listDropMenuHeightIn.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Delete List")
            }
        }
    }
}

@Composable
fun LabelListScreen(
    navController: NavController,
    categories: List<Category>,
    scaffoldState: ScaffoldState,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onSetReminderClick: () -> Unit,
    onGotoTodayClick: () -> Unit,
    onLabelClick: (String) -> Unit,
    onChainClick: () -> Unit,
    categoryViewModel: CategoryViewModel,
) {
    val coroutineScope = rememberCoroutineScope()
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    val labelFontSize = 18

    // Observe habit enabled state as mutableState for dynamic hide/show with recomposition
    var habitEnabled by rememberSaveable { mutableStateOf(PreferencesManager.isHabitEnabled()) }
    LaunchedEffect(key1 = PreferencesManager.isHabitEnabled()) {
        habitEnabled = PreferencesManager.isHabitEnabled()
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TasksPageTopBar(
                scaffoldState = scaffoldState,
                onSetReminderClick = onSetReminderClick,
                onGotoTodayClick = onGotoTodayClick,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = onMenuExpandedChange,
                onAddCategoryClick = { showAddCategoryDialog = true }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            LazyColumn {
                itemsIndexed(categories) { index, category ->
                    CategoryRow(
                        category = category,
                        onClick = {
                            val dest = "taskList/${category.id}"
                            PreferencesManager.saveLastPage(dest)
                            navController.navigate(dest)
                        },
                        onEdit = { editingCategory = category },
                        onDelete = { deletingCategory = category },
                        onMoveUp = { coroutineScope.launch { categoryViewModel.moveCategoryUp(category) } },
                        onMoveDown = { coroutineScope.launch { categoryViewModel.moveCategoryDown(category) } },
                        labelFontSize = labelFontSize
                    )
                    Divider(color = Color.LightGray, thickness = 0.5.dp)
                }
                item {
                    if (habitEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChainClick() }
                                .padding(horizontal = 6.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Habit",
                                fontSize = labelFontSize.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.Black
                            )
                        }
                        Divider(color = Color.LightGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            categoryName = newCategoryName,
            onCategoryNameChange = { newCategoryName = it },
            saveEnabled = newCategoryName.isNotBlank(),
            onSave = {
                categoryViewModel.addCategory(newCategoryName.trim())
                newCategoryName = ""
                showAddCategoryDialog = false
            },
            onCancel = {
                newCategoryName = ""
                showAddCategoryDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        EditCategoryDialog(
            showDialog = true,
            category = category,
            onDismiss = { editingCategory = null },
            onUpdateCategory = {
                categoryViewModel.updateCategory(it)
                editingCategory = null
            }
        )
    }

    deletingCategory?.let { category ->
        DeleteCategoryConfirmDialog(
            showDialog = true,
            categoryName = category.name,
            onDismiss = { deletingCategory = null },
            onConfirmDelete = {
                categoryViewModel.deleteCategory(category.id)
                deletingCategory = null
            }
        )
    }
}

@Composable
fun TaskListScreen(
    selectedCategory: Category,
    categories: List<Category>,
    vm: TodoViewModel,
    scaffoldState: ScaffoldState,
    coroutineScope: CoroutineScope,
    dialogState: DialogState,
    onDialogStateChange: (DialogState) -> Unit,
    newTask: String,
    onNewTaskChange: (String) -> Unit,
    category: Category,
    onCategoryChange: (Category) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    val todos by vm.todos.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val filteredTasks = todos.filter { it.categoryId == selectedCategory.id }

    val hasCompletedInCategory = remember(filteredTasks, selectedCategory) {
        vm.hasCompletedTasksInCategory(selectedCategory)
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TodoPageTopBar(
                title = selectedCategory.name,
                scaffoldState = scaffoldState,
                onAddClick = onAddClick,
                onClearCompletedClick = {
                    var undoAvailable = true
                    vm.clearCompletedByCategory(selectedCategory) { undoAvailable = false }
                    coroutineScope.launch {
                        val result = scaffoldState.snackbarHostState.showSnackbar(
                            message = "Completed tasks cleared",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed && undoAvailable) {
                            vm.restoreClearedCompleted()
                        }
                    }
                },
                menuExpanded = menuExpanded,
                onMenuExpandedChange = onMenuExpandedChange,
                onBackClick = onBackClick,
                clearCompletedEnabled = hasCompletedInCategory
            )
        },
        content = { paddingValues ->
            Box(
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
            ) {
                if (!isLoading) {
                    if (filteredTasks.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No tasks in ${selectedCategory.name}", fontSize = 16.sp)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(filteredTasks, key = { it.id }) { todo ->
                                val index = filteredTasks.indexOf(todo)
                                val canMoveUp = index > 0
                                val canMoveDown = index < filteredTasks.lastIndex
                                var showMenu by remember { mutableStateOf(false) }
                                TodoRow(
                                    item = todo,
                                    categories = categories,
                                    showMenu = showMenu,
                                    onShowMenuChange = { showMenu = it },
                                    onToggleDone = { vm.toggleDone(todo) },
                                    onDelete = { vm.deleteTask(todo.id) },
                                    onEdit = { onDialogStateChange(DialogState.EditTask(todo)) },
                                    onMove = { newCat -> vm.updateTask(todo.copy(categoryId = newCat.id)) },
                                    vm = vm,
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}


@Composable
fun TodoScreen(
    vm: TodoViewModel,
    categoryViewModel: CategoryViewModel,
) {
    val context = LocalContext.current
    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var dialogState by remember { mutableStateOf<DialogState>(DialogState.None) }
    var newTask by rememberSaveable { mutableStateOf("") }

    var newCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }

    val categories by categoryViewModel.categories.collectAsState(emptyList())
    val navController = rememberNavController()

    val newCategory = categories.find { it.id == newCategoryId } ?: categories.firstOrNull()

    val onCategoryChange: (Category) -> Unit = { category ->
        newCategoryId = category.id
    }

    NavHost(
        navController = navController,
        startDestination = "labelList"
    ) {
        composable("labelList") {
            LabelListScreen(
                navController = navController,
                categories = categories,
                scaffoldState = scaffoldState,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it },
                onSetReminderClick = {
                    dialogState = DialogState.SetReminder
                    menuExpanded = false
                },
                onGotoTodayClick = { },
                onLabelClick = { label ->
                    categories.find { it.name == label }?.let {
                        navController.navigate("taskList/${it.id}")
                    }
                },
                onChainClick = {
                    navController.navigate("chainCalendar")
                    menuExpanded = false
                },
                categoryViewModel = categoryViewModel
            )
        }
        composable(
            "taskList/{categoryId}",
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId")
            val selectedCategory = categories.find { it.id == categoryId } ?: categories.firstOrNull()
            selectedCategory?.let { cat ->
                TaskListScreen(
                    selectedCategory = cat,
                    categories = categories,
                    vm = vm,
                    scaffoldState = scaffoldState,
                    coroutineScope = coroutineScope,
                    dialogState = dialogState,
                    onDialogStateChange = { dialogState = it },
                    newTask = newTask,
                    onNewTaskChange = { newTask = it },
                    category = cat,
                    onCategoryChange = onCategoryChange,
                    menuExpanded = menuExpanded,
                    onMenuExpandedChange = { menuExpanded = it },
                    onBackClick = { navController.popBackStack() },
                    onAddClick = {
                        newTask = ""
                        onCategoryChange(cat)  // Save current category ID for adding task
                        dialogState = DialogState.AddTask
                        menuExpanded = false
                    }
                )
            }
        }
        composable("chainCalendar") {
            val chainVm: ChainViewModel = viewModel(factory = ChainViewModelFactory(context))
            ChainCalendarScreen(chainVm = chainVm, navController = navController)
        }
    }

    DialogManager(
        dialogState = dialogState,
        onDismiss = { dialogState = DialogState.None },
        vm = vm,
        scaffoldState = scaffoldState,
        coroutineScope = coroutineScope,
        context = context,
        onDialogStateChange = { dialogState = it },
        newTask = newTask,
        onNewTaskChange = { newTask = it },
        newCategory = newCategory ?: Category(id = 0L, name = "Default"),
        onCategoryChange = onCategoryChange,
        categories = categories
    )
}
