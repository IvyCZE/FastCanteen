package yt.ivy.fastcanteen.core

import yt.ivy.fastcanteen.core.model.AccountStatus
import yt.ivy.fastcanteen.core.model.AllergenSettings
import yt.ivy.fastcanteen.core.model.Credentials
import yt.ivy.fastcanteen.core.model.DayMenu
import yt.ivy.fastcanteen.core.model.ExchangeOffer
import yt.ivy.fastcanteen.core.model.Meal
import yt.ivy.fastcanteen.core.model.OrderRecord
import yt.ivy.fastcanteen.core.model.Payment
import java.time.LocalDate

/**
 * Backend-agnostic access to a school canteen. The only implementation today scrapes
 * iCanteen's HTML; an official API could be added as another implementation.
 */
public interface CanteenProvider {
    /** @throws CanteenException.AuthenticationFailed on wrong credentials. */
    public suspend fun login(credentials: Credentials)

    public suspend fun logout()

    public suspend fun status(): AccountStatus

    public suspend fun menu(day: LocalDate): DayMenu

    /**
     * Performs the operation offered by [meal] (order, cancel or switch) and returns the day as the
     * server now reports it. [meal] must come from a recent [menu] call, because its operation is signed
     * by the server for that page.
     *
     * @throws CanteenException.OrderRejected if the canteen refuses.
     * @throws CanteenException.OrderNotApplied if the canteen accepted the request but the day did not change.
     */
    public suspend fun order(day: LocalDate, meal: Meal): DayMenu

    /**
     * Stores [note] on the ordered [meal] and returns the day as the server now reports it.
     *
     * @throws CanteenException.OrderNotApplied if the stored note differs from [note] afterwards.
     */
    public suspend fun setNote(day: LocalDate, meal: Meal, note: String): DayMenu

    /** Meals other users currently offer in the exchange; empty when there are none. */
    public suspend fun exchangeOffers(): List<ExchangeOffer>

    /**
     * Takes [offer] from the exchange, which orders it and charges its price, and returns the offer's day as the
     * server now reports it. [offer] must come from a recent [exchangeOffers] call.
     *
     * @throws CanteenException.OrderRejected if the canteen refuses, e.g. because someone else was faster.
     * @throws CanteenException.OrderNotApplied if the canteen accepted the request but the order did not appear.
     */
    public suspend fun takeFromExchange(offer: ExchangeOffer): DayMenu

    /**
     * Offers [quantity] pieces of the ordered [meal] in the exchange and returns the day as the server now reports
     * it. The pieces stay ordered until another user takes them. [meal] must offer [Meal.exchangePut].
     *
     * @throws CanteenException.OrderNotApplied if the meal is not in the exchange afterwards.
     */
    public suspend fun putIntoExchange(day: LocalDate, meal: Meal, quantity: Int): DayMenu

    /**
     * Takes the pieces of [meal] back from the exchange, if nobody took them yet. [meal] must offer
     * [Meal.exchangeWithdraw].
     *
     * @throws CanteenException.OrderNotApplied if the exchanged quantity did not drop.
     */
    public suspend fun withdrawFromExchange(day: LocalDate, meal: Meal): DayMenu

    /** The user's allergen preferences as configured in the canteen's settings. */
    public suspend fun allergenSettings(): AllergenSettings

    /**
     * Stores the selected allergen [codes] and [displayMode] (the canteen's option number, 1 to 6)
     * and returns the settings as the server now reports them.
     *
     * @throws CanteenException.OrderNotApplied if the stored settings differ afterwards.
     */
    public suspend fun saveAllergenSettings(codes: Set<String>, displayMode: Int): AllergenSettings

    public suspend fun orderHistory(): List<OrderRecord>

    public suspend fun payments(): List<Payment>

    /**
     * Changes the password and confirms the change by logging in with the new one.
     *
     * @param current the account's username and its current password.
     * @throws CanteenException.PasswordChangeRejected if the canteen refuses or the new password does not work.
     */
    public suspend fun changePassword(current: Credentials, newPassword: String)
}

public sealed class CanteenException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    public class AuthenticationFailed : CanteenException("Wrong username or password")

    public class SessionExpired : CanteenException("Session expired, log in again")

    /** The server answered, but not with markup this version understands. */
    public class UnsupportedLayout(detail: String) : CanteenException("Unsupported page layout: $detail")

    public class Network(cause: Throwable) : CanteenException("Network error: ${cause.message}", cause)

    /** [reason] is the canteen's own wording, suitable for showing to the user. */
    public class OrderRejected(public val reason: String) : CanteenException(reason)

    public class OrderNotApplied : CanteenException("The canteen did not apply the change")

    /** The page no longer offers what the user acted on (changed elsewhere, deadline passed); nothing was sent. */
    public class ChangedMeanwhile : CanteenException("The offer changed since it was shown; nothing was sent")

    public class PasswordChangeRejected(public val reason: String) : CanteenException(reason)
}
