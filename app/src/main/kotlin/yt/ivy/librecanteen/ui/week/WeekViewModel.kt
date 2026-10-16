package yt.ivy.librecanteen.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.model.ActionType
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.core.model.Money
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.toUiText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class MealKey(val day: LocalDate, val course: String, val label: String)

data class WeekState(
    val monday: LocalDate,
    val days: Map<LocalDate, DayMenu> = emptyMap(),
    val credit: Money? = null,
    val loading: Boolean = true,
    /** Set when nothing could be loaded at all; partial failures are reported as events. */
    val error: UiText? = null,
    val busy: Set<MealKey> = emptySet(),
) {
    val weekdays: List<LocalDate> get() = (0L..4L).map { monday.plusDays(it) }
}

/** One-off message for the snackbar. [undo] reverses the change that was just made. */
data class WeekEvent(val message: UiText, val undo: (() -> Unit)? = null)

class WeekViewModel(
    private val repository: CanteenRepository,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val _state = MutableStateFlow(WeekState(monday = initialMonday(today)))
    val state: StateFlow<WeekState> = _state.asStateFlow()

    private val events = Channel<WeekEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        val monday = _state.value.monday
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val (menus, status) = coroutineScope {
                val statusJob = async { runCatching { repository.status() } }
                val dayJobs = (0L..4L).map { offset ->
                    val day = monday.plusDays(offset)
                    async { day to runCatching { repository.menu(day) } }
                }
                dayJobs.awaitAll() to statusJob.await()
            }
            // The user may have moved to another week while this one was loading.
            if (_state.value.monday != monday) return@launch

            val loaded = menus.mapNotNull { (day, result) -> result.getOrNull()?.let { day to it } }.toMap()
            val failure = menus.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            _state.update { current ->
                current.copy(
                    days = current.days + loaded,
                    credit = status.getOrNull()?.credit ?: current.credit,
                    loading = false,
                    error = if (loaded.isEmpty() && failure != null) failure.toUiText() else null,
                )
            }
            if (failure != null && loaded.isNotEmpty()) {
                events.trySend(WeekEvent(UiText.Res(R.string.error_partial)))
            }
        }
    }

    fun showWeek(monday: LocalDate) {
        _state.update { WeekState(monday = monday, credit = it.credit) }
        load()
    }

    fun previousWeek() = showWeek(_state.value.monday.minusWeeks(1))

    fun nextWeek() = showWeek(_state.value.monday.plusWeeks(1))

    fun apply(day: LocalDate, meal: Meal) = apply(day, meal, offerUndo = true)

    private fun apply(day: LocalDate, meal: Meal, offerUndo: Boolean) {
        val key = MealKey(day, meal.course, meal.label)
        if (_state.value.busy.isNotEmpty()) return // operations are signed per page, so run them one at a time
        _state.update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            try {
                val updated = repository.order(day, meal)
                _state.update { it.copy(days = it.days + (day to updated)) }
                launch { runCatching { repository.status() }.getOrNull()?.let { s -> _state.update { it.copy(credit = s.credit) } } }
                events.trySend(successEvent(day, meal, updated, offerUndo))
            } catch (e: CancellationException) {
                throw e
            } catch (e: CanteenException.SessionExpired) {
                events.trySend(WeekEvent(e.toUiText()))
                load()
            } catch (e: Exception) {
                events.trySend(WeekEvent(e.toUiText()))
                // The page may have changed under us (deadline passed, limit reached); show the truth.
                refreshDay(day)
            } finally {
                _state.update { it.copy(busy = it.busy - key) }
            }
        }
    }

    private suspend fun refreshDay(day: LocalDate) {
        runCatching { repository.menu(day) }.getOrNull()?.let { menu ->
            _state.update { it.copy(days = it.days + (day to menu)) }
        }
    }

    private fun successEvent(day: LocalDate, before: Meal, updated: DayMenu, offerUndo: Boolean): WeekEvent {
        val name = before.label
        val message = when (before.action?.type) {
            ActionType.DELETE -> UiText.Res(R.string.msg_cancelled, listOf(name))
            ActionType.REORDER -> UiText.Res(R.string.msg_switched, listOf(name))
            else -> UiText.Res(R.string.msg_ordered, listOf(name))
        }
        val opposite = when (before.action?.type) {
            ActionType.DELETE -> MealState.ORDERABLE
            ActionType.MAKE -> MealState.ORDERED
            else -> null
        }
        val target = opposite?.takeIf { offerUndo }?.let { wanted ->
            updated.meals.firstOrNull { it.course == before.course && it.label == before.label && it.state == wanted }
        }
        return WeekEvent(message, target?.let { { apply(day, it, offerUndo = false) } })
    }

    private companion object {
        /** From Saturday on, the interesting week is the next one. */
        fun initialMonday(today: LocalDate): LocalDate {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return if (today.dayOfWeek >= DayOfWeek.SATURDAY) monday.plusWeeks(1) else monday
        }
    }
}
