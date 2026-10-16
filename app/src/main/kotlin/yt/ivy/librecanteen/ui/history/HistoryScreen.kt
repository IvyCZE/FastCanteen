package yt.ivy.librecanteen.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.model.OrderRecord
import yt.ivy.librecanteen.core.model.Payment
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.components.DateBar
import yt.ivy.librecanteen.ui.lunch.formatMonth
import yt.ivy.librecanteen.ui.asString
import yt.ivy.librecanteen.ui.formatMoney
import yt.ivy.librecanteen.ui.formatShortDate
import yt.ivy.librecanteen.ui.theme.NumericStyle
import yt.ivy.librecanteen.ui.toUiText

data class HistoryState(
    val orders: List<OrderRecord>? = null,
    val payments: List<Payment>? = null,
    val loading: Boolean = true,
    val error: UiText? = null,
)

class HistoryViewModel(private val repository: CanteenRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val (orders, payments) = coroutineScope {
                launch { runCatching { repository.status() } }
                val o = async { runCatching { repository.orderHistory() } }
                val p = async { runCatching { repository.payments() } }
                o.await() to p.await()
            }
            _state.update {
                HistoryState(
                    orders = orders.getOrNull() ?: it.orders,
                    payments = payments.getOrNull() ?: it.payments,
                    loading = false,
                    error = (orders.exceptionOrNull() ?: payments.exceptionOrNull())?.toUiText(),
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(state: HistoryState, onRefresh: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.history_orders)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_payments)) })
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            val failed = state.error
            if (failed != null && state.orders == null && state.payments == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(failed.asString())
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                }
            } else if (tab == 0) {
                OrdersList(state.orders?.filter { YearMonth.from(it.date) == month })
            } else {
                PaymentsList(state.payments?.filter { YearMonth.from(it.date) == month })
            }
        }
        DateBar(
            label = formatMonth(month),
            previousDescription = stringResource(R.string.month_previous),
            nextDescription = stringResource(R.string.month_next),
            onPrevious = { monthText = month.minusMonths(1).toString() },
            onNext = { monthText = month.plusMonths(1).toString() },
        )
    }
}

@Composable
private fun Empty() {
    Text(
        stringResource(R.string.history_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun RecordCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun OrdersList(orders: List<OrderRecord>?) {
    if (orders != null && orders.isEmpty()) return Empty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(orders.orEmpty()) { order ->
            RecordCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatShortDate(order.date)} · ${order.label}",
                        style = MaterialTheme.typography.titleSmall.merge(NumericStyle),
                        modifier = Modifier.weight(1f),
                    )
                    order.price?.let {
                        Text(formatMoney(it), style = MaterialTheme.typography.titleSmall.merge(NumericStyle))
                    }
                }
                Text(order.dishes.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                val issued = stringResource(if (order.issuedCount >= order.orderedCount) R.string.history_issued else R.string.history_not_issued)
                Text(
                    "${stringResource(R.string.history_quantity, order.orderedCount)} · $issued",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaymentsList(payments: List<Payment>?) {
    if (payments != null && payments.isEmpty()) return Empty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(payments.orEmpty()) { payment ->
            RecordCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatShortDate(payment.date)} · ${payment.kind}",
                        style = MaterialTheme.typography.titleSmall.merge(NumericStyle),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatMoney(payment.amount),
                        style = MaterialTheme.typography.titleSmall.merge(NumericStyle),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    listOf(payment.method, payment.note).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
