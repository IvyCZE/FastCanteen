package yt.ivy.librecanteen.ui.lunch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Dish
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.core.model.exchangedQuantity
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
                            DaySection(day, state.days[day], state, showHeader = true, onAction, onNote, onExchangePut, onExchangeWithdraw, Modifier.padding(bottom = 12.dp))
                        }
                    } else {
                        item(key = "calendar") {
                            MonthCalendar(state, onPreviousMonth, onNextMonth, onSelect, onPickMonth)
                        }
                        item(key = "day-${state.anchor}") {
                            DaySection(state.anchor, state.days[state.anchor], state, showHeader = true, onAction, onNote, onExchangePut, onExchangeWithdraw)
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
            formatDayHeader(state.anchor).replace("\u00a0", "")
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
        Modifier.fillMaxWidth().horizontalSwipe(onPrevious = onPreviousMonth, onNext = onNextMonth).padding(bottom = 12.dp),
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
    modifier: Modifier = Modifier,
) {
    val isToday = day == LocalDate.now()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            else -> Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                val edge = MaterialTheme.colorScheme.outline
                Column(
                    // The surface clips the stripe to its rounded corners, so it follows the curve on the left.
                    Modifier
                        .drawBehind { drawRect(edge, size = Size(DAY_EDGE_WIDTH.toPx(), size.height)) }
                        .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val shared = CommonDishes.leadingCount(menu.meals)
                    if (shared > 0) SharedDishes(menu.meals[0].dishes.take(shared), state.allergens)
                    menu.meals.forEach { meal ->
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
                            hiddenDishes = shared,
                        )
                    }
                }
            }
        }
    }
}

private val DAY_EDGE_WIDTH = 4.dp

/** What all of a day's meals start with, written once above the cards. */
@Composable
private fun SharedDishes(dishes: List<Dish>, allergens: Set<String>) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            dishes.forEach { DishLine(it, allergens) }
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
    hiddenDishes: Int,
) {
    var showInfo by remember { mutableStateOf(false) }
    var showPut by remember { mutableStateOf(false) }
    val ordered = meal.state == MealState.ORDERED || meal.state == MealState.ORDERED_LOCKED
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (ordered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The meal's own name is its first dish that is not shared with the other meals of the day.
            val title = meal.dishes.getOrNull(hiddenDishes) ?: meal.dishes.firstOrNull()
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 6.dp, end = 8.dp)) {
                    SqueezedText(title?.name ?: meal.label, MaterialTheme.typography.titleMedium)
                    title?.let { AllergenCodes(it, allergens, Modifier.padding(top = 2.dp)) }
                }
                Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ordered && meal.inExchange) {
                        Icon(SwapHorizIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            exchangeLabel(meal),
                            style = MaterialTheme.typography.titleSmall.merge(NumericStyle),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    IconButton(onClick = { showInfo = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.meal_details),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            meal.dishes.drop(hiddenDishes + 1).forEach { DishLine(it, allergens) }

            meal.lockedReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    if (meal.noteAction != null && !offline) {
                        NoteField(saved = meal.note.orEmpty(), enabled = !busy) { onNote(day, meal, it) }
                    }
                }
                // A meal that can no longer be ordered has nothing left to show here.
                if (meal.state != MealState.LOCKED) {
                    if (ordered && meal.quantity > 1) {
                        Text("${meal.quantity}×", style = MaterialTheme.typography.titleSmall.merge(NumericStyle))
                    }
                    meal.price?.let { Text(formatMoney(it), style = MaterialTheme.typography.titleSmall.merge(NumericStyle)) }
                    OrderControl(
                        meal = meal,
                        ordered = ordered,
                        busy = busy,
                        offline = offline,
                        onClick = { onAction(day, meal) },
                        onExchangePut = { showPut = true },
                        onExchangeWithdraw = { onExchangeWithdraw(day, meal) },
                    )
                }
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

private val AllergenRed = Color(0xFFFF3B30)

/**
 * One dish with its allergen codes on the right. Codes that match the user's [selected] allergens turn red and get a
 * warning icon in front of them; the dish name itself is left alone.
 */
@Composable
internal fun DishLine(dish: Dish, selected: Set<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dish.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        AllergenCodes(dish, selected, Modifier.widthIn(max = 96.dp), TextAlign.End)
    }
}

@Composable
private fun AllergenCodes(dish: Dish, selected: Set<String>, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    if (dish.allergens.isEmpty()) return
    val hits = dish.triggeringAllergens(selected)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hits.isNotEmpty()) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = stringResource(R.string.allergen_warning),
                tint = AllergenRed,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            buildAnnotatedString {
                dish.allergens.forEachIndexed { index, code ->
                    if (index > 0) append(",")
                    if (code in hits) {
                        withStyle(SpanStyle(color = AllergenRed, fontWeight = FontWeight.Bold)) { append(code) }
                    } else {
                        append(code)
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall.merge(NumericStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
        )
    }
}

/**
 * A title that stays on one line when a little tighter letter spacing is enough. Beyond [TITLE_TIGHTENING_EM] the
 * text would get hard to read, so it wraps at normal spacing instead.
 */
@Composable
private fun SqueezedText(text: String, style: ComposeTextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val baseSpacing = style.letterSpacing.value.takeIf { !it.isNaN() } ?: 0f
        val fontSize = style.fontSize.value
        val spacing = remember(text, style, available) {
            val steps = (0..TITLE_TIGHTENING_STEPS).map { baseSpacing - it * TITLE_TIGHTENING_EM / TITLE_TIGHTENING_STEPS * fontSize }
            steps.firstOrNull { candidate ->
                available == Constraints.Infinity ||
                    !measurer.measure(
                        text,
                        style.copy(letterSpacing = candidate.sp),
                        softWrap = false,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = available),
                    ).didOverflowWidth
            } ?: baseSpacing
        }
        Text(text, style = style.copy(letterSpacing = spacing.sp))
    }
}

