package com.example.tada.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tada.domain.model.Booking
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.HistoryRow
import com.example.tada.domain.repository.RestoreBookingQueue
import com.example.tada.domain.usecase.LoadHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val totalCount: Int = 0,
    val totalPrice: Int = 0,
    val rows: List<HistoryRow> = emptyList(),
    val error: FailureReason? = null,
)

sealed interface HistoryEvent {
    data object ReturnToMap : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val loadHistory: LoadHistoryUseCase,
    private val restoreQueue: RestoreBookingQueue,
) : ViewModel() {
    private val _ui = MutableStateFlow(HistoryUiState())
    val ui: StateFlow<HistoryUiState> = _ui.asStateFlow()

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var returning = false

    init {
        refresh()
    }

    fun onRowSelected(row: HistoryRow) {
        if (returning) return
        returning = true
        restoreQueue.submit(
            Booking(
                id = row.id,
                timestamp = "",
                price = 0,
                a = row.a,
                b = row.b,
            ),
        )
        viewModelScope.launch { _events.send(HistoryEvent.ReturnToMap) }
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
