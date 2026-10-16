package yt.ivy.librecanteen.core

import yt.ivy.librecanteen.core.model.AccountStatus
import yt.ivy.librecanteen.core.model.AllergenSettings
import yt.ivy.librecanteen.core.model.Credentials
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.OrderRecord
import yt.ivy.librecanteen.core.model.Payment
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

    /** The user's allergen preferences as configured in the canteen's settings. */
    public suspend fun allergenSettings(): AllergenSettings

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

    public class PasswordChangeRejected(public val reason: String) : CanteenException(reason)
}
