package yt.ivy.librecanteen.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.ExchangeOffer
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.Money
import yt.ivy.librecanteen.core.model.exchangedQuantity
import yt.ivy.librecanteen.data.AppSettings
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.toUiText
import java.time.DayOfWeek
import java.time.LocalDate

/** Offers are matched with prices by day and variant, because the exchange list itself shows no price. */
data class OfferKey(val date: LocalDate, val label: String)

/** A meal of the user's own that is currently offered in the exchange. */
data class OwnOffer(val day: LocalDate, val meal: Meal)

data class ExchangeState(
    val offers: List<ExchangeOffer>? = null,
    val mine: List<OwnOffer> = emptyList(),
    val withdrawing: OwnOffer? = null,
    val prices: Map<OfferKey, Money> = emptyMap(),
    val loading: Boolean = true,
    val error: UiText? = null,
    val taking: ExchangeOffer? = null,
    val allergens: Set<String> = emptySet(),
)

class ExchangeViewModel(
    private val repository: CanteenRepository,
    private val settings: AppSettings,
    /** Called with the refreshed day after a meal was taken, so other screens show the new order. */
    private val onDayChanged: (DayMenu) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(ExchangeState(allergens = settings.allergenCodes.value))
    val state: StateFlow<ExchangeState> = _state.asStateFlow()

    private val events = Channel<UiText>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.allergenCodes.collect { codes -> _state.update { it.copy(allergens = codes) } }
        }
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            val offers = runCatching { repository.exchangeOffers() }
            offers.onSuccess { found -> _state.update { it.copy(offers = found) } }
            offers.exceptionOrNull()?.let { e ->
                if (e is CancellationException) throw e
                _state.update { it.copy(error = e.toUiText()) }
            }
            loadOwnOffersAndPrices(offers.getOrNull().orEmpty())
            _state.update { it.copy(loading = false) }
            runCatching { repository.status() }
        }
    }

    /**
     * The exchange list never shows the user's own offers, so they are found in the upcoming days' menus. The same
     * menus supply the prices, which the exchange list lacks.
     */
    private suspend fun loadOwnOffersAndPrices(offers: List<ExchangeOffer>) {
        val today = LocalDate.now()
        val upcoming = (0..SCAN_DAYS).map { today.plusDays(it.toLong()) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        val days = (upcoming + offers.map { it.date }).distinct()

        val gate = Semaphore(MAX_PARALLEL_REQUESTS)
        val menus = coroutineScope {
            days.map { day -> async { gate.withPermit { runCatching { repository.menu(day) }.getOrNull() } } }.awaitAll()
        }.filterNotNull()

        val mine = menus.flatMap { menu ->
            menu.meals.filter { it.exchangedQuantity > 0 }.map { OwnOffer(menu.date, it) }
        }
        val prices = menus.flatMap { menu ->
            offers.filter { it.date == menu.date }.mapNotNull { offer ->
                menu.meals.firstOrNull { it.label == offer.label }?.price?.let { OfferKey(menu.date, offer.label) to it }
            }
        }.toMap()
        _state.update { it.copy(mine = mine, prices = prices) }
    }

    fun take(offer: ExchangeOffer) {
        if (_state.value.taking != null || _state.value.withdrawing != null) return
        _state.update { it.copy(taking = offer) }
        viewModelScope.launch {
            try {
                val updated = repository.takeFromExchange(offer)
                onDayChanged(updated)
                events.trySend(UiText.Res(R.string.msg_exchange_taken, listOf(offer.label)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                events.trySend(e.toUiText())
            } finally {
                _state.update { it.copy(taking = null) }
            }
            // Whatever happened, the list is stale now: someone else may have been faster, or the take went through.
            load()
        }
    }

    fun withdraw(own: OwnOffer) {
        if (_state.value.taking != null || _state.value.withdrawing != null) return
        _state.update { it.copy(withdrawing = own) }
        viewModelScope.launch {
            try {
                val updated = repository.withdrawFromExchange(own.day, own.meal)
                onDayChanged(updated)
                events.trySend(UiText.Res(R.string.msg_exchange_withdrawn, listOf(own.meal.label)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                events.trySend(e.toUiText())
            } finally {
                _state.update { it.copy(withdrawing = null) }
            }
            load()
        }
    }

    private companion object {
        /** Own offers are looked for this many days ahead (a meal can only be offered once it cannot be cancelled). */
        const val SCAN_DAYS = 7

        /** Be gentle with the school's server. */
        const val MAX_PARALLEL_REQUESTS = 3
    }
}
