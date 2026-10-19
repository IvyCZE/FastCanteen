package yt.ivy.librecanteen.ui.lunch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Dish
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.core.model.exchangedQuantity
import yt.ivy.librecanteen.core.model.triggers
import yt.ivy.librecanteen.data.CalendarMode
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.asString
import yt.ivy.librecanteen.ui.components.DateBar
import yt.ivy.librecanteen.ui.components.horizontalSwipe
import yt.ivy.librecanteen.ui.formatDayHeader
import yt.ivy.librecanteen.ui.formatDeadline
import yt.ivy.librecanteen.ui.formatMoney
import yt.ivy.librecanteen.ui.theme.NumericStyle
import yt.ivy.librecanteen.ui.theme.SwapHorizIcon
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun LunchScreen(
    state: LunchState,
    mode: CalendarMode,
    events: Flow<LunchEvent>,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onPickMonth: (YearMonth) -> Unit,
    onInitialScroll: () -> Boolean,
    onToggleMode: () -> Unit,
    onAction: (LocalDate, Meal) -> Unit,
    onNote: (LocalDate, Meal, String) -> Unit,
    onExchangePut: (LocalDate, Meal, Int) -> Unit,
    onExchangeWithdraw: (LocalDate, Meal) -> Unit,
) {
    val undoLabel = stringResource(R.string.undo)
    val context = LocalContext.current
    LaunchedEffect(events) {
        events.collect { event ->
            val text = when (val m = event.message) {
                is UiText.Res -> context.getString(m.id, *m.args.toTypedArray())
                is UiText.Raw -> m.text
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

    val listState = rememberLazyListState()
    LaunchedEffect(mode) {
        // Only the first time the week list is shown after the app started.
        if (mode == CalendarMode.WEEK) {
            val index = state.week.indexOf(LocalDate.now())
            if (index >= 0 && onInitialScroll()) listState.scrollToItem(index)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (state.offline) {
            Text(
                stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    state = listState,
                    // Dragging sideways moves a day (month view) or a week; the calendar has its own swipe for months.
                    modifier = Modifier.fillMaxSize().horizontalSwipe(onPrevious, onNext),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (mode == CalendarMode.WEEK) {
                        items(state.week, key = { it.toString() }) { day ->
                            DaySection(day, state.days[day], state, showHeader = true, onAction, onNote, onExchangePut, onExchangeWithdraw)
                        }
                    } else {
                        item(key = "calendar") {
                            MonthCalendar(state, onPreviousMonth, onNextMonth, onSelect, onPickMonth)
                        }
                        item(key = "day-${state.anchor}") {
                            DaySection(state.anchor, state.days[state.anchor], state, showHeader = false, onAction, onNote, onExchangePut, onExchangeWithdraw)
                        }
                    }
                }
            }
        }
        val barLabel = if (mode == CalendarMode.WEEK) {
            val first = state.week.first()
            val last = state.week.last()
            "${first.dayOfMonth}.${first.monthValue}. – ${last.dayOfMonth}.${last.monthValue}."
        } else {
            formatDayHeader(state.anchor).replaceFirst(' ', ',').replace("\u00a0", " ")
        }
        DateBar(
            label = barLabel,
            previousDescription = stringResource(if (mode == CalendarMode.WEEK) R.string.week_previous else R.string.day_previous),
            nextDescription = stringResource(if (mode == CalendarMode.WEEK) R.string.week_next else R.string.day_next),
            onPrevious = onPrevious,
            onNext = onNext,
            longClickDescription = stringResource(R.string.toggle_calendar_mode),
            onLongClick = onToggleMode,
        )
    }
}

@Composable
private fun MonthCalendar(
    state: LunchState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onPickMonth: (YearMonth) -> Unit,
) {
    val today = LocalDate.now()
    var picking by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().horizontalSwipe(onPrevious = onPreviousMonth, onNext = onNextMonth),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.month_pick)) { picking = true }
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(formatMonth(state.month), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LunchNav.monthCells(state.month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCircle(day, state.days[day], selected = day == state.anchor, today = day == today, onSelect)
                        } else {
                            Spacer(Modifier.size(CIRCLE_SIZE))
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        MonthPickerDialog(state.month, onDismiss = { picking = false }) { month ->
            picking = false
            onPickMonth(month)
        }
    }
}

private val CIRCLE_SIZE = 40.dp

@Composable
private fun DayCircle(day: LocalDate, menu: DayMenu?, selected: Boolean, today: Boolean, onSelect: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val weekend = LunchNav.isWeekend(day)
    val ordered = menu?.meals?.any { it.state == MealState.ORDERED || it.state == MealState.ORDERED_LOCKED } == true
    val hasMenu = menu?.meals?.isNotEmpty() == true

    val background = when {
        selected -> colors.primary
        hasMenu -> colors.surfaceContainerHighest
        else -> Color.Transparent
    }
    val content = when {
        selected -> colors.onPrimary
        !hasMenu && (weekend || menu != null) -> colors.outline
        else -> colors.onSurface
    }
    Box(
        Modifier
            .size(CIRCLE_SIZE)
            .clip(CircleShape)
            .background(background)
            .then(if (today && !selected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
            .clickable { onSelect(day) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.merge(NumericStyle),
            color = content,
        )
        if (ordered) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.onPrimary else colors.primary),
            )
        }
    }
}

@Composable
private fun MonthPickerDialog(current: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    var year by remember { mutableIntStateOf(current.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.year_previous))
                }
                Text(year.toString(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { year++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.year_next))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { number ->
                            val month = YearMonth.of(year, number)
                            val selected = month == current
                            Surface(
                                onClick = { onPick(month) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    java.time.Month.of(number).getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DaySection(
    day: LocalDate,
    menu: DayMenu?,
    state: LunchState,
    showHeader: Boolean,
    onAction: (LocalDate, Meal) -> Unit,
    onNote: (LocalDate, Meal, String) -> Unit,
    onExchangePut: (LocalDate, Meal, Int) -> Unit,
    onExchangeWithdraw: (LocalDate, Meal) -> Unit,
) {
    val isToday = day == LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                Text(formatDayHeader(day), style = MaterialTheme.typography.titleMedium.merge(NumericStyle))
                if (isToday) {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        when {
            menu == null -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            menu.meals.isEmpty() -> Text(
                stringResource(R.string.no_menu),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            else -> menu.meals.forEach { meal ->
                MealCard(
                    day = day,
                    meal = meal,
                    busy = state.busy.contains(MealKey(day, meal.course, meal.label)),
                    offline = state.offline,
                    allergens = state.allergens,
                    allergenNames = state.allergenNames,
                    onAction = onAction,
                    onNote = onNote,
                    onExchangePut = onExchangePut,
                    onExchangeWithdraw = onExchangeWithdraw,
                )
            }
        }
    }
}

@Composable
private fun MealCard(
    day: LocalDate,
    meal: Meal,
    busy: Boolean,
    offline: Boolean,
    allergens: Set<String>,
    allergenNames: Map<String, String>,
    onAction: (LocalDate, Meal) -> Unit,
    onNote: (LocalDate, Meal, String) -> Unit,
    onExchangePut: (LocalDate, Meal, Int) -> Unit,
    onExchangeWithdraw: (LocalDate, Meal) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    var showPut by remember { mutableStateOf(false) }
    val ordered = meal.state == MealState.ORDERED || meal.state == MealState.ORDERED_LOCKED
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (ordered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.label, style = MaterialTheme.typography.titleMedium)
                if (ordered) {
                    if (meal.inExchange) {
                        Icon(SwapHorizIcon, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                        Text(
                            exchangeLabel(meal),
                            style = MaterialTheme.typography.titleSmall.merge(NumericStyle),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    } else {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.state_ordered),
                            modifier = Modifier.padding(start = 6.dp).size(20.dp),
                        )
                    }
                    if (meal.quantity > 1) {
                        Text("${meal.quantity}×", style = MaterialTheme.typography.titleSmall.merge(NumericStyle), modifier = Modifier.padding(start = 2.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                meal.price?.let { Text(formatMoney(it), style = MaterialTheme.typography.titleMedium.merge(NumericStyle)) }
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.meal_details),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            val flagged = meal.triggers(allergens)
            meal.dishes.forEach { DishLine(it, flagged) }

            meal.lockedReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    if (meal.noteAction != null && !offline) {
                        NoteField(saved = meal.note.orEmpty(), enabled = !busy) { onNote(day, meal, it) }
                    }
                }
                ActionButton(
                    meal = meal,
                    busy = busy || offline,
                    onClick = { onAction(day, meal) },
                    onExchangePut = { showPut = true },
                    onExchangeWithdraw = { onExchangeWithdraw(day, meal) },
                )
            }
        }
    }
    if (showInfo) MealInfoDialog(meal, allergenNames) { showInfo = false }
    meal.exchangePut?.let { put ->
        if (showPut) {
            ExchangePutDialog(meal.label, put.maxQuantity, onDismiss = { showPut = false }) { quantity ->
                showPut = false
                onExchangePut(day, meal, quantity)
            }
        }
    }
}

/** "In the exchange", with the offered share when only part of several pieces is in it. */
@Composable
private fun exchangeLabel(meal: Meal): String {
    val label = stringResource(R.string.in_exchange)
    val offered = meal.exchangedQuantity
    return if (meal.quantity > 1 && offered in 1 until meal.quantity) "$label $offered/${meal.quantity}" else label
}

@Composable
internal fun DishLine(dish: Dish, flagged: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            dish.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (flagged) FontWeight.Bold else FontWeight.Normal,
            color = if (flagged) MaterialTheme.colorScheme.error else Color.Unspecified,
            modifier = Modifier.weight(1f),
        )
        if (dish.allergens.isNotEmpty()) {
            Text(
                dish.allergens.joinToString(","),
                style = MaterialTheme.typography.bodySmall.merge(NumericStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

/** Underlined note line; a check button appears once the text differs from what is stored. */
@Composable
private fun NoteField(saved: String, enabled: Boolean, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    val dirty = text.trim() != saved
    TextField(
        value = text,
        onValueChange = { text = it.replace("<", "").replace('\n', ' ').take(NOTE_MAX_LENGTH) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text(stringResource(R.string.note_hint), style = MaterialTheme.typography.bodyMedium) },
        trailingIcon = {
            if (dirty) {
                IconButton(onClick = { onSave(text.trim()) }) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.note_save))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (dirty) onSave(text.trim()) }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val NOTE_MAX_LENGTH = 200

@Composable
private fun ActionButton(
    meal: Meal,
    busy: Boolean,
    onClick: () -> Unit,
    onExchangePut: () -> Unit,
    onExchangeWithdraw: () -> Unit,
) {
    val padding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    // Offered only on orders the canteen no longer lets you cancel, so it never competes with the buttons below.
    if (meal.exchangeWithdraw != null) {
        OutlinedButton(onClick = onExchangeWithdraw, enabled = !busy, contentPadding = padding) {
            ButtonContent(busy, R.string.action_exchange_withdraw)
        }
        return
    }
    if (meal.exchangePut != null) {
        OutlinedButton(onClick = onExchangePut, enabled = !busy, contentPadding = padding) {
            ButtonContent(busy, R.string.action_exchange_put)
        }
        return
    }
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

@Composable
private fun ExchangePutDialog(label: String, max: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var quantity by remember { mutableIntStateOf(max) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exchange_put_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.exchange_put_text, label))
                if (max > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.exchange_quantity), modifier = Modifier.weight(1f))
                        TextButton(onClick = { quantity = (quantity - 1).coerceAtLeast(1) }) { Text("−") }
                        Text(quantity.toString(), style = MaterialTheme.typography.titleMedium.merge(NumericStyle))
                        TextButton(onClick = { quantity = (quantity + 1).coerceAtMost(max) }) { Text("+") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(quantity) }) { Text(stringResource(R.string.exchange_put_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun MealInfoDialog(meal: Meal, allergenNames: Map<String, String>, onDismiss: () -> Unit) {
    val timings = meal.timings
    val codes = meal.dishes.flatMap { it.allergens }.distinct()
        .sortedWith(compareBy({ it.takeWhile(Char::isDigit).toIntOrNull() ?: 99 }, { it }))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meal.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                timings.orderUntil?.let { InfoLine(stringResource(R.string.info_order_until), formatDeadline(it)) }
                timings.cancelUntil?.let { InfoLine(stringResource(R.string.info_cancel_until), formatDeadline(it)) }
                if (timings.pickupFrom != null && timings.pickupTo != null) {
                    InfoLine(stringResource(R.string.info_pickup), "${formatTime(timings.pickupFrom!!)} – ${formatTime(timings.pickupTo!!)}")
                }
                if (timings.ordersCount != null && timings.ordersLimit != null) {
                    InfoLine(stringResource(R.string.info_orders), "${timings.ordersCount} / ${timings.ordersLimit}")
                }
                Spacer(Modifier.size(4.dp))
                if (codes.isNotEmpty()) {
                    Text(stringResource(R.string.info_allergens), style = MaterialTheme.typography.titleSmall)
                    codes.forEach { code ->
                        Text("$code  ${allergenNames[code].orEmpty()}".trim(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(NumericStyle))
    }
}

private fun formatTime(time: LocalTime): String = "${time.hour}:${time.minute.toString().padStart(2, '0')}"

internal fun formatMonth(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }} ${month.year}"
