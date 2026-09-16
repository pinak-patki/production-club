package com.Pinak.ProductiveClub.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Pinak.ProductiveClub.data.Task
import com.Pinak.ProductiveClub.data.TaskDao
import com.Pinak.ProductiveClub.util.ReminderManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TaskViewModel(
    private val taskDao: TaskDao,
    private val reminderManager: ReminderManager
) : ViewModel() {

    private val _currentDate = MutableStateFlow(LocalDate.now().toString())
    val currentDate: StateFlow<String> = _currentDate

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasksForToday: StateFlow<List<Task>> = _currentDate
        .flatMapLatest { date -> taskDao.getTasksForDate(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refreshDate() {
        val now = LocalDate.now().toString()
        if (_currentDate.value != now) {
            _currentDate.value = now
        }
    }

    fun addTask(
        title: String,
        startTime: String,
        endTime: String,
        priority: String = "Medium",
        category: String = "General"
    ) {
        viewModelScope.launch {
            val duration = calculateDurationMinutes(startTime, endTime)
            val task = Task(
                title = title,
                startTime = startTime,
                endTime = endTime,
                durationMinutes = duration,
                date = _currentDate.value,
                priority = priority,
                category = category
            )
            val id = taskDao.insertTask(task)
            reminderManager.scheduleReminder(task.copy(id = id.toInt()))
        }
    }

    fun addTask(
        title: String,
        startTime: String,
        durationMinutes: Int,
        priority: String = "Medium",
        category: String = "General"
    ) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                startTime = startTime,
                durationMinutes = durationMinutes,
                date = _currentDate.value,
                priority = priority,
                category = category
            )
            val id = taskDao.insertTask(task)
            reminderManager.scheduleReminder(task.copy(id = id.toInt()))
        }
    }

    fun calculateDurationMinutes(startTime: String, endTime: String): Int {
        return try {
            val startParts = startTime.split(":").map { it.toInt() }
            val endParts = endTime.split(":").map { it.toInt() }
            val startMins = startParts[0] * 60 + startParts[1]
            var endMins = endParts[0] * 60 + endParts[1]
            if (endMins < startMins) endMins += 24 * 60
            val diff = endMins - startMins
            if (diff > 0) diff else 60
        } catch (_: Exception) {
            60
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            taskDao.updateTask(task)
            reminderManager.cancelReminder(task)
            if (!task.isCompleted) {
                reminderManager.scheduleReminder(task)
            }
        }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted)
            taskDao.updateTask(updatedTask)
            if (updatedTask.isCompleted) {
                reminderManager.cancelReminder(updatedTask)
            } else {
                reminderManager.scheduleReminder(updatedTask)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
            reminderManager.cancelReminder(task)
        }
    }
}
