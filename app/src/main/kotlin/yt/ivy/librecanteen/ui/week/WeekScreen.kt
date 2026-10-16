package yt.ivy.librecanteen.ui.week

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.IsoFields
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Dish
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.ui.asString
import yt.ivy.librecanteen.ui.formatDayHeader
import yt.ivy.librecanteen.ui.formatDeadline
import yt.ivy.librecanteen.ui.formatMoney
import yt.ivy.librecanteen.ui.theme.NumericStyle

@Composable
fun WeekScreen(
    state: WeekState,
    events: kotlinx.coroutines.flow.Flow<WeekEvent>,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAction: (LocalDate, Meal) -> Unit,
) {
    val undoLabel = stringResource(R.string.undo)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(events) {
        events.collect { event ->
            val text = when (val m = event.message) {
                is yt.ivy.librecanteen.ui.UiText.Res -> context.getString(m.id, *m.args.toTypedArray())
                is yt.ivy.librecanteen.ui.UiText.Raw -> m.text
            }
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = if (event.undo != null) undoLabel else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) event.undo?.invoke()
        }
    }

    Column(Modifier.fillMaxSize()) {
        WeekHeader(state, onPrevious, onNext)
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            val error = state.error
            if (error != null && state.days.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error.asString(), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.weekdays, key = { it.toString() }) { day ->
                        DayCard(day, state.days[day], state.busy, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(state: WeekState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.week_previous))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.week_title, state.monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                state.credit?.let { "${stringResource(R.string.credit_label)} ${formatMoney(it)}" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall.merge(NumericStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.week_next))
        }
    }
}

@Composable
private fun DayCard(
    day: LocalDate,
    menu: DayMenu?,
    busy: Set<MealKey>,
    onAction: (LocalDate, Meal) -> Unit,
) {
    val isToday = day == LocalDate.now()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            if (isToday) 1.5.dp else 1.dp,
            if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDayHeader(day), style = MaterialTheme.typography.titleMedium.merge(NumericStyle))
                if (isToday) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.today),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            when {
                menu == null -> Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                menu.meals.isEmpty() -> Text(
                    stringResource(R.string.no_menu),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> menu.meals.forEachIndexed { index, meal ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    else Spacer(Modifier.padding(top = 8.dp))
                    MealRow(day, meal, busy.contains(MealKey(day, meal.course, meal.label)), onAction)
                }
            }
        }
    }
}

@Composable
private fun MealRow(day: LocalDate, meal: Meal, busy: Boolean, onAction: (LocalDate, Meal) -> Unit) {
    val ordered = meal.state == MealState.ORDERED || meal.state == MealState.ORDERED_LOCKED
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meal.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            meal.price?.let {
                Text(
                    formatMoney(it),
                    style = MaterialTheme.typography.labelLarge.merge(NumericStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        meal.dishes.forEach { DishLine(it) }

        meal.timings.orderUntil?.takeIf { meal.state == MealState.ORDERABLE || meal.state == MealState.REORDERABLE }?.let {
            Text(
                stringResource(R.string.order_until, formatDeadline(it)),
                style = MaterialTheme.typography.bodySmall.merge(NumericStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        meal.timings.cancelUntil?.takeIf { meal.state == MealState.ORDERED }?.let {
            Text(
                stringResource(R.string.cancel_until, formatDeadline(it)),
                style = MaterialTheme.typography.bodySmall.merge(NumericStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        meal.lockedReason?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ordered) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(
                        if (meal.state == MealState.ORDERED) R.string.state_ordered else R.string.state_ordered_locked,
                    ) + if (meal.quantity > 1) " · ${meal.quantity}×" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            ActionButton(meal, busy) { onAction(day, meal) }
        }
    }
}

@Composable
private fun DishLine(dish: Dish) {
    Row {
        Text(dish.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        if (dish.allergens.isNotEmpty()) {
            Text(
                "  " + dish.allergens.joinToString(","),
                style = MaterialTheme.typography.bodySmall.merge(NumericStyle),
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ActionButton(meal: Meal, busy: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    when (meal.state) {
        MealState.ORDERABLE -> Button(onClick = onClick, enabled = !busy, contentPadding = padding) {
            ButtonContent(busy, R.string.action_order)
        }
        MealState.ORDERED -> OutlinedButton(onClick = onClick, enabled = !busy, contentPadding = padding) {
            ButtonContent(busy, R.string.action_cancel_order)
        }
        MealState.REORDERABLE -> OutlinedButton(onClick = onClick, enabled = !busy, contentPadding = padding) {
            ButtonContent(busy, R.string.action_switch)
        }
        MealState.ORDERED_LOCKED, MealState.LOCKED -> Unit
    }
}

@Composable
private fun ButtonContent(busy: Boolean, label: Int) {
    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(label))
}
