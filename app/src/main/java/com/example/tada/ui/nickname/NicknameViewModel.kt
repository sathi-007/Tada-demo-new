package com.example.tada.ui.nickname

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.NicknameRules
import com.example.tada.domain.model.Side
import com.example.tada.domain.repository.SelectionRepository
import com.example.tada.domain.usecase.SaveNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NicknameUiState(
    val side: Side,
    val address: String = "",
    val aqi: Int? = null,
    val nickname: String = "",
    val error: FailureReason? = null,
    val missing: Boolean = false,
    val saving: Boolean = false,
)

sealed interface NicknameEvent {
    data object Saved : NicknameEvent
}

@HiltViewModel
class NicknameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val selectionRepository: SelectionRepository,
    private val saveNickname: SaveNicknameUseCase,
) : ViewModel() {
    private val side = Side.valueOf(checkNotNull(savedStateHandle.get<String>("side")))

    private val _ui = MutableStateFlow(NicknameUiState(side = side))
    val ui: StateFlow<NicknameUiState> = _ui.asStateFlow()

    private val _events = Channel<NicknameEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val point = when (side) {
            Side.A -> selectionRepository.current().a
            Side.B -> selectionRepository.current().b
        }
        if (point == null) {
            _ui.update { it.copy(missing = true) }
        } else {
            _ui.update {
                it.copy(address = point.address, aqi = point.aqi, nickname = point.nickname)
            }
        }
    }

    fun onNicknameChange(value: String) {
        if (!NicknameRules.accept(value)) return
        _ui.update { it.copy(nickname = value, error = null) }
    }

    fun save() {
        val state = _ui.value
        if (state.saving || state.missing) return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            when (val result = saveNickname(side, state.nickname)) {
                is DomainResult.Ok -> {
                    _ui.update { it.copy(saving = false) }
                    _events.send(NicknameEvent.Saved)
                }
                is DomainResult.Err -> _ui.update { it.copy(saving = false, error = result.reason) }
            }
        }
    }
}
