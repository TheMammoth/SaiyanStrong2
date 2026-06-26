package com.saiyanstrong.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

sealed class HistoryItem {
    data class MonthHeader(val label: String, val count: Int) : HistoryItem()
    data class SessionCard(val session: WorkoutSession, val prCount: Int) : HistoryItem()
}

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        sessionRepository.getAllSessions()
            .onEach { sessions ->
                _uiState.value = HistoryUiState(
                    items = buildHistoryItems(sessions),
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { sessionRepository.deleteSession(sessionId) }
    }

    private fun buildHistoryItems(sessions: List<WorkoutSession>): List<HistoryItem> {
        val sorted = sessions.sortedByDescending { it.dateMs }
        val grouped = sorted.groupBy { session ->
            val cal = Calendar.getInstance().apply { timeInMillis = session.dateMs }
            "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}"
        }
        val result = mutableListOf<HistoryItem>()
        grouped.forEach { (_, groupSessions) ->
            val cal = Calendar.getInstance().apply { timeInMillis = groupSessions.first().dateMs }
            val monthName = MONTH_NAMES[cal.get(Calendar.MONTH)]
            val year = cal.get(Calendar.YEAR)
            result += HistoryItem.MonthHeader("$monthName $year", groupSessions.size)
            groupSessions.forEach { session ->
                result += HistoryItem.SessionCard(
                    session = session,
                    prCount = computePrCount(session, sessions)
                )
            }
        }
        return result
    }

    private fun computePrCount(session: WorkoutSession, allSessions: List<WorkoutSession>): Int {
        val olderSessions = allSessions.filter { it.dateMs < session.dateMs }
        return session.exerciseLogs.count { exerciseLog ->
            val sessionBest = exerciseLog.sets.maxOfOrNull { s ->
                if (s.reps == 1) s.weightKg else s.weightKg * (1.0 + s.reps / 30.0)
            } ?: return@count false
            val historicalBest = olderSessions
                .flatMap { s -> s.exerciseLogs.filter { it.exercise.id == exerciseLog.exercise.id } }
                .flatMap { it.sets }
                .maxOfOrNull { s -> if (s.reps == 1) s.weightKg else s.weightKg * (1.0 + s.reps / 30.0) }
                ?: 0.0
            sessionBest > historicalBest
        }
    }

    companion object {
        private val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
}
