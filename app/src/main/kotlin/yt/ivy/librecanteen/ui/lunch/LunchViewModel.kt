package yt.ivy.librecanteen.ui.lunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.model.ActionType
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.data.AppSettings
import yt.ivy.librecanteen.data.CalendarMode
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.toUiText
import java.time.LocalDate
import java.time.YearMonth

data class MealKey(val day: LocalDate, val course: String, val label: String)

data class LunchState(
    /** The selected day in month view; in week view any day of the shown week. */
    val anchor: LocalDate,
    val days: Map<LocalDate, DayMenu> = emptyMap(),
    val loading: Boolean = true,
    /** Set when nothing could be loaded at all; partial failures are reported as events. */
    val error: UiText? = null,
    val busy: Set<MealKey> = emptySet(),
    /** Allergen codes the user selected in the canteen settings. */
    val allergens: Set<String> = emptySet(),
    val allergenNames: Map<String, String> = emptyMap(),
    /** Some days come from the cache, so nothing can be changed. */
    val offline: Boolean = false,
) {
    val weekdays: List<LocalDate> get() = LunchNav.weekdays(anchor)
    val month: YearMonth get() = YearMonth.from(anchor)
}

/** One-off message for the snackbar. [undo] reverses the change that was just made. */
data class LunchEvent(val message: UiText, val undo: (() -> Unit)? = null)

