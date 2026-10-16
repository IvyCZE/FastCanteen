@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class, LocalTimeSerializer::class)

package yt.ivy.librecanteen.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

public class Credentials(public val username: String, public val password: String) {
    override fun toString(): String = "Credentials(username=$username, password=***)"
}

/** Amount in Czech koruna, stored in hellers to avoid floating point errors. */
@JvmInline
@Serializable
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

@Serializable
public data class Dish(val name: String, val allergens: List<String>) {
    /**
     * Whether the dish declares an allergen the user selected. Canteens list either a group ("1")
     * or a subtype ("1a"); a group matches any of its subtypes and the other way round.
     */
    public fun triggers(selected: Set<String>): Boolean =
        allergens.any { declared -> selected.any { chosen -> allergenCodesOverlap(declared, chosen) } }
}

private fun allergenCodesOverlap(a: String, b: String): Boolean {
    if (a == b) return true
    val groupA = a.takeWhile { it.isDigit() }
    val groupB = b.takeWhile { it.isDigit() }
    return groupA == groupB && (a == groupA || b == groupB)
}

@Serializable
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

@Serializable
public enum class ActionType { MAKE, DELETE, REORDER, NONE, OTHER }

/**
 * A server-signed order operation, valid only for the page it was read from.
 * [path] is relative to the canteen's `faces/secured/` directory.
 */
@Serializable
public data class MealAction(val type: ActionType, val path: String)

/**
 * Server-signed operation that stores a free-text note on an order. The note goes between
 * [prefix] and [suffix] and must be URL-encoded; like [MealAction] it is valid only for the page it was read from.
 */
@Serializable
public data class NoteAction(val prefix: String, val suffix: String) {
    public fun path(note: String): String = prefix + java.net.URLEncoder.encode(note, Charsets.UTF_8) + suffix
}

@Serializable
public data class MealTimings(
    val pickupFrom: LocalTime?,
    val pickupTo: LocalTime?,
    val orderUntil: LocalDateTime?,
    val cancelUntil: LocalDateTime?,
    val ordersCount: Int?,
    val ordersLimit: Int?,
)

@Serializable
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
    /** The meal is offered in the canteen's meal exchange ("burza"). */
    val inExchange: Boolean = false,
    /** Note stored on the order; null when the order cannot carry one. */
    val note: String? = null,
    val noteAction: NoteAction? = null,
)

/** A day's menu. [meals] is empty when the canteen has no menu for [date]. */
@Serializable
public data class DayMenu(val date: LocalDate, val meals: List<Meal>)

/** One line of the order history. */
public data class OrderRecord(
    val date: LocalDate,
    val label: String,
    val dishes: List<Dish>,
    val orderedAt: LocalDateTime?,
    /** Channel the order came through as reported by the canteen, e.g. "Internet". */
    val orderedVia: String?,
    val pickupPoint: String,
    val orderedCount: Int,
    val issuedCount: Int,
    val price: Money?,
)

public data class Payment(
    val date: LocalDate,
    val method: String,
    val kind: String,
    val amount: Money,
    val note: String,
)

/** One allergen from the canteen's list, e.g. code "1a" for wheat. */
public data class Allergen(val code: String, val name: String, val selected: Boolean)

/** How the canteen highlights allergens; [displayMode] is the server's option number (1 = hide them). */
public data class AllergenSettings(val allergens: List<Allergen>, val displayMode: Int) {
    public val selectedCodes: Set<String> get() = allergens.filter { it.selected }.map { it.code }.toSet()
}
