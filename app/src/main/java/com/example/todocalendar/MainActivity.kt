package com.example.todocalendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.*

data class TodoTask(
    val id: Long,
    val eventId: Long,
    val title: String,
    val description: String,
    val startTime: Long,
    val isCompleted: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TodoAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoAppScreen() {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf<List<TodoTask>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<TodoTask?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.WRITE_CALENDAR] == true &&
                permissions[Manifest.permission.READ_CALENDAR] == true
        if (hasPermission) {
            tasks = loadTasksFromCalendar(context)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            tasks = loadTasksFromCalendar(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todo Calendar") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (hasPermission) {
                FloatingActionButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "需要日历权限来管理任务和提醒",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        }
                    ) {
                        Text("授予权限")
                    }
                }
            } else {
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无任务",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "点击 + 按钮添加新任务",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            TaskItem(
                                task = task,
                                onEdit = {
                                    selectedTask = task
                                    showEditDialog = true
                                },
                                onDelete = {
                                    deleteTaskFromCalendar(context, task.eventId)
                                    tasks = loadTasksFromCalendar(context)
                                    Toast.makeText(context, "任务已删除", Toast.LENGTH_SHORT).show()
                                },
                                onToggleComplete = {
                                    val updatedTask = task.copy(isCompleted = !task.isCompleted)
                                    updateTaskInCalendar(context, updatedTask)
                                    tasks = loadTasksFromCalendar(context)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditTaskDialog(
            title = "添加任务",
            onDismiss = { showAddDialog = false },
            onSave = { title, description, reminderMinutes ->
                val eventId = addTaskToCalendar(context, title, description, reminderMinutes)
                if (eventId != -1L) {
                    tasks = loadTasksFromCalendar(context)
                    Toast.makeText(context, "任务已添加到日历", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "添加失败", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && selectedTask != null) {
        AddEditTaskDialog(
            title = "编辑任务",
            initialTitle = selectedTask!!.title,
            initialDescription = selectedTask!!.description,
            onDismiss = { showEditDialog = false },
            onSave = { title, description, reminderMinutes ->
                val updatedTask = selectedTask!!.copy(
                    title = title,
                    description = description
                )
                updateTaskInCalendar(context, updatedTask, reminderMinutes)
                tasks = loadTasksFromCalendar(context)
                Toast.makeText(context, "任务已更新", Toast.LENGTH_SHORT).show()
                showEditDialog = false
                selectedTask = null
            }
        )
    }
}

@Composable
fun TaskItem(
    task: TodoTask,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (task.description.isNotEmpty()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDate(task.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    title: String,
    initialTitle: String = "",
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var taskTitle by remember { mutableStateOf(initialTitle) }
    var taskDescription by remember { mutableStateOf(initialDescription) }
    var reminderMinutes by remember { mutableStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text("任务标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = taskDescription,
                    onValueChange = { taskDescription = it },
                    label = { Text("任务描述（可选）") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "提醒时间（提前 $reminderMinutes 分钟）",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Slider(
                    value = reminderMinutes.toFloat(),
                    onValueChange = { reminderMinutes = it.toInt() },
                    valueRange = 1f..60f,
                    steps = 11
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (taskTitle.isNotBlank()) {
                        onSave(taskTitle, taskDescription, reminderMinutes)
                    }
                },
                enabled = taskTitle.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

fun addTaskToCalendar(
    context: android.content.Context,
    title: String,
    description: String,
    reminderMinutes: Int
): Long {
    try {
        val calendarId = getCalendarId(context) ?: return -1L
        
        val startMillis = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 10)
        }.timeInMillis
        
        val endMillis = startMillis + (60 * 60 * 1000)
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        
        val uri = context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI,
            values
        )
        
        val eventId = uri?.lastPathSegment?.toLongOrNull() ?: return -1L
        
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, reminderMinutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        
        context.contentResolver.insert(
            CalendarContract.Reminders.CONTENT_URI,
            reminderValues
        )
        
        return eventId
    } catch (e: Exception) {
        e.printStackTrace()
        return -1L
    }
}

fun updateTaskInCalendar(
    context: android.content.Context,
    task: TodoTask,
    reminderMinutes: Int? = null
) {
    try {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, task.title)
            put(CalendarContract.Events.DESCRIPTION, task.description)
        }
        
        val eventUri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            task.eventId
        )
        
        context.contentResolver.update(eventUri, values, null, null)
        
        if (reminderMinutes != null) {
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(task.eventId.toString())
            )
            
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, task.eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            
            context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                reminderValues
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun deleteTaskFromCalendar(context: android.content.Context, eventId: Long) {
    try {
        val deleteUri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            eventId
        )
        context.contentResolver.delete(deleteUri, null, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadTasksFromCalendar(context: android.content.Context): List<TodoTask> {
    val tasks = mutableListOf<TodoTask>()
    
    try {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART
        )
        
        val selection = "${CalendarContract.Events.DTSTART} >= ?"
        val selectionArgs = arrayOf(System.currentTimeMillis().toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"
        
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(CalendarContract.Events._ID)
            val titleIndex = it.getColumnIndex(CalendarContract.Events.TITLE)
            val descIndex = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val startIndex = it.getColumnIndex(CalendarContract.Events.DTSTART)
            
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val title = it.getString(titleIndex) ?: ""
                val description = it.getString(descIndex) ?: ""
                val startTime = it.getLong(startIndex)
                
                if (title.isNotBlank()) {
                    tasks.add(
                        TodoTask(
                            id = id,
                            eventId = id,
                            title = title,
                            description = description,
                            startTime = startTime
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return tasks
}

fun getCalendarId(context: android.content.Context): Long? {
    val projection = arrayOf(CalendarContract.Calendars._ID)
    
    val cursor = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        null,
        null,
        null
    )
    
    cursor?.use {
        if (it.moveToFirst()) {
            val idIndex = it.getColumnIndex(CalendarContract.Calendars._ID)
            return it.getLong(idIndex)
        }
    }
    
    return null
}

fun formatDate(timeInMillis: Long): String {
    val calendar = Calendar.getInstance().apply {
        this.timeInMillis = timeInMillis
    }
    
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    
    return String.format("%d月%d日 %02d:%02d", month, day, hour, minute)
}
