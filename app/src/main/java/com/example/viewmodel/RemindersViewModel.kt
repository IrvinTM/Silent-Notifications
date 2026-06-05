package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskRepository
import com.example.data.HapticPattern
import com.example.ui.theme.AppThemeType
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository: TaskRepository
    private val prefs = context.getSharedPreferences("silent_reminders_prefs", Context.MODE_PRIVATE)

    val tasks: StateFlow<List<Task>>
    val hapticPatterns: StateFlow<List<HapticPattern>>
    private val hapticPatternDao = AppDatabase.getDatabase(context).hapticPatternDao()

    private val _currentTheme = MutableStateFlow(AppThemeType.COBALT_MIDNIGHT)
    val currentTheme: StateFlow<AppThemeType> = _currentTheme

    private val _customColor = MutableStateFlow<Color?>(null)
    val customColor: StateFlow<Color?> = _customColor

    private val _selectedFontFamilyName = MutableStateFlow("DEFAULT")
    val selectedFontFamilyName: StateFlow<String> = _selectedFontFamilyName

    // Google Calendar Sync States
    private val _calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEvent>> = _calendarEvents

    private val _calendarAutoSyncEnabled = MutableStateFlow(false)
    val calendarAutoSyncEnabled: StateFlow<Boolean> = _calendarAutoSyncEnabled

    private val _calendarDefaultPatternId = MutableStateFlow(0)
    val calendarDefaultPatternId: StateFlow<Int> = _calendarDefaultPatternId

    init {
        val database = AppDatabase.getDatabase(context)
        repository = TaskRepository(database.taskDao())
        
        // Expose a flowable list from DB reactive to UI additions
        tasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        hapticPatterns = hapticPatternDao.getAllPatterns().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        _calendarAutoSyncEnabled.value = prefs.getBoolean("calendar_auto_sync_enabled", false)
        _calendarDefaultPatternId.value = prefs.getInt("calendar_default_pattern_id", 0)

        // Reactive: trigger calendar sync on tasks updates (just refresh UI checkmarks, do NOT run auto-import recursively)
        viewModelScope.launch {
            tasks.collect {
                triggerCalendarSync(runAutoImport = false)
            }
        }

        // Pre-populate system patterns if DB is empty
        viewModelScope.launch {
            try {
                val list = hapticPatternDao.getAllPatterns().first()
                if (list.isEmpty()) {
                    val defaultPatterns = listOf(
                        HapticPattern(name = "Default Wave", patternStr = "0,500,200,500,200,800", isSystem = true),
                        HapticPattern(name = "Double Pulse", patternStr = "0,150,100,150,500,150,100,150", isSystem = true),
                        HapticPattern(name = "Heartbeat Sync", patternStr = "0,100,100,100,600,100,100,100", isSystem = true),
                        HapticPattern(name = "Morse Code SOS", patternStr = "0,100,100,100,100,100,300,300,300,300,300,100,100,100,100,100", isSystem = true),
                        HapticPattern(name = "Rapid Buzz", patternStr = "0,60,60,60,60,60,60,60,60,60,60,60", isSystem = true)
                    )
                    defaultPatterns.forEach {
                        hapticPatternDao.insertPattern(it)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Read custom theme preference
        val savedThemeName = prefs.getString("selected_theme", AppThemeType.COBALT_MIDNIGHT.name)
        val savedTheme = try {
            AppThemeType.valueOf(savedThemeName ?: AppThemeType.COBALT_MIDNIGHT.name)
        } catch (e: Exception) {
            AppThemeType.COBALT_MIDNIGHT
        }
        _currentTheme.value = savedTheme

        // Read custom color
        val customColorValue = prefs.getLong("custom_theme_color", -1L)
        _customColor.value = if (customColorValue != -1L) Color(customColorValue.toULong()) else null

        // Read custom font
        _selectedFontFamilyName.value = prefs.getString("selected_font_family", "DEFAULT") ?: "DEFAULT"
    }

    fun selectTheme(theme: AppThemeType) {
        _currentTheme.value = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun selectCustomColor(color: Color?) {
        _customColor.value = color
        if (color != null) {
            prefs.edit().putLong("custom_theme_color", color.value.toLong()).apply()
        } else {
            prefs.edit().remove("custom_theme_color").apply()
        }
    }

    fun selectFontFamily(fontFamilyName: String) {
        _selectedFontFamilyName.value = fontFamilyName
        prefs.edit().putString("selected_font_family", fontFamilyName).apply()
    }

    fun addTask(title: String, notes: String, timeInMillis: Long, repeatType: String, hapticPatternId: Int = 0) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                notes = notes,
                scheduleTime = timeInMillis,
                repeatType = repeatType,
                isCompleted = false,
                isArchived = false,
                hapticPatternId = hapticPatternId
            )
            repository.insert(task, context)
        }
    }

    fun addHapticPattern(name: String, patternStr: String, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = hapticPatternDao.insertPattern(HapticPattern(name = name, patternStr = patternStr, isSystem = false))
            onComplete(id)
        }
    }

    fun deleteHapticPattern(pattern: HapticPattern) {
        viewModelScope.launch {
            hapticPatternDao.deletePattern(pattern)
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = !task.isCompleted)
            repository.update(updated, context)
        }
    }

    fun toggleTaskArchived(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(isArchived = !task.isArchived)
            repository.update(updated, context)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.delete(task, context)
        }
    }

    fun toggleCalendarAutoSync(enabled: Boolean) {
        _calendarAutoSyncEnabled.value = enabled
        prefs.edit().putBoolean("calendar_auto_sync_enabled", enabled).apply()
        if (enabled) {
            triggerCalendarSync(runAutoImport = true)
        }
    }

    fun selectCalendarDefaultPattern(patternId: Int) {
        _calendarDefaultPatternId.value = patternId
        prefs.edit().putInt("calendar_default_pattern_id", patternId).apply()
    }

    fun triggerCalendarSync(runAutoImport: Boolean = false) {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return

        viewModelScope.launch {
            try {
                val currentTasksList = tasks.value
                val importedCalendarEventIds = currentTasksList.mapNotNull { it.googleEventId }.toSet()

                val rawEvents = fetchUpcomingEventsDirectly(context, 14)
                val mappedEvents = rawEvents.map { event ->
                    event.copy(isImported = importedCalendarEventIds.contains(event.id))
                }

                _calendarEvents.value = mappedEvents

                // If Auto-Sync is enabled AND runAutoImport is requested, automatically import any event that is not yet imported
                if (_calendarAutoSyncEnabled.value && runAutoImport) {
                    val defaultPatternId = _calendarDefaultPatternId.value
                    mappedEvents.forEach { event ->
                        if (!event.isImported) {
                            importCalendarEvent(event, defaultPatternId)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchUpcomingEventsDirectly(context: Context, daysAhead: Int): List<CalendarEvent> {
        val eventsList = mutableListOf<CalendarEvent>()
        try {
            val now = System.currentTimeMillis()
            val end = now + (daysAhead * 24 * 60 * 60 * 1000L)

            val builder = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, now)
            android.content.ContentUris.appendId(builder, end)
            val uri = builder.build()

            val projection = arrayOf(
                android.provider.CalendarContract.Instances.EVENT_ID,
                android.provider.CalendarContract.Instances.TITLE,
                android.provider.CalendarContract.Instances.DESCRIPTION,
                android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.END,
                android.provider.CalendarContract.Instances.CALENDAR_DISPLAY_NAME
            )

            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${android.provider.CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use { c ->
                val idIndex = c.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_ID)
                val titleIndex = c.getColumnIndex(android.provider.CalendarContract.Instances.TITLE)
                val descIndex = c.getColumnIndex(android.provider.CalendarContract.Instances.DESCRIPTION)
                val beginIndex = c.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                val endIndex = c.getColumnIndex(android.provider.CalendarContract.Instances.END)
                val calNameIndex = c.getColumnIndex(android.provider.CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                while (c.moveToNext()) {
                    val eventId = if (idIndex >= 0) c.getString(idIndex) else ""
                    val title = if (titleIndex >= 0) c.getString(titleIndex) ?: "No Title" else "No Title"
                    val notes = if (descIndex >= 0) c.getString(descIndex) ?: "" else ""
                    val begin = if (beginIndex >= 0) c.getLong(beginIndex) else 0L
                    val endTimes = if (endIndex >= 0) c.getLong(endIndex) else 0L
                    val calName = if (calNameIndex >= 0) c.getString(calNameIndex) ?: "Default Calendar" else "Default Calendar"

                    if (eventId.isNotEmpty() && begin >= now) {
                        eventsList.add(
                            CalendarEvent(
                                id = eventId,
                                title = title,
                                notes = notes,
                                startTime = begin,
                                endTime = endTimes,
                                calendarName = calName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return eventsList
    }

    fun importCalendarEvent(event: CalendarEvent, hapticPatternId: Int) {
        viewModelScope.launch {
            // Check again to avoid race conditions/duplicates
            val currentTasks = tasks.value
            if (currentTasks.any { it.googleEventId == event.id }) return@launch

            val task = Task(
                title = event.title,
                notes = event.notes.ifBlank { "Imported from Calendar (${event.calendarName})" },
                scheduleTime = event.startTime,
                repeatType = "ONCE",
                isCompleted = false,
                isArchived = false,
                hapticPatternId = hapticPatternId,
                googleEventId = event.id
            )
            repository.insert(task, context)
            
            // Refresh
            triggerCalendarSync()
        }
    }
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val notes: String,
    val startTime: Long,
    val endTime: Long,
    val calendarName: String,
    val isImported: Boolean = false
)
