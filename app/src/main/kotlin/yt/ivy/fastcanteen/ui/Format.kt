package yt.ivy.fastcanteen.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import yt.ivy.fastcanteen.R
import yt.ivy.fastcanteen.core.CanteenException
import yt.ivy.fastcanteen.core.model.Money
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** A message that is either a string resource or text supplied by the canteen. */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> text
}

fun Throwable.toUiText(): UiText = when (this) {
    is CanteenException.AuthenticationFailed -> UiText.Res(R.string.error_auth)
    is CanteenException.SessionExpired -> UiText.Res(R.string.error_session)
    is CanteenException.Network -> UiText.Res(R.string.error_network)
    is CanteenException.UnsupportedLayout -> UiText.Res(R.string.error_layout)
    is CanteenException.OrderNotApplied -> UiText.Res(R.string.error_not_applied)
    is CanteenException.ChangedMeanwhile -> UiText.Res(R.string.error_changed_meanwhile)
    is CanteenException.OrderRejected -> UiText.Raw(reason)
    is CanteenException.PasswordChangeRejected -> UiText.Raw(reason)
    is IllegalArgumentException -> UiText.Res(R.string.error_address)
    else -> UiText.Res(R.string.error_generic)
}

fun formatMoney(money: Money): String {
    val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(money.toBigDecimal())
    return "$number\u00a0Kč"
}

fun formatDayHeader(date: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return "$weekday, ${date.dayOfMonth}.\u00a0${date.monthValue}."
}

fun formatShortDate(date: LocalDate): String = "${date.dayOfMonth}.\u00a0${date.monthValue}.\u00a0${date.year}"

fun formatDeadline(at: LocalDateTime): String {
    val time = "${at.hour}:${at.minute.toString().padStart(2, '0')}"
    return "${at.dayOfMonth}.\u00a0${at.monthValue}. $time"
}
