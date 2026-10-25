package com.example.tada.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tada.domain.model.BookingPresentation
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.usecase.BookRideUseCase
import com.example.tada.domain.usecase.PresentBookingUseCase
import com.example.tada.domain.usecase.ResetSelectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class BookingUiState(
    val loading: Boolean = true,
    val presentation: BookingPresentation? = null,
    val price: Int? = null,
    val error: FailureReason? = null,
)

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val presentBooking: PresentBookingUseCase,
    private val bookRide: BookRideUseCase,
    private val resetSelection: ResetSelectionUseCase,
) : ViewModel() {
    private val _ui = MutableStateFlow(BookingUiState())
    val ui: StateFlow<BookingUiState> = _ui.asStateFlow()

    init {
        book()
    }

    fun retry() {
        book()
    }

    fun leave(onDone: () -> Unit) {
        viewModelScope.launch {
            resetSelection()
            onDone()
        }
    }

    private fun book() {
        viewModelScope.launch {
            val presentation = presentBooking()
            if (presentation == null) {
                _ui.value = BookingUiState(loading = false, error = FailureReason.NotReady)
                return@launch
            }
            _ui.value = BookingUiState(loading = true, presentation = presentation)
            _ui.value = when (val result = bookRide()) {
                is DomainResult.Ok -> BookingUiState(
                    loading = false,
                    presentation = presentation,
                    price = result.value.price,
                )
                is DomainResult.Err -> BookingUiState(
                    loading = false,
                    presentation = presentation,
                    error = result.reason,
                )
            }
        }
    }
}
