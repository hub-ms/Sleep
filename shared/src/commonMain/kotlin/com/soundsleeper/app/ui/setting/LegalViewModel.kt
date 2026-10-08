package com.soundsleeper.app.ui.setting

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.soundsleeper.app.domain.model.LegalDocument
import com.soundsleeper.app.domain.repository.LegalRepository
import com.soundsleeper.app.enum_.LegalType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface LegalUiState {
    data object Loading : LegalUiState
    data class Success(val document: LegalDocument) : LegalUiState
    data object Error : LegalUiState
}

class LegalViewModel(
    private val type: LegalType,
    private val repository: LegalRepository,
) : ScreenModel {
    private val _state = MutableStateFlow<LegalUiState>(LegalUiState.Loading)
    val state: StateFlow<LegalUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = LegalUiState.Loading
        screenModelScope.launch {
            _state.value = try {
                LegalUiState.Success(repository.load(type))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                LegalUiState.Error
            }
        }
    }
}