/** Most the title's letters may be pulled together, as a share of the font size. */
private const val TITLE_TIGHTENING_EM = 0.05f
private const val TITLE_TIGHTENING_STEPS = 5

/** Compact, borderless note line; a check button appears once the text differs from what is stored. */
@Composable
private fun NoteField(saved: String, enabled: Boolean, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    val dirty = text.trim() != saved
    val content = LocalContentColor.current
    BasicTextField(
        value = text,
        onValueChange = { text = it.replace("<", "").replace('\n', ' ').take(NOTE_MAX_LENGTH) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = content),
        cursorBrush = SolidColor(content),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (dirty) onSave(text.trim()) }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = content.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.note_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                    field()
                }
                if (dirty) {
                    IconButton(onClick = { onSave(text.trim()) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.note_save), modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    )
}

private const val NOTE_MAX_LENGTH = 200

/** A checkbox for ordering; where the meal can go to the exchange instead, that button takes its place. */
@Composable
private fun OrderControl(
    meal: Meal,
    ordered: Boolean,
    busy: Boolean,
    offline: Boolean,
    onClick: () -> Unit,
    onExchangePut: () -> Unit,
    onExchangeWithdraw: () -> Unit,
) {
    val idle = !busy && !offline
    // Filled so it stays readable on the tinted card of an ordered meal.
    val quiet = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
        contentColor = MaterialTheme.colorScheme.primaryContainer,
    )
    // Offered only on orders the canteen no longer lets you cancel, so it never competes with the checkbox.
    if (meal.exchangeWithdraw != null) {
        ExchangeIconButton(Icons.Filled.Close, R.string.action_exchange_withdraw, busy, idle, quiet, onExchangeWithdraw)
        return
    }
    if (meal.exchangePut != null) {
        ExchangeIconButton(SwapHorizIcon, R.string.action_exchange_put, busy, idle, quiet, onExchangePut)
        return
    }
    val description = when (meal.state) {
        MealState.ORDERABLE -> R.string.action_order
        MealState.ORDERED -> R.string.action_cancel_order
        MealState.REORDERABLE -> R.string.action_switch
        MealState.ORDERED_LOCKED, MealState.LOCKED -> null
    }?.let { stringResource(it) }
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                Checkbox(
                    checked = ordered,
                    onCheckedChange = { onClick() },
                    enabled = description != null && !offline,
                    colors = if (ordered) {
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            checkmarkColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    } else {
                        CheckboxDefaults.colors()
                    },
                    modifier = Modifier.semantics { description?.let { contentDescription = it } },
                )
            }
        }
    }
}

@Composable
private fun ExchangeIconButton(
    icon: ImageVector,
    description: Int,
    busy: Boolean,
    enabled: Boolean,
    colors: ButtonColors,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, colors = colors, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(20.dp))
        }
    }
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
