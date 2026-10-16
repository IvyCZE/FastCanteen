package yt.ivy.librecanteen.core.icanteen

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.model.Credentials
import yt.ivy.librecanteen.core.model.MealState
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ICanteenClientMutationsTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ICanteenClient
    private val day = LocalDate.of(2026, 10, 20)

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = ICanteenClient(server.url("/"))
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private fun redirect(to: String) = MockResponse.Builder().code(302).addHeader("Location", to).build()

    private suspend fun loadDay() = run {
        server.enqueue(ok(fixture("day_ordered.html")))
        client.menu(day).also { server.takeRequest() }
    }

    @Test
    fun `cancelling an order calls the signed operation and returns the refreshed day`() = runTest {
        val menu = loadDay()
        server.enqueue(ok(fixture("synthetic_order_ok.html")))
        server.enqueue(ok(fixture("day_closed.html").replace("2026-10-19", "2026-10-20")))

        val updated = client.order(day, menu.meals[0])

        val operation = server.takeRequest().requestLine
        assertTrue(operation.startsWith("GET /faces/secured/db/dbProcessOrder.jsp?"), operation)
        assertTrue("type=delete" in operation, operation)
        assertEquals(MealState.LOCKED, updated.meals[0].state)
    }

    @Test
    fun `a refused order is reported with the canteen's wording and the day is not reloaded`() = runTest {
        val menu = loadDay()
        server.enqueue(ok(fixture("synthetic_order_error.html")))

        val error = assertFailsWith<CanteenException.OrderRejected> { client.order(day, menu.meals[0]) }

        assertEquals("Objednávky jsou již uzavřeny.", error.reason)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an order that leaves the day unchanged is not reported as success`() = runTest {
        val menu = loadDay()
        server.enqueue(ok(fixture("synthetic_order_ok.html")))
        server.enqueue(ok(fixture("day_ordered.html")))

        assertFailsWith<CanteenException.OrderNotApplied> { client.order(day, menu.meals[1]) }
    }

    @Test
    fun `locked meals are never sent to the server`() = runTest {
        server.enqueue(ok(fixture("day_closed.html")))
        val menu = client.menu(LocalDate.of(2026, 10, 19))

        assertFailsWith<IllegalArgumentException> { client.order(LocalDate.of(2026, 10, 19), menu.meals[0]) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `operation outside the order endpoint is refused`() = runTest {
        val menu = loadDay()
        val tampered = menu.meals[0].copy(action = menu.meals[0].action!!.copy(path = "../../web/setting?x=1"))

        assertFailsWith<CanteenException.UnsupportedLayout> { client.order(day, tampered) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `history and payments are read from their pages`() = runTest {
        server.enqueue(ok(fixture("objednavky.html")))
        server.enqueue(ok(fixture("platby.html")))

        assertEquals(7, client.orderHistory().size)
        assertEquals(3, client.payments().size)
        assertTrue(server.takeRequest().requestLine.startsWith("GET /faces/secured/objednavky.jsp"))
        assertTrue(server.takeRequest().requestLine.startsWith("GET /faces/secured/platby.jsp"))
    }

    @Test
    fun `password change submits the form and is confirmed by logging in with the new password`() = runTest {
        server.enqueue(ok(fixture("password_form.html")))
        server.enqueue(ok("<html></html>"))
        // verification session: login page, credential post redirecting to the app, main page, then logout
        server.enqueue(ok(fixture("login.html")))
        server.enqueue(redirect("/faces/secured/main.jsp"))
        server.enqueue(ok(fixture("main_today.html")))
        server.enqueue(ok(fixture("main_today.html")))
        server.enqueue(ok("<html></html>"))

        client.changePassword(Credentials("someone", "old-pass"), "new-pass")

        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("POST /web/setting HTTP/1.1", post.requestLine)
        val body = post.body!!.utf8()
        assertTrue("passwordSetting%3AStravnikPasswordCurrent=old-pass" in body, body)
        assertTrue("passwordSetting%3AStravnikPassword=new-pass" in body, body)
        assertTrue("passwordSetting%3AStravnikPasswordSecond=new-pass" in body, body)
        assertTrue("javax.faces.ViewState=VIEWSTATE" in body, body)
        server.takeRequest()
        val verification = server.takeRequest()
        assertTrue("j_password=new-pass" in verification.body!!.utf8())
    }

    @Test
    fun `password change the canteen rejects surfaces its message`() = runTest {
        server.enqueue(ok(fixture("password_form.html")))
        server.enqueue(ok(fixture("password_rejected.html")))
        server.enqueue(ok(fixture("login.html")))
        server.enqueue(redirect("/login?login_error=1"))
        server.enqueue(ok(fixture("login.html")))

        val error = assertFailsWith<CanteenException.PasswordChangeRejected> {
            client.changePassword(Credentials("someone", "wrong"), "new-pass")
        }

        assertEquals("Změna hesla se nezdařila - nezadáno správné stávající heslo", error.reason)
    }

    @Test
    fun `password change rejects an unchanged or empty password before any request`() = runTest {
        assertFailsWith<IllegalArgumentException> { client.changePassword(Credentials("u", "same"), "same") }
        assertFailsWith<IllegalArgumentException> { client.changePassword(Credentials("u", "old"), "  ") }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `saving allergens posts the form with its hidden fields and confirms by reading it back`() = runTest {
        val saved = AllergenSettingsParser.parse(fixture("allergens_form.html"))
        server.enqueue(ok(fixture("allergens_form.html")))
        server.enqueue(ok("<html></html>"))
        server.enqueue(ok(fixture("allergens_form.html")))

        val result = client.saveAllergenSettings(saved.selectedCodes, saved.displayMode)

        assertEquals(saved.selectedCodes, result.selectedCodes)
        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("POST /web/setting HTTP/1.1", post.requestLine)
        val body = post.body!!.utf8()
        assertTrue("_csrf=00000000-0000-0000-0000-000000000000" in body, body)
        assertTrue("alergens%3AalergenyFirst%3A0%3AshowAlergens=true" in body, body)
        assertTrue("alergens_SUBMIT=1" in body, body)
    }

    @Test
    fun `allergens that the canteen did not store are reported`() = runTest {
        server.enqueue(ok(fixture("allergens_form.html")))
        server.enqueue(ok("<html></html>"))
        server.enqueue(ok(fixture("allergens_form.html")))

        assertFailsWith<CanteenException.OrderNotApplied> { client.saveAllergenSettings(setOf("2"), 3) }
    }

    @Test
    fun `unknown allergen codes are refused before anything is sent`() = runTest {
        server.enqueue(ok(fixture("allergens_form.html")))

        assertFailsWith<IllegalArgumentException> { client.saveAllergenSettings(setOf("99"), 3) }
        assertEquals(1, server.requestCount)
    }
}
