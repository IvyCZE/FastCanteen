package yt.ivy.librecanteen.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.CanteenProvider
import yt.ivy.librecanteen.core.icanteen.ICanteenClient
import yt.ivy.librecanteen.core.model.AccountStatus
import yt.ivy.librecanteen.core.model.AllergenSettings
import yt.ivy.librecanteen.core.model.Credentials
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.OrderRecord
import yt.ivy.librecanteen.core.model.Payment
import java.time.LocalDate

data class Account(val address: String, val username: String)

/**
 * Owns the canteen session. A session that the server dropped is re-established transparently
 * with the stored login, so screens never have to deal with expiry.
 */
class CanteenRepository(
    private val store: CredentialStore,
    private val cache: MenuCache = NoMenuCache,
    private val providerFactory: (address: String) -> CanteenProvider = { ICanteenClient(ICanteenClient.normalizeBaseUrl(it)) },
) {
    private val _account = MutableStateFlow(store.load()?.let { Account(it.address, it.username) })
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val sessionLock = Mutex()
    private var provider: CanteenProvider? = null
    private var generation = 0

    /** @throws IllegalArgumentException for an unusable address. */
    suspend fun signIn(address: String, username: String, password: String) {
        val login = StoredLogin(address.trim(), username.trim(), password)
        if (store.load()?.let { it.address != login.address || it.username != login.username } != false) cache.clear()
        val fresh = providerFactory(login.address)
        fresh.login(Credentials(login.username, login.password))
        sessionLock.withLock {
            provider = fresh
            generation++
        }
        store.save(login)
        _account.value = Account(login.address, login.username)
    }

    suspend fun signOut() {
        val current = sessionLock.withLock { provider.also { provider = null; generation++ } }
        runCatching { current?.logout() }
        store.clear()
        cache.clear()
        _account.value = null
    }

    suspend fun status(): AccountStatus = session { it.status() }

    suspend fun menu(day: LocalDate): DayMenu = session { it.menu(day) }.also(cache::put)

    /** Last menu seen for [day], without the operations needed to change it. */
    fun cachedMenu(day: LocalDate): DayMenu? = cache.get(day)

    suspend fun setNote(day: LocalDate, meal: Meal, note: String): DayMenu =
        session(retry = false) { it.setNote(day, meal, note) }.also(cache::put)

    suspend fun allergenSettings(): AllergenSettings = session { it.allergenSettings() }

    suspend fun order(day: LocalDate, meal: Meal): DayMenu =
        session(retry = false) { it.order(day, meal) }.also(cache::put)

    suspend fun orderHistory(): List<OrderRecord> = session { it.orderHistory() }

    suspend fun payments(): List<Payment> = session { it.payments() }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val login = store.load() ?: throw CanteenException.SessionExpired()
        session(retry = false) { it.changePassword(Credentials(login.username, currentPassword), newPassword) }
        // Without this the next automatic re-login would use the old password.
        store.save(login.copy(password = newPassword))
    }

    /**
     * Reads are retried once on a fresh session. Operations that change state ([retry] = false) are not:
     * their server-signed operation belongs to the old session, so the caller must reload and decide again.
     */
    private suspend fun <T> session(retry: Boolean = true, block: suspend (CanteenProvider) -> T): T {
        val (current, usedGeneration) = sessionLock.withLock { currentProvider() to generation }
        return try {
            block(current)
        } catch (expired: CanteenException.SessionExpired) {
            val fresh = reLogin(usedGeneration)
            if (!retry) throw expired
            block(fresh)
        }
    }

    // Callers hold sessionLock.
    private suspend fun currentProvider(): CanteenProvider {
        provider?.let { return it }
        val login = store.load() ?: throw CanteenException.SessionExpired()
        return providerFactory(login.address).also {
            it.login(Credentials(login.username, login.password))
            provider = it
            generation++
        }
    }

    private suspend fun reLogin(failedGeneration: Int): CanteenProvider = sessionLock.withLock {
        // Several requests can expire together; only the first one logs in again.
        if (generation != failedGeneration) return@withLock provider ?: throw CanteenException.SessionExpired()
        val login = store.load() ?: throw CanteenException.SessionExpired()
        providerFactory(login.address).also {
            it.login(Credentials(login.username, login.password))
            provider = it
            generation++
        }
    }
}
