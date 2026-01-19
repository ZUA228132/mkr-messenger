package com.pioneer.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String,
    val creatorId: String,
    val assigneeIds: List<String>,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val chatId: String? = null,
    val subtasks: List<Subtask> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val comments: List<TaskComment> = emptyList()
)

@Serializable
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    REVIEW,
    COMPLETED,
    CANCELLED
}

@Serializable
enum class TaskPriority {
    LOW, MEDIUM, HIGH, URGENT
}

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false
)

@Serializable
data class TaskComment(
    val id: String,
    val userId: String,
    val content: String,
    val timestamp: Long
)

@Serializable
data class TaskReport(
    val id: String,
    val taskId: String,
    val userId: String,
    val content: String,
    val hoursSpent: Float,
    val timestamp: Long,
    val attachments: List<Attachment> = emptyList()
)
