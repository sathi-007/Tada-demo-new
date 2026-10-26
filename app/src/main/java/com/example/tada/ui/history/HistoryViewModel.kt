package com.example.tada.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.HistoryRow
import com.example.tada.domain.usecase.LoadHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val totalCount: Int = 0,
    val totalPrice: Int = 0,
    val rows: List<HistoryRow> = emptyList(),
    val error: FailureReason? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val loadHistory: LoadHistoryUseCase,
) : ViewModel() {
    private val _ui = MutableStateFlow(HistoryUiState())
    val ui: StateFlow<HistoryUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = HistoryUiState(loading = true)
            _ui.value = when (val result = loadHistory()) {
                is DomainResult.Ok -> HistoryUiState(
                    loading = false,
                    totalCount = result.value.totalCount,
                    totalPrice = result.value.totalPrice,
                    rows = result.value.rows,
                )
                is DomainResult.Err -> HistoryUiState(loading = false, error = result.reason)
            }
        }
    }
}
