package yt.ivy.fastcanteen.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import yt.ivy.fastcanteen.core.CanteenException
import yt.ivy.fastcanteen.core.CanteenProvider
import yt.ivy.fastcanteen.core.model.AccountStatus
import yt.ivy.fastcanteen.core.model.AllergenSettings
import yt.ivy.fastcanteen.core.model.Credentials
import yt.ivy.fastcanteen.core.model.DayMenu
import yt.ivy.fastcanteen.core.model.ExchangeOffer
import yt.ivy.fastcanteen.core.model.Meal
import yt.ivy.fastcanteen.core.model.MealTimings
import yt.ivy.fastcanteen.core.model.Money
import yt.ivy.fastcanteen.core.model.OrderRecord
import yt.ivy.fastcanteen.core.model.Payment
import yt.ivy.fastcanteen.core.model.MealState
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private class MemoryStore(var login: StoredLogin? = null) : CredentialStore {
    override fun load() = login
    override fun save(login: StoredLogin) { this.login = login }
    override fun clear() { login = null }
}

/** Behaves like a canteen whose session can be made to expire. */
private class FakeCanteen : CanteenProvider {
    val logins = mutableListOf<Credentials>()
    var loggedOut = false
    var valid = false
    var orderCalls = 0
    var takeCalls = 0
    var passwordChanges = mutableListOf<Pair<Credentials, String>>()

    override suspend fun login(credentials: Credentials) {
        logins += credentials
        valid = true
    }

    override suspend fun logout() { loggedOut = true }

    private fun requireSession() { if (!valid) throw CanteenException.SessionExpired() }

    override suspend fun status(): AccountStatus {
        requireSession()
        return AccountStatus("Test", Money(0), null)
    }

    override suspend fun menu(day: LocalDate): DayMenu {
        requireSession()
        return DayMenu(day, emptyList())
    }

    override suspend fun order(day: LocalDate, meal: Meal): DayMenu {
        orderCalls++
        requireSession()
        return DayMenu(day, emptyList())
    }

    override suspend fun setNote(day: LocalDate, meal: Meal, note: String): DayMenu {
        requireSession()
        return DayMenu(day, emptyList())
    }

    override suspend fun allergenSettings(): AllergenSettings {
        requireSession()
        return AllergenSettings(emptyList(), 1)
    }

    override suspend fun saveAllergenSettings(codes: Set<String>, displayMode: Int): AllergenSettings {
        requireSession()
        return AllergenSettings(emptyList(), displayMode)
    }

    override suspend fun putIntoExchange(day: LocalDate, meal: Meal, quantity: Int): DayMenu {
        requireSession()
        return DayMenu(day, emptyList())
    }

    override suspend fun withdrawFromExchange(day: LocalDate, meal: Meal): DayMenu {
        requireSession()
        return DayMenu(day, emptyList())
    }

    override suspend fun exchangeOffers(): List<ExchangeOffer> {
        requireSession()
        return emptyList()
    }

    override suspend fun takeFromExchange(offer: ExchangeOffer): DayMenu {
        takeCalls++
        requireSession()
        return DayMenu(offer.date, emptyList())
    }

    override suspend fun orderHistory(): List<OrderRecord> = emptyList()

    override suspend fun payments(): List<Payment> = emptyList()

    override suspend fun changePassword(current: Credentials, newPassword: String) {
        requireSession()
        passwordChanges += current to newPassword
    }
}

class CanteenRepositoryTest {
    private val day = LocalDate.of(2026, 10, 20)
    private val meal = Meal(
        course = "Obědy", label = "Oběd 1", dishes = emptyList(), price = null, state = MealState.ORDERABLE,
        quantity = 0, lockedReason = null, action = null,
        timings = MealTimings(null, null, null, null, null, null),
    )

    private fun repository(canteen: FakeCanteen, store: MemoryStore = MemoryStore(StoredLogin("host", "user", "pw"))) =
        CanteenRepository(store) { canteen }

    @Test
    fun `first request logs in with the stored login`() = runTest {
        val canteen = FakeCanteen()
        repository(canteen).menu(day)

        assertEquals(listOf("user" to "pw"), canteen.logins.map { it.username to it.password })
    }

    @Test
    fun `an expired session is renewed and the read is retried`() = runTest {
        val canteen = FakeCanteen()
        val repository = repository(canteen)
        repository.menu(day)
        canteen.valid = false

        repository.menu(day)

        assertEquals(2, canteen.logins.size)
    }

    @Test
    fun `requests that expire together trigger a single new login`() = runTest {
        val canteen = FakeCanteen()
        val repository = repository(canteen)
        repository.menu(day)
        canteen.valid = false

        (1..5).map { async { repository.menu(day) } }.awaitAll()

        assertEquals(2, canteen.logins.size)
    }

    @Test
    fun `taking a meal from the exchange is never retried on a new session`() = runTest {
        val canteen = FakeCanteen()
        val repository = repository(canteen)
        repository.menu(day)
        canteen.valid = false
        val offer = ExchangeOffer(day, "Oběd 1", emptyList(), "Kuchyň", 1, "db/dbProcessOrder.jsp?type=getburza")

        assertFailsWith<CanteenException.SessionExpired> { repository.takeFromExchange(offer) }

        assertEquals(1, canteen.takeCalls)
    }

    @Test
    fun `an order is never retried on a new session`() = runTest {
        val canteen = FakeCanteen()
        val repository = repository(canteen)
        repository.menu(day)
        canteen.valid = false

        assertFailsWith<CanteenException.SessionExpired> { repository.order(day, meal) }

        assertEquals(1, canteen.orderCalls)
        assertEquals(2, canteen.logins.size) // the session itself was renewed for the reload that follows
    }

    @Test
    fun `without a stored login the session is reported expired`() = runTest {
        val canteen = FakeCanteen()
        assertFailsWith<CanteenException.SessionExpired> { repository(canteen, MemoryStore()).menu(day) }
    }

    @Test
    fun `sign in stores the trimmed login and sign out forgets it`() = runTest {
        val canteen = FakeCanteen()
        val store = MemoryStore()
        val repository = repository(canteen, store)

        repository.signIn(" host ", " user ", "pw")
        assertEquals(StoredLogin("host", "user", "pw"), store.login)
        assertEquals(Account("host", "user"), repository.account.value)

        repository.signOut()
        assertNull(store.login)
        assertNull(repository.account.value)
        assertEquals(true, canteen.loggedOut)
    }

    @Test
    fun `a changed password replaces the stored one so re-login keeps working`() = runTest {
        val canteen = FakeCanteen()
        val store = MemoryStore(StoredLogin("host", "user", "old"))
        val repository = repository(canteen, store)

        repository.changePassword("old", "new")

        assertEquals("new", store.login?.password)
        assertEquals("old", canteen.passwordChanges.single().first.password)
        assertEquals("new", canteen.passwordChanges.single().second)
    }

    @Test
    fun `a rejected password change leaves the stored password alone`() = runTest {
        val canteen = object : CanteenProvider by FakeCanteen() {
            override suspend fun changePassword(current: Credentials, newPassword: String) {
                throw CanteenException.PasswordChangeRejected("no")
            }
        }
        val store = MemoryStore(StoredLogin("host", "user", "old"))
        val repository = CanteenRepository(store) { canteen }

        assertFailsWith<CanteenException.PasswordChangeRejected> { repository.changePassword("old", "new") }

        assertEquals("old", store.login?.password)
    }
}
