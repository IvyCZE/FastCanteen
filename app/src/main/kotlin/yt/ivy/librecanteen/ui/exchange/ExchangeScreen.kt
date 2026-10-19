package yt.ivy.librecanteen.ui.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.model.ExchangeOffer
import yt.ivy.librecanteen.core.model.Money
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.asString
import yt.ivy.librecanteen.ui.formatDayHeader
import yt.ivy.librecanteen.ui.formatMoney
import yt.ivy.librecanteen.ui.lunch.DishLine
import yt.ivy.librecanteen.ui.theme.NumericStyle

@Composable
fun ExchangeScreen(
    state: ExchangeState,
    events: Flow<UiText>,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onTake: (ExchangeOffer) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(events) {
        events.collect { message ->
            val text = when (message) {
                is UiText.Res -> context.getString(message.id, *message.args.toTypedArray())
                is UiText.Raw -> message.text
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        val offers = state.offers
        val error = state.error
        when {
            error != null && offers == null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.asString(), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }
            offers == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            offers.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.exchange_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> OfferList(offers, state, onTake)
        }
    }
}

@Composable
private fun OfferList(offers: List<ExchangeOffer>, state: ExchangeState, onTake: (ExchangeOffer) -> Unit) {
    var confirming by remember { mutableStateOf<ExchangeOffer?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(offers, key = { it.path }) { offer ->
            OfferCard(
                offer = offer,
                price = state.prices[OfferKey(offer.date, offer.label)],
                flagged = offer.dishes.any { it.triggers(state.allergens) },
                busy = state.taking == offer,
                enabled = state.taking == null,
                onTake = { confirming = offer },
            )
        }
    }
    confirming?.let { offer ->
        val price = state.prices[OfferKey(offer.date, offer.label)]
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.exchange_take_title)) },
            text = {
                val label = "${offer.label}, ${formatDayHeader(offer.date)}"
                Text(
                    if (price != null) stringResource(R.string.exchange_take_text, label, formatMoney(price))
                    else stringResource(R.string.exchange_take_text_no_price, label),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; onTake(offer) }) { Text(stringResource(R.string.exchange_take)) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun OfferCard(
    offer: ExchangeOffer,
    price: Money?,
    flagged: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onTake: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(offer.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatDayHeader(offer.date),
                        style = MaterialTheme.typography.bodySmall.merge(NumericStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                price?.let { Text(formatMoney(it), style = MaterialTheme.typography.titleMedium.merge(NumericStyle)) }
            }
            offer.dishes.forEach { DishLine(it, flagged) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${stringResource(R.string.exchange_pickup_point, offer.pickupPoint)} · ${stringResource(R.string.exchange_pieces, offer.quantity)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onTake, enabled = enabled, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.exchange_take))
                }
            }
        }
    }
}
