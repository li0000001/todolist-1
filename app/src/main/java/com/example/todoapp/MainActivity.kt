package com.example.todoapp

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TodoAppScreen(context = this)
        }
    }
}

@Composable
fun TodoAppScreen(context: Context) {
    val titleState = remember { mutableStateOf("") }
    val descriptionState = remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readCalendarGranted = permissions[android.Manifest.permission.READ_CALENDAR] ?: false
        val writeCalendarGranted = permissions[android.Manifest.permission.WRITE_CALENDAR] ?: false

        if (readCalendarGranted && writeCalendarGranted) {
            addTaskToCalendar(context, titleState.value, descriptionState.value)
        } else {
            Toast.makeText(context, "需要日历权限", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "我的待办事项",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = titleState.value,
                onValueChange = { titleState.value = it },
                label = { Text("输入任务内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = descriptionState.value,
                onValueChange = { descriptionState.value = it },
                label = { Text("输入备注信息 (可选)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (titleState.value.isEmpty()) {
                        Toast.makeText(context, "任务内容不能为空", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val hasReadPermission = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.READ_CALENDAR
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    val hasWritePermission = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.WRITE_CALENDAR
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasReadPermission && hasWritePermission) {
                        addTaskToCalendar(context, titleState.value, descriptionState.value)
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_CALENDAR,
                                android.Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("添加到日历并设置提醒")
            }
        }
    }
}

fun addTaskToCalendar(context: Context, title: String, description: String) {
    try {
        val contentResolver = context.contentResolver

        // 查询主日历ID
        val calendarUri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.IS_PRIMARY} = 1"

        val calendarCursor = contentResolver.query(
            calendarUri,
            projection,
            selection,
            null,
            null
        )

        var calendarId: Long = -1
        if (calendarCursor != null) {
            if (calendarCursor.moveToFirst()) {
                calendarId = calendarCursor.getLong(0)
            }
            calendarCursor.close()
        }

        if (calendarId == -1L) {
            Toast.makeText(context, "找不到主日历", Toast.LENGTH_SHORT).show()
            return
        }

        // 设置事件时间
        val calendar = Calendar.getInstance()
        val startTime = calendar.timeInMillis + (5 * 60 * 1000) // 5分钟后
        val endTime = calendar.timeInMillis + (15 * 60 * 1000) // 15分钟后

        // 创建事件
        val eventValues = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_TIMEZONE, Calendar.getInstance().timeZone.id)
        }

        val eventUri = contentResolver.insert(
            CalendarContract.Events.CONTENT_URI,
            eventValues
        )

        if (eventUri == null) {
            Toast.makeText(context, "添加事件失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取事件ID
        val eventId = eventUri.lastPathSegment?.toLongOrNull()

        if (eventId == null) {
            Toast.makeText(context, "获取事件ID失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 添加提醒
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 5) // 提前5分钟提醒
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }

        contentResolver.insert(
            CalendarContract.Reminders.CONTENT_URI,
            reminderValues
        )

        Toast.makeText(context, "任务已成功添加到系统日历！", Toast.LENGTH_SHORT).show()

    } catch (e: SecurityException) {
        Toast.makeText(context, "权限错误: ${e.message}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "错误: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
