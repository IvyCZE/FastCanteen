package yt.ivy.librecanteen.core

import yt.ivy.librecanteen.core.model.AccountStatus
import yt.ivy.librecanteen.core.model.Credentials
import yt.ivy.librecanteen.core.model.DayMenu
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
}

public sealed class CanteenException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    public class AuthenticationFailed : CanteenException("Wrong username or password")

    public class SessionExpired : CanteenException("Session expired, log in again")

    /** The server answered, but not with markup this version understands. */
    public class UnsupportedLayout(detail: String) : CanteenException("Unsupported page layout: $detail")

    public class Network(cause: Throwable) : CanteenException("Network error: ${cause.message}", cause)
}
