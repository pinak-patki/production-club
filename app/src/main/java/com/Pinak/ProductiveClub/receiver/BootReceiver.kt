package com.Pinak.ProductiveClub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.Pinak.ProductiveClub.data.AppDatabase
import com.Pinak.ProductiveClub.util.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val reminderManager = ReminderManager(context)
            val db = AppDatabase.getDatabase(context)
            val today = LocalDate.now().toString()

            CoroutineScope(Dispatchers.IO).launch {
                val tasks = db.taskDao().getTasksForDateSync(today)
                tasks.forEach { task ->
                    if (!task.isCompleted) {
                        reminderManager.scheduleReminder(task)
                    }
                }
            }
        }
    }
}