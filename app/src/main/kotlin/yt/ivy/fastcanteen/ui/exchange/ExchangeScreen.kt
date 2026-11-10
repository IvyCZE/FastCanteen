package yt.ivy.fastcanteen.ui.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.time.LocalDate
import yt.ivy.fastcanteen.R
import yt.ivy.fastcanteen.core.model.Dish
import yt.ivy.fastcanteen.core.model.ExchangeOffer
import yt.ivy.fastcanteen.core.model.exchangedQuantity
import yt.ivy.fastcanteen.core.model.Money
import yt.ivy.fastcanteen.ui.UiText
import yt.ivy.fastcanteen.ui.components.TopBarPullToRefresh
import yt.ivy.fastcanteen.ui.asString
import yt.ivy.fastcanteen.ui.formatDayHeader
import yt.ivy.fastcanteen.ui.formatMoney
import yt.ivy.fastcanteen.ui.lunch.DishLine
import yt.ivy.fastcanteen.ui.theme.NumericStyle

@Composable
fun ExchangeScreen(
    state: ExchangeState,
    events: Flow<UiText>,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onTake: (ExchangeOffer) -> Unit,
    onWithdraw: (OwnOffer) -> Unit,
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

    TopBarPullToRefresh(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        val offers = state.offers
        val error = state.error
        when {
            error != null && offers == null && state.mine.isEmpty() -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.asString(), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }
            offers == null && state.mine.isEmpty() -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            offers.isNullOrEmpty() && state.mine.isEmpty() -> Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.exchange_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> OfferList(offers.orEmpty(), state, onTake, onWithdraw)
        }
    }
}

@Composable
private fun OfferList(
    offers: List<ExchangeOffer>,
    state: ExchangeState,
    onTake: (ExchangeOffer) -> Unit,
    onWithdraw: (OwnOffer) -> Unit,
) {
    var confirming by remember { mutableStateOf<ExchangeOffer?>(null) }
    val idle = state.taking == null && state.withdrawing == null
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.mine.isNotEmpty()) {
            item(key = "mine-heading") { SectionHeading(R.string.exchange_section_mine) }
            items(state.mine, key = { "mine-${it.day}-${it.meal.label}" }) { own ->
                ExchangeCard(
                    date = own.day,
                    price = own.meal.price,
                    dishes = own.meal.dishes,
                    allergens = state.allergens,
                    detail = stringResource(R.string.exchange_pieces, own.meal.exchangedQuantity),
                    actionLabel = R.string.action_exchange_withdraw,
                    outlined = true,
                    busy = state.withdrawing == own,
                    enabled = idle,
                    onAction = { onWithdraw(own) },
                )
            }
            item(key = "others-heading") { SectionHeading(R.string.exchange_section_others) }
        }
        if (offers.isEmpty()) {
            item(key = "others-empty") {
                Text(
                    stringResource(R.string.exchange_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        items(offers, key = { it.path }) { offer ->
            ExchangeCard(
                date = offer.date,
                price = state.prices[OfferKey(offer.date, offer.label)],
                dishes = offer.dishes,
                allergens = state.allergens,
                detail = "${stringResource(R.string.exchange_pickup_point, offer.pickupPoint)} · ${stringResource(R.string.exchange_pieces, offer.quantity)}",
                actionLabel = R.string.exchange_take,
                outlined = false,
                busy = state.taking == offer,
                enabled = idle,
                onAction = { confirming = offer },
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
private fun SectionHeading(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun ExchangeCard(
    date: LocalDate,
    price: Money?,
    dishes: List<Dish>,
    allergens: Set<String>,
    detail: String,
    actionLabel: Int,
    outlined: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (outlined) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDayHeader(date),
                    style = MaterialTheme.typography.titleMedium.merge(NumericStyle),
                    modifier = Modifier.weight(1f),
                )
                price?.let { Text(formatMoney(it), style = MaterialTheme.typography.titleMedium.merge(NumericStyle)) }
            }
            dishes.forEach { DishLine(it, allergens) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                // Own offers sit on the tinted card, so their button uses the inverted colors.
                val colors = if (outlined) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
                Button(
                    onClick = onAction,
                    enabled = enabled,
                    colors = colors,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(actionLabel))
                }
            }
        }
    }
}
