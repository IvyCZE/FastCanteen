package yt.ivy.librecanteen.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import yt.ivy.librecanteen.core.model.Money
import yt.ivy.librecanteen.data.AppSettings
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.toUiText
import java.time.LocalDate

/** Offers are matched with prices by day and variant, because the exchange list itself shows no price. */
data class OfferKey(val date: LocalDate, val label: String)

data class ExchangeState(
    val offers: List<ExchangeOffer>? = null,
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
            runCatching { repository.exchangeOffers() }
                .onSuccess { offers ->
                    _state.update { it.copy(offers = offers, loading = false) }
                    loadPrices(offers)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.toUiText()) }
                }
            runCatching { repository.status() }
        }
    }

    private suspend fun loadPrices(offers: List<ExchangeOffer>) {
        for (date in offers.map { it.date }.distinct()) {
            val menu = runCatching { repository.menu(date) }.getOrNull() ?: continue
            val prices = offers.filter { it.date == date }.mapNotNull { offer ->
                menu.meals.firstOrNull { it.label == offer.label }?.price?.let { OfferKey(date, offer.label) to it }
            }
            _state.update { it.copy(prices = it.prices + prices) }
        }
    }

    fun take(offer: ExchangeOffer) {
        if (_state.value.taking != null) return
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
}
