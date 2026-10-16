package yt.ivy.librecanteen.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

public class Credentials(public val username: String, public val password: String) {
    override fun toString(): String = "Credentials(username=$username, password=***)"
}

/** Amount in Czech koruna, stored in hellers to avoid floating point errors. */
@JvmInline
public value class Money(public val halere: Long) {
    public fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(halere, 2)

    public companion object {
        public fun of(amount: BigDecimal): Money =
            Money(amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact())
    }
}

public data class AccountStatus(
    val displayName: String,
    val credit: Money,
    /** Last day for which orders can currently be placed, if the canteen reports it. */
    val orderableUntil: LocalDate?,
)

public data class Dish(val name: String, val allergens: List<String>)

public enum class MealState {
    /** Ordered and can still be cancelled. */
    ORDERED,

    /** Ordered, but cancelling is no longer possible. */
    ORDERED_LOCKED,

    /** Not ordered, can be ordered now. */
    ORDERABLE,

    /** Another variant is ordered; this one can replace it. */
    REORDERABLE,

    /** Not ordered and cannot be ordered. */
    LOCKED,
}

public enum class ActionType { MAKE, DELETE, REORDER, NONE, OTHER }

/**
 * A server-signed order operation, valid only for the page it was read from.
 * [path] is relative to the canteen's `faces/secured/` directory.
 */
public data class MealAction(val type: ActionType, val path: String)

public data class MealTimings(
    val pickupFrom: LocalTime?,
    val pickupTo: LocalTime?,
    val orderUntil: LocalDateTime?,
    val cancelUntil: LocalDateTime?,
    val ordersCount: Int?,
    val ordersLimit: Int?,
)

public data class Meal(
    /** Course heading as shown by the canteen, e.g. "Obědy". */
    val course: String,
    /** Variant label, e.g. "Oběd 1". */
    val label: String,
    val dishes: List<Dish>,
    val price: Money?,
    val state: MealState,
    val quantity: Int,
    /** Why the meal cannot be changed, as stated by the canteen. */
    val lockedReason: String?,
    val timings: MealTimings,
    val action: MealAction?,
)

/** A day's menu. [meals] is empty when the canteen has no menu for [date]. */
public data class DayMenu(val date: LocalDate, val meals: List<Meal>)
