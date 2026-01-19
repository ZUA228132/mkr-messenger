package com.pioneer.messenger.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.FinanceDao
import com.pioneer.messenger.data.local.FinanceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val financeDao: FinanceDao,
    private val authManager: AuthManager
) : ViewModel() {
    
    val records: Flow<List<FinanceRecordUi>> = financeDao.getAllRecords().map { entities ->
        entities.map { entity ->
            FinanceRecordUi(
                id = entity.id,
                type = entity.type,
                category = entity.category,
                amount = entity.amount,
                description = entity.description,
                createdAt = entity.createdAt
            )
        }
    }
    
    private val _summary = MutableStateFlow(FinanceSummaryUi())
    val summary: StateFlow<FinanceSummaryUi> = _summary
    
    init {
        loadSummary()
    }
    
    private fun loadSummary() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            
            val income = financeDao.getTotalIncome(monthStart, now) ?: 0.0
            val expense = financeDao.getTotalExpense(monthStart, now) ?: 0.0
            
            _summary.value = FinanceSummaryUi(
                totalIncome = income,
                totalExpense = expense,
                balance = income - expense
            )
        }
    }
    
    fun addRecord(type: String, category: String, amount: Double, description: String) {
        viewModelScope.launch {
            val currentUser = authManager.currentUser.first() ?: return@launch
            
            val record = FinanceEntity(
                id = UUID.randomUUID().toString(),
                type = type,
                category = category,
                amount = amount,
                currency = "RUB",
                description = description,
                createdBy = currentUser.id,
                createdAt = System.currentTimeMillis(),
                tags = ""
            )
            
            financeDao.insertRecord(record)
            loadSummary()
        }
    }
}
