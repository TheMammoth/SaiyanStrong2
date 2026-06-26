package com.saiyanstrong.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.usecase.GetEvolutionStageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class WeekBar(val label: String, val count: Int)

@HiltViewModel
class HomeViewModel @Inject constructor(
    getEvolutionStageUseCase: GetEvolutionStageUseCase,
    sessionRepository: SessionRepository
) : ViewModel() {

    val powerLevel: StateFlow<PowerLevel?> = getEvolutionStageUseCase.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weeklyBars: StateFlow<List<WeekBar>> = sessionRepository.getAllSessions()
        .map { sessions -> buildWeekBars(sessions.map { it.dateMs }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildWeekBars(sessionDates: List<Long>): List<WeekBar> =
        (7 downTo 0).map { weeksAgo ->
            val weekStart = Calendar.getInstance().apply {
                add(Calendar.WEEK_OF_YEAR, -weeksAgo)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val weekEnd = weekStart.timeInMillis + 7L * 24 * 60 * 60 * 1000
            val count = sessionDates.count { it >= weekStart.timeInMillis && it < weekEnd }
            val label = "${weekStart.get(Calendar.MONTH) + 1}/${weekStart.get(Calendar.DAY_OF_MONTH)}"
            WeekBar(label, count)
        }
}
