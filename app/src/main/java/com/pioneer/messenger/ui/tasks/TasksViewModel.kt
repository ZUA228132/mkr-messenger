package com.pioneer.messenger.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.TaskDao
import com.pioneer.messenger.data.local.TaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val authManager: AuthManager
) : ViewModel() {
    
    val tasks: Flow<List<TaskUiModel>> = taskDao.getAllTasks().map { entities ->
        val currentUserId = authManager.currentUser.first()?.id ?: ""
        entities.map { entity ->
            TaskUiModel(
                id = entity.id,
                title = entity.title,
                description = entity.description,
                status = entity.status,
                priority = entity.priority,
                dueDate = entity.dueDate,
                isAssignedToMe = entity.assigneeIds.contains(currentUserId)
            )
        }
    }
    
    fun createTask(title: String, description: String, priority: String) {
        viewModelScope.launch {
            val currentUser = authManager.currentUser.first() ?: return@launch
            
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                creatorId = currentUser.id,
                assigneeIds = currentUser.id,
                status = "PENDING",
                priority = priority,
                dueDate = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                chatId = null,
                subtasks = "",
                attachments = ""
            )
            
            taskDao.insertTask(task)
        }
    }
    
    fun toggleTaskComplete(taskId: String) {
        viewModelScope.launch {
            val task = taskDao.getAllTasks().first().find { it.id == taskId } ?: return@launch
            
            val newStatus = if (task.status == "COMPLETED") "PENDING" else "COMPLETED"
            
            taskDao.updateTask(task.copy(
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }
}