class LunchViewModel(
    private val repository: CanteenRepository,
    private val settings: AppSettings,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        LunchState(
            anchor = LunchNav.initialAnchor(today),
            allergens = settings.allergenCodes.value,
            allergenNames = settings.allergenNames,
        ),
    )
    val state: StateFlow<LunchState> = _state.asStateFlow()

    private val events = Channel<LunchEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var mode = settings.calendarMode.value
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.allergenCodes.collect { codes -> _state.update { it.copy(allergens = codes) } }
        }
        // Emits the current mode first, which doubles as the initial load.
        viewModelScope.launch {
            settings.calendarMode.collect {
                mode = it
                load()
            }
        }
    }

    /**
     * Loads what the current view shows. Week view reloads every day, because each action on the
     * page is signed by the server; month view reloads the selected day and fills in the rest
     * (for the calendar markers) only where nothing is known yet, unless [forceAll].
     */
    fun load(forceAll: Boolean = false) {
        loadJob?.cancel()
        val anchor = _state.value.anchor
        val visible = LunchNav.visibleDays(anchor, mode)
        val forced = if (mode == CalendarMode.WEEK || forceAll) visible.toSet() else setOf(anchor)
        val wanted = visible.filter { it in forced || it !in _state.value.days }
        _state.update { it.copy(loading = true, error = null) }

        loadJob = viewModelScope.launch {
            val gate = Semaphore(MAX_PARALLEL_REQUESTS)
            val results = coroutineScope {
                launch { refreshAccount() }
                wanted.map { day ->
                    async {
                        gate.withPermit {
                            val result = runCatching { repository.menu(day) }
                            result.getOrNull()?.let { menu -> _state.update { it.copy(days = it.days + (day to menu)) } }
                            day to result
                        }
                    }
                }.awaitAll()
            }
            if (_state.value.anchor != anchor) return@launch

            val failure = results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            // Only a lost connection falls back to old data; other failures must stay visible.
            val cached = if (failure is CanteenException.Network) {
                results.filter { it.second.isFailure }
                    .mapNotNull { (day, _) -> repository.cachedMenu(day)?.let { day to it } }.toMap()
            } else {
                emptyMap()
            }
            val shownLoaded = visible.any { it in _state.value.days } || cached.isNotEmpty()
            _state.update { current ->
                current.copy(
                    days = current.days + cached,
                    offline = cached.isNotEmpty(),
                    loading = false,
                    error = if (!shownLoaded && failure != null) failure.toUiText() else null,
                )
            }
            if (failure != null && shownLoaded && failure !is CanteenException.Network) {
                events.trySend(LunchEvent(UiText.Res(R.string.error_partial)))
            }
        }
    }

    private suspend fun refreshAccount() {
        runCatching { repository.status() }
        runCatching { repository.allergenSettings() }.getOrNull()?.let { allergens ->
            settings.setAllergenCodes(allergens.selectedCodes)
            val names = allergens.allergens.associate { it.code to it.name.substringBefore(" - ") }
            settings.allergenNames = names
            _state.update { it.copy(allergenNames = names) }
        }
    }

    private fun goTo(day: LocalDate) {
        if (day == _state.value.anchor) return
        _state.update { it.copy(anchor = day, error = null) }
        load()
    }

    fun previous() = goTo(LunchNav.previous(_state.value.anchor, mode))

    fun next() = goTo(LunchNav.next(_state.value.anchor, mode))

    fun previousMonth() = goTo(LunchNav.shiftMonth(_state.value.anchor, -1))

    fun nextMonth() = goTo(LunchNav.shiftMonth(_state.value.anchor, 1))

    fun select(day: LocalDate) = goTo(day)

    fun toggleMode() {
        settings.setCalendarMode(if (mode == CalendarMode.WEEK) CalendarMode.MONTH else CalendarMode.WEEK)
    }

    fun apply(day: LocalDate, meal: Meal) = apply(day, meal, offerUndo = true)

    fun saveNote(day: LocalDate, meal: Meal, note: String) {
        if (meal.noteAction == null) return
        runOperation(day, meal) {
            val updated = repository.setNote(day, meal, note)
            _state.update { it.copy(days = it.days + (day to updated)) }
            events.trySend(LunchEvent(UiText.Res(R.string.msg_note_saved)))
        }
    }

    fun putIntoExchange(day: LocalDate, meal: Meal, quantity: Int) {
        if (meal.exchangePut == null) return
        runOperation(day, meal) {
            val updated = repository.putIntoExchange(day, meal, quantity)
            _state.update { it.copy(days = it.days + (day to updated)) }
            events.trySend(LunchEvent(UiText.Res(R.string.msg_exchange_put, listOf(meal.label))))
        }
    }

    fun withdrawFromExchange(day: LocalDate, meal: Meal) {
        if (meal.exchangeWithdraw == null) return
        runOperation(day, meal) {
            val updated = repository.withdrawFromExchange(day, meal)
            _state.update { it.copy(days = it.days + (day to updated)) }
            events.trySend(LunchEvent(UiText.Res(R.string.msg_exchange_withdrawn, listOf(meal.label))))
        }
    }

    /** Takes over a day that changed elsewhere, e.g. after a meal was taken from the exchange. */
    fun replaceDay(menu: DayMenu) {
        _state.update { it.copy(days = it.days + (menu.date to menu)) }
    }

    private fun apply(day: LocalDate, meal: Meal, offerUndo: Boolean) {
        if (meal.action == null) return
        runOperation(day, meal) {
            val updated = repository.order(day, meal)
            _state.update { it.copy(days = it.days + (day to updated)) }
            launch { runCatching { repository.status() } }
            events.trySend(successEvent(day, meal, updated, offerUndo))
        }
    }

    private fun runOperation(day: LocalDate, meal: Meal, block: suspend CoroutineScope.() -> Unit) {
        val key = MealKey(day, meal.course, meal.label)
        if (_state.value.busy.isNotEmpty()) return // operations are signed per page, so run them one at a time
        _state.update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: CanteenException.SessionExpired) {
                events.trySend(LunchEvent(e.toUiText()))
                load()
            } catch (e: Exception) {
                events.trySend(LunchEvent(e.toUiText()))
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

    private fun successEvent(day: LocalDate, before: Meal, updated: DayMenu, offerUndo: Boolean): LunchEvent {
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
        return LunchEvent(message, target?.let { { apply(day, it, offerUndo = false) } })
    }

    private companion object {
        /** Be gentle with the school's server when filling a whole month. */
        const val MAX_PARALLEL_REQUESTS = 3
    }
}
