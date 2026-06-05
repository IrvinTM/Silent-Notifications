@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Task
import com.example.ui.theme.AppThemeType
import com.example.ui.theme.SilentRemindersTheme
import com.example.viewmodel.RemindersViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: RemindersViewModel = viewModel()
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
            val customColor by viewModel.customColor.collectAsStateWithLifecycle()
            val selectedFontFamily by viewModel.selectedFontFamilyName.collectAsStateWithLifecycle()

            SilentRemindersTheme(
                themeType = currentTheme,
                customColor = customColor,
                fontFamilyName = selectedFontFamily
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RemindersScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(viewModel: RemindersViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val customColor by viewModel.customColor.collectAsStateWithLifecycle()
    val selectedFontFamily by viewModel.selectedFontFamilyName.collectAsStateWithLifecycle()

    // 1. Manage state fields for Task Creation form
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var repeatType by rememberSaveable { mutableStateOf("ONCE") } // "ONCE", "WEEKLY", "MONTHLY"

    var triggerTimeMillis by rememberSaveable {
        mutableLongStateOf(Calendar.getInstance().apply {
            add(Calendar.MINUTE, 5)
        }.timeInMillis)
    }

    // Toggle forms and active tab
    var isAddReminderDialogOpen by rememberSaveable { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }

    // Haptic Pattern States
    val hapticPatterns by viewModel.hapticPatterns.collectAsStateWithLifecycle()
    var selectedHapticPatternId by rememberSaveable { mutableStateOf(0) }
    var isRecorderDialogOpen by rememberSaveable { mutableStateOf(false) }
    var showPatternSelectorDialog by rememberSaveable { mutableStateOf(false) }

    // 2. Notification Runtime Permissions (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "Vibration notifications enabled!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Notifications blocked. Alarm reminders might only trigger hardware vibration.", Toast.LENGTH_LONG).show()
            }
        }
    )

    var hasCalendarPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCalendarPermission = isGranted
            if (isGranted) {
                Toast.makeText(context, "Calendar access granted!", Toast.LENGTH_SHORT).show()
                viewModel.triggerCalendarSync(runAutoImport = viewModel.calendarAutoSyncEnabled.value)
            } else {
                Toast.makeText(context, "Calendar access denied. Cannot sync Google Calendar events.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (hasCalendarPermission) {
            viewModel.triggerCalendarSync(runAutoImport = viewModel.calendarAutoSyncEnabled.value)
        }
    }

    val triggerDateTimeString = remember(triggerTimeMillis) {
        val sdf = SimpleDateFormat("EEEE, h:mm a (MMM d, yyyy)", Locale.getDefault())
        sdf.format(Date(triggerTimeMillis))
    }

    // Helper to open both DatePickerDialog and TimePickerDialog sequentially
    val showDateTimePicker = {
        val currentCal = Calendar.getInstance().apply {
            timeInMillis = triggerTimeMillis
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    timeInMillis = triggerTimeMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                
                android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        selectedCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        selectedCal.set(Calendar.MINUTE, minute)
                        selectedCal.set(Calendar.SECOND, 0)
                        selectedCal.set(Calendar.MILLISECOND, 0)
                        
                        triggerTimeMillis = selectedCal.timeInMillis
                    },
                    currentCal.get(Calendar.HOUR_OF_DAY),
                    currentCal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Categorize tasks for simplified view lists
    val activeTasks = remember(tasks) { tasks.filter { !it.isCompleted && !it.isArchived } }
    val archivedOrCompletedTasks = remember(tasks) { tasks.filter { it.isCompleted || it.isArchived } }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Drawer Header Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "App Logo Indicator",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Silent Reminders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Vibration alerts only • No audio disruptions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Items
                NavigationDrawerItem(
                    label = { Text("Active Reminders (${activeTasks.size})", fontWeight = FontWeight.Bold) },
                    selected = selectedTabIndex == 0 && !isSettingsOpen,
                    onClick = {
                        selectedTabIndex = 0
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("drawer_menu_dashboard")
                )

                NavigationDrawerItem(
                    label = { Text("Schedule New Reminder", fontWeight = FontWeight.Bold) },
                    selected = isAddReminderDialogOpen,
                    onClick = {
                        isAddReminderDialogOpen = true
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("drawer_menu_create")
                )

                NavigationDrawerItem(
                    label = { Text("History & Archive (${archivedOrCompletedTasks.size})", fontWeight = FontWeight.Bold) },
                    selected = selectedTabIndex == 1 && !isSettingsOpen,
                    onClick = {
                        selectedTabIndex = 1
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("drawer_menu_history")
                )

                NavigationDrawerItem(
                    label = { Text("App Settings", fontWeight = FontWeight.Bold) },
                    selected = isSettingsOpen,
                    onClick = {
                        isSettingsOpen = true
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("drawer_menu_settings")
                )

                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "v1.2.0 • Silent Sync Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 24.dp)
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open navigation drawer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Silent Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { isSettingsOpen = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("settings_button")
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "App Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        label = { Text("Active") },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeTasks.isNotEmpty()) {
                                        Badge {
                                            Text(
                                                text = activeTasks.size.toString(),
                                                modifier = Modifier.testTag("active_badge_count")
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Active Reminders Tab"
                                )
                            }
                        },
                        modifier = Modifier.testTag("tab_reminders")
                    )
                    NavigationBarItem(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        label = { Text("Archived") },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (archivedOrCompletedTasks.isNotEmpty()) {
                                        Badge {
                                            Text(
                                                text = archivedOrCompletedTasks.size.toString(),
                                                modifier = Modifier.testTag("archived_badge_count")
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "Archived Tab"
                                )
                            }
                        },
                        modifier = Modifier.testTag("tab_archived")
                    )
                }
            },
            floatingActionButton = {
                if (selectedTabIndex == 0) {
                    FloatingActionButton(
                        onClick = { isAddReminderDialogOpen = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_reminder_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Schedule New Silent Reminder"
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTabIndex == 0) {
                    // --- TAB 0: REMINDERS (ACTIVE REMINDERS) ---
                    // --- MAIN LIST: UPCOMING ALARMS (ACTIVE REMINDERS) ---
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Reminders (${activeTasks.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Swipe to Archive/Delete",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (activeTasks.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Empty list",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                    modifier = Modifier.size(60.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No active silent reminders scheduled",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Schedule one-time, weekly, or monthly alarms above.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(items = activeTasks, key = { it.id }) { task ->
                            SwipeableTaskItem(
                                task = task,
                                onArchive = {
                                    viewModel.toggleTaskArchived(task)
                                    Toast.makeText(context, "Task archived", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    viewModel.deleteTask(task)
                                    Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                TaskItemCard(
                                    task = task,
                                    hapticPatternName = hapticPatterns.find { it.id == task.hapticPatternId }?.name,
                                    onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                                    onToggleArchive = { viewModel.toggleTaskArchived(task) }
                                )
                            }
                        }
                    }
                } else {
                    // --- TAB 1: ARCHIVED & COMPLETED HISTORY ---
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "History & Archive (${archivedOrCompletedTasks.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Swipe to Delete",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (archivedOrCompletedTasks.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Empty History",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                    modifier = Modifier.size(60.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "History collection is empty",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Completed or archived silent reminders will appear here.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(items = archivedOrCompletedTasks, key = { it.id }) { task ->
                            SwipeableTaskItem(
                                task = task,
                                onArchive = {
                                    viewModel.toggleTaskArchived(task)
                                    Toast.makeText(context, "Task archived status updated", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    viewModel.deleteTask(task)
                                    Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                TaskItemCard(
                                    task = task,
                                    hapticPatternName = hapticPatterns.find { it.id == task.hapticPatternId }?.name,
                                    onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                                    onToggleArchive = { viewModel.toggleTaskArchived(task) }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
    }
}

    if (isSettingsOpen) {
        AlertDialog(
            onDismissRequest = { isSettingsOpen = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(text = "App Settings", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Accent Theme Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Choose App Theme Accent",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Classic Presets",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val classicThemes = listOf(
                                    AppThemeType.COBALT_MIDNIGHT,
                                    AppThemeType.EMERALD_FOREST,
                                    AppThemeType.SUNSET_COPPER,
                                    AppThemeType.LAVENDER_FIELDS,
                                    AppThemeType.CYBERPUNK_NEON
                                )
                                classicThemes.forEach { themeItem ->
                                    val isSelected = currentTheme == themeItem && customColor == null
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .testTag("theme_item_${themeItem.name}")
                                            .clip(CircleShape)
                                            .background(themeItem.primaryColor)
                                            .clickable {
                                                viewModel.selectCustomColor(null) // Reset custom color to use preset
                                                viewModel.selectTheme(themeItem)
                                            }
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (themeItem.isLight) Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Coffee & Catppuccin Presets",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val cozyThemes = listOf(
                                    AppThemeType.CAPPUCCINO_ROAST,
                                    AppThemeType.CATPPUCCIN_MOCHA,
                                    AppThemeType.CATPPUCCIN_MACCHIATO,
                                    AppThemeType.CATPPUCCIN_FRAPPE,
                                    AppThemeType.CATPPUCCIN_LATTE
                                )
                                cozyThemes.forEach { themeItem ->
                                    val isSelected = currentTheme == themeItem && customColor == null
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .testTag("theme_item_${themeItem.name}")
                                            .clip(CircleShape)
                                            .background(themeItem.primaryColor)
                                            .clickable {
                                                viewModel.selectCustomColor(null) // Reset custom color to use preset
                                                viewModel.selectTheme(themeItem)
                                            }
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (themeItem.isLight) Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (customColor == null) "Active preset: ${currentTheme.displayName}" else "Active: Custom Color Hue",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Use Custom Accent Color",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = customColor != null,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            viewModel.selectCustomColor(Color(0xFF3B82F6)) // Default custom color to Cobalt Blue
                                        } else {
                                            viewModel.selectCustomColor(null)
                                        }
                                    },
                                    modifier = Modifier.testTag("custom_color_switch")
                                )
                            }

                            if (customColor != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                val hsv = FloatArray(3).apply {
                                    android.graphics.Color.colorToHSV(
                                        android.graphics.Color.argb(
                                            (customColor!!.alpha * 255).toInt(),
                                            (customColor!!.red * 255).toInt(),
                                            (customColor!!.green * 255).toInt(),
                                            (customColor!!.blue * 255).toInt()
                                        ),
                                        this
                                    )
                                }
                                var hue by remember(customColor) { mutableFloatStateOf(hsv[0]) }

                                Text(
                                    text = "Adjust Hue: ${hue.toInt()}°",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                                )
                                            )
                                        )
                                )
                                
                                Slider(
                                    value = hue,
                                    onValueChange = { newHue ->
                                        hue = newHue
                                        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(newHue, 0.85f, 0.95f))
                                        viewModel.selectCustomColor(Color(colorInt))
                                    },
                                    valueRange = 0f..360f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("hue_slider")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Quick Presets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val quickColors = listOf(
                                        Color(0xFFEF4444), // Red
                                        Color(0xFFF97316), // Orange
                                        Color(0xFFEAB308), // Yellow
                                        Color(0xFF22C55E), // Green
                                        Color(0xFF06B6D4), // Cyan
                                        Color(0xFF3B82F6), // Blue
                                        Color(0xFF8B5CF6), // Purple
                                        Color(0xFFEC4899)  // Pink
                                    )
                                    quickColors.forEach { qc ->
                                        val qcSelected = customColor?.toArgb() == qc.toArgb()
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(qc)
                                                .border(
                                                    width = if (qcSelected) 3.dp else 0.dp,
                                                    color = if (qcSelected) Color.White else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    viewModel.selectCustomColor(qc)
                                                }
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))

                                var hexText by remember(customColor) {
                                    val colorHex = String.format("%06X", (customColor!!.toArgb() and 0xFFFFFF))
                                    mutableStateOf(colorHex)
                                }
                                var isHexError by remember { mutableStateOf(false) }

                                OutlinedTextField(
                                    value = hexText,
                                    onValueChange = { newVal ->
                                        val sanitized = newVal.trim().uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
                                        hexText = sanitized
                                        if (sanitized.length == 6) {
                                            try {
                                                val parsedColor = Color(android.graphics.Color.parseColor("#$sanitized"))
                                                viewModel.selectCustomColor(parsedColor)
                                                isHexError = false
                                            } catch (e: Exception) {
                                                isHexError = true
                                            }
                                        } else {
                                            isHexError = true
                                        }
                                    },
                                    label = { Text("HEX Color Value") },
                                    placeholder = { Text("3B82F6") },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(customColor ?: Color.Transparent)
                                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        )
                                    },
                                    trailingIcon = {
                                        if (isHexError) {
                                            Icon(Icons.Default.Warning, contentDescription = "Error parsing color", tint = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("hex_color_input"),
                                    isError = isHexError,
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }

                    // Theme Typography Font Family Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Choose Typography Style",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            val fontChoices = listOf(
                                Triple("DEFAULT", "System Default", FontFamily.Default),
                                Triple("SANS_SERIF", "Neo Sans", FontFamily.SansSerif),
                                Triple("SERIF", "Classic Serif", FontFamily.Serif),
                                Triple("MONOSPACE", "Developer Mono", FontFamily.Monospace),
                                Triple("CURSIVE", "Cozy Script", FontFamily.Cursive)
                            )
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                fontChoices.forEach { (id, name, family) ->
                                    val isFontSelected = selectedFontFamily == id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isFontSelected) 
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else 
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                            )
                                            .clickable {
                                                viewModel.selectFontFamily(id)
                                            }
                                            .border(
                                                width = 1.dp,
                                                color = if (isFontSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = name,
                                            fontFamily = family,
                                            fontWeight = if (isFontSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isFontSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isFontSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- NEW SECTION: VIBRATION HAPTIC PATTERNS ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Vibration Pattern Rhythms",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { isRecorderDialogOpen = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Record New", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                hapticPatterns.forEach { pattern ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pattern.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (pattern.isSystem) "System Preset" else "Custom Pulse Pattern",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Play Back Preview button
                                            var isPlayingThis by remember { mutableStateOf(false) }
                                            IconButton(
                                                onClick = {
                                                    if (!isPlayingThis) {
                                                        isPlayingThis = true
                                                        scope.launch {
                                                            try {
                                                                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                                                if (vibrator != null && vibrator.hasVibrator()) {
                                                                    val parts = pattern.patternStr.split(",").map { it.trim().toLong() }.toLongArray()
                                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                                        vibrator.vibrate(android.os.VibrationEffect.createWaveform(parts, -1))
                                                                    } else {
                                                                        @Suppress("DEPRECATION")
                                                                        vibrator.vibrate(parts, -1)
                                                                    }
                                                                    kotlinx.coroutines.delay(parts.sum())
                                                                }
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            } finally {
                                                                isPlayingThis = false
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingThis) Icons.Default.Close else Icons.Default.PlayArrow,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                    contentDescription = "Play back Preview"
                                                )
                                            }
                                            
                                            if (!pattern.isSystem) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteHapticPattern(pattern)
                                                        Toast.makeText(context, "Pattern deleted", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp),
                                                        contentDescription = "Delete Pattern"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Google Calendar Section
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("google_calendar_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Google Calendar Sync",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Match upcoming meetings and calendar events. The app automatically registers them as completely silent vibration reminders.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (!hasCalendarPermission) {
                                Button(
                                    onClick = {
                                        calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("authorize_calendar_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Authorize Calendar Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Auto Sync Toggle
                                val autoSyncEnabled by viewModel.calendarAutoSyncEnabled.collectAsStateWithLifecycle()
                                val defaultPatternId by viewModel.calendarDefaultPatternId.collectAsStateWithLifecycle()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-Pilot Sync",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Automatically schedule reminders for active calendar items.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = autoSyncEnabled,
                                        onCheckedChange = { checked ->
                                            viewModel.toggleCalendarAutoSync(checked)
                                        },
                                        modifier = Modifier.testTag("calendar_auto_sync_switch")
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Default Haptic Selector for calendar
                                Text(
                                    text = "Assign Haptic Pattern:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                var expandedDefaultPatternDropdown by remember { mutableStateOf(false) }
                                val selectedPatternName = hapticPatterns.find { it.id == defaultPatternId }?.name ?: "Default System Wave"

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { expandedDefaultPatternDropdown = true },
                                        modifier = Modifier.fillMaxWidth().testTag("default_calendar_pattern_selector"),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(selectedPatternName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedDefaultPatternDropdown,
                                        onDismissRequest = { expandedDefaultPatternDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Default System Wave") },
                                            onClick = {
                                                viewModel.selectCalendarDefaultPattern(0)
                                                expandedDefaultPatternDropdown = false
                                            }
                                        )
                                        hapticPatterns.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p.name) },
                                                onClick = {
                                                    viewModel.selectCalendarDefaultPattern(p.id)
                                                    expandedDefaultPatternDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.triggerCalendarSync(runAutoImport = true)
                                            Toast.makeText(context, "Scanning and Syncing Google Calendar...", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("force_sync_calendar_button"),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sync Events Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Calendar Events List
                                val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
                                Text(
                                    text = "Upcoming Calendar Events (Next 14 Days):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                if (calendarEvents.isEmpty()) {
                                    Text(
                                        text = "No upcoming calendar events detected. Try tapping Sync above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 180.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        calendarEvents.forEach { event ->
                                            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                            val dateStr = sdf.format(Date(event.startTime))
                                            
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (event.isImported) 
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) 
                                                    else 
                                                        MaterialTheme.colorScheme.surface
                                                ),
                                                border = BorderStroke(
                                                    1.dp, 
                                                    if (event.isImported) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = event.title,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "$dateStr (${event.calendarName})",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    if (event.isImported) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Synced",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Text(
                                                                text = "Synced",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    } else {
                                                        Button(
                                                            onClick = {
                                                                viewModel.importCalendarEvent(event, defaultPatternId)
                                                                Toast.makeText(context, "Scheduled: ${event.title}", Toast.LENGTH_SHORT).show()
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(26.dp).testTag("sync_event_btn_${event.id}"),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                contentColor = MaterialTheme.colorScheme.primary
                                                            )
                                                        ) {
                                                            Text("Sync", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Configuration status or info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Vibration & Silent Alerts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "All scheduled reminders use the system hardware vibrator dynamically. This avoids loud sound disturbances during meetings, study sessions, or quiet hours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Test trigger button
                            Button(
                                onClick = {
                                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    if (vibrator != null && vibrator.hasVibrator()) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(200)
                                        }
                                        Toast.makeText(context, "Testing Vibration Sync!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Vibrator engine not detected", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Static Vibration Pulse", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { isSettingsOpen = false },
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (isAddReminderDialogOpen) {
        AlertDialog(
            onDismissRequest = { isAddReminderDialogOpen = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )
                    Text(
                        text = "Schedule Silent Reminder",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title input
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Reminder Title") },
                        placeholder = { Text("e.g., Take Afternoon Meds") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_title_input"),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                        )
                    )

                    // Notes input
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Details / Description (Optional)") },
                        placeholder = { Text("e.g., Take with water") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_notes_input"),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Presets
                    Text(
                        text = "Quick Schedule Presets",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pillModifier = Modifier.weight(1f).height(36.dp)
                        Button(
                            onClick = {
                                triggerTimeMillis = System.currentTimeMillis() + 1 * 60 * 1000
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = pillModifier.testTag("quick_schedule_1m"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("+1 Min", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                triggerTimeMillis = System.currentTimeMillis() + 5 * 60 * 1000
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = pillModifier.testTag("quick_schedule_5m"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("+5 Min", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                triggerTimeMillis = System.currentTimeMillis() + 60 * 60 * 1000
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = pillModifier.testTag("quick_schedule_1h"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("+1 Hr", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                triggerTimeMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = pillModifier.testTag("quick_schedule_1d"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("+1 Day", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Date Time Card
                    Text(
                        text = "Choose Date & Time",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    OutlinedCard(
                        onClick = { showDateTimePicker() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("date_time_picker_card"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Select Date and Time",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Set Alarm For",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = triggerDateTimeString,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Button(
                                onClick = { showDateTimePicker() },
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Change", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Recurrence Selector
                    Text(
                        text = "Recurrence Interval",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val recurrenceOptions = listOf("ONCE" to "One-time", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly")
                        recurrenceOptions.forEach { opt ->
                            val isSelected = repeatType == opt.first
                            Button(
                                onClick = { repeatType = opt.first },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(opt.second, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Vibration Style Selector Card
                    Text(
                        text = "Vibration Pattern Style",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    
                    val currentSelectedPatternName = hapticPatterns.find { it.id == selectedHapticPatternId }?.name ?: "System Standard"
                    OutlinedCard(
                        onClick = { showPatternSelectorDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("haptic_pattern_picker_card"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Vibration Pattern Selector",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Vibe Alarm Style",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = currentSelectedPatternName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Button(
                                onClick = { showPatternSelectorDialog = true },
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Please enter a reminder title", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addTask(title, notes, triggerTimeMillis, repeatType, selectedHapticPatternId)
                            Toast.makeText(context, "Silent Alert scheduled!", Toast.LENGTH_SHORT).show()
                            
                            // Reset state fields
                            title = ""
                            notes = ""
                            triggerTimeMillis = System.currentTimeMillis() + 5 * 60 * 1000
                            selectedHapticPatternId = 0
                            isAddReminderDialogOpen = false
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.testTag("add_task_button")
                ) {
                    Text("Schedule", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { isAddReminderDialogOpen = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPatternSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showPatternSelectorDialog = false },
            title = { Text("Choose Vibration Style", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Default/System Option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedHapticPatternId = 0
                                showPatternSelectorDialog = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedHapticPatternId == 0) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("System Standard", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Default hardware vibration rhythm", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            if (selectedHapticPatternId == 0) {
                                Icon(Icons.Default.Check, tint = MaterialTheme.colorScheme.primary, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Database patterns options
                    hapticPatterns.forEach { pattern ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedHapticPatternId = pattern.id
                                    showPatternSelectorDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedHapticPatternId == pattern.id) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pattern.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(if (pattern.isSystem) "Predefined preset" else "Custom recorded rhythmic pattern", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                if (selectedHapticPatternId == pattern.id) {
                                    Icon(Icons.Default.Check, tint = MaterialTheme.colorScheme.primary, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Button to record a new pattern directly from here!
                    OutlinedButton(
                        onClick = {
                            isRecorderDialogOpen = true
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Record Custom Alarm Style...", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPatternSelectorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (isRecorderDialogOpen) {
        val recorderScope = rememberCoroutineScope()
        var patternName by remember { mutableStateOf("") }
        var isRecordingStarted by remember { mutableStateOf(false) }
        var lastEventTime by remember { mutableLongStateOf(0L) }
        val recordedDurations = remember { mutableStateListOf<Long>() }
        var isPadPressed by remember { mutableStateOf(false) }
        var isPlayingPreview by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        LaunchedEffect(isPressed) {
            if (isRecordingStarted) {
                val now = System.currentTimeMillis()
                if (isPressed) {
                    isPadPressed = true
                    val durationOfSilence = now - lastEventTime
                    recordedDurations.add(durationOfSilence)
                    lastEventTime = now
                    
                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(10000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(10000)
                        }
                    }
                } else {
                    isPadPressed = false
                    if (recordedDurations.isNotEmpty()) {
                        val durationOfVibration = now - lastEventTime
                        recordedDurations.add(durationOfVibration)
                        lastEventTime = now
                        
                        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        vibrator?.cancel()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { 
                isRecorderDialogOpen = false 
                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                vibrator?.cancel()
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )
                    Text(text = "Haptic Pattern Recorder", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Record dynamic silent tapping rhythms. Name your pattern, press Start, then hold the pad to describe vibration pulses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    OutlinedTextField(
                        value = patternName,
                        onValueChange = { patternName = it },
                        label = { Text("Pattern Name") },
                        placeholder = { Text("e.g. Work Buzz Tap") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isRecordingStarted) {
                            Button(
                                onClick = {
                                    recordedDurations.clear()
                                    isRecordingStarted = true
                                    lastEventTime = System.currentTimeMillis()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Recording", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isRecordingStarted = false
                                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    vibrator?.cancel()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Recording", fontSize = 11.sp)
                            }
                        }
                    }
                    
                    // Recording Pad
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isRecordingStarted) {
                                    if (isPadPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                }
                            )
                            .border(
                                width = 2.dp,
                                color = if (isRecordingStarted) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                enabled = isRecordingStarted,
                                interactionSource = interactionSource,
                                indication = LocalIndication.current
                            ) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Recorder Vibration Pad Icon",
                                tint = if (isRecordingStarted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isRecordingStarted) {
                                    if (isPadPressed) "VIBRATING NOW..." else "Press and Hold to Vibrate"
                                } else {
                                    "Click Start Recording Above"
                                },
                                fontWeight = FontWeight.Bold,
                                color = if (isRecordingStarted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    if (recordedDurations.isNotEmpty()) {
                        Text(
                            text = "Recorded Intervals (${recordedDurations.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            recordedDurations.forEachIndexed { idx, dur ->
                                val isVibration = idx % 2 == 1
                                Box(
                                    modifier = Modifier
                                        .weight(dur.toFloat().coerceAtLeast(10f))
                                        .fillMaxHeight()
                                        .background(
                                            if (isVibration) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                )
                            }
                        }
                        
                        // Playback Preview
                        Button(
                            onClick = {
                                if (!isPlayingPreview) {
                                    isPlayingPreview = true
                                    recorderScope.launch {
                                        try {
                                            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                            if (vibrator != null && vibrator.hasVibrator()) {
                                                val parts = recordedDurations.toLongArray()
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(parts, -1))
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    vibrator.vibrate(parts, -1)
                                                 }
                                                 kotlinx.coroutines.delay(parts.sum())
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        } finally {
                                            isPlayingPreview = false
                                        }
                                    }
                                }
                            },
                            enabled = !isRecordingStarted && !isPlayingPreview,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPlayingPreview) "Playing Preview..." else "Play back Recorded Pattern", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (patternName.isBlank()) {
                            Toast.makeText(context, "Please enter a pattern name", Toast.LENGTH_SHORT).show()
                        } else if (recordedDurations.isEmpty()) {
                            Toast.makeText(context, "Please record some vibration taps first", Toast.LENGTH_SHORT).show()
                        } else {
                            val patternStr = recordedDurations.joinToString(",")
                            viewModel.addHapticPattern(patternName, patternStr) { newId ->
                                selectedHapticPatternId = newId.toInt()
                                isRecorderDialogOpen = false
                                showPatternSelectorDialog = false
                                Toast.makeText(context, "Vibration Alarm Style Saved!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isRecordingStarted
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        isRecorderDialogOpen = false 
                        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        vibrator?.cancel()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskItem(
    task: Task,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onArchive()
                    false // Return false so the item bounces back visually (makes archiving toggleable)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true // Return true since deletion removes it completely
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFFF59E0B) // Amber for Archive
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFEF4444) // Red for Delete
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val iconLabel = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> "📦 Archive Task"
                SwipeToDismissBoxValue.EndToStart -> "🗑️ Delete Permanently"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Text(
                    text = iconLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        content = {
            content()
        }
    )
}

@Composable
fun TaskItemCard(
    task: Task,
    hapticPatternName: String? = null,
    onToggleComplete: () -> Unit,
    onToggleArchive: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val formattedTime = sdf.format(Date(task.scheduleTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            }
        ),
        border = if (task.isArchived) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        } else if (task.isCompleted) {
            BorderStroke(1.dp, Color.Transparent)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox on the left
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() },
                modifier = Modifier.size(24.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Info text block
            Column(modifier = Modifier.weight(1.0f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time Badge & Pulse Vibration indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (task.isCompleted) {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formattedTime,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (task.isCompleted) {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    // Schedule Repeat Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val repeatLabel = when (task.repeatType) {
                            "WEEKLY" -> "Weekly Repeat"
                            "MONTHLY" -> "Monthly Repeat"
                            else -> "Single Shot"
                        }
                        Text(
                            text = repeatLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }

                    if (task.hapticPatternId != 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📳 ${hapticPatternName ?: "Custom Pattern"}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (task.isArchived) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFBBF24).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Archived",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                }
            }

            // Quick Archive trigger button
            IconButton(
                onClick = { onToggleArchive() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (task.isArchived) Icons.Default.PlayArrow else Icons.Default.Settings,
                    contentDescription = if (task.isArchived) "Unarchive" else "Archive",
                    tint = if (task.isArchived) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
