package com.pioneer.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FinanceRecord(
    val id: String,
    val type: FinanceType,
    val category: String,
    val amount: Double,
    val currency: String = "RUB",
    val description: String,
    val createdBy: String,
    val createdAt: Long,
    val attachments: List<Attachment> = emptyList(),
    val tags: List<String> = emptyList()
)

@Serializable
enum class FinanceType {
    INCOME,     // Доход
    EXPENSE,    // Расход
    TRANSFER    // Перевод
}

@Serializable
data class FinanceCategory(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: FinanceType,
    val parentId: String? = null
)

@Serializable
data class FinanceSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val balance: Double,
    val byCategory: Map<String, Double>,
    val periodStart: Long,
    val periodEnd: Long
)

@Serializable
data class Budget(
    val id: String,
    val categoryId: String,
    val limit: Double,
    val spent: Double,
    val periodType: BudgetPeriod,
    val startDate: Long
)

@Serializable
enum class BudgetPeriod {
    DAILY, WEEKLY, MONTHLY, YEARLY
}
