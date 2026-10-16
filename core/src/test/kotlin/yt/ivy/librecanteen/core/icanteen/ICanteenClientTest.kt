package yt.ivy.librecanteen.core.icanteen

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.model.Credentials
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.core.model.Money
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ICanteenClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ICanteenClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = ICanteenClient(server.url("/"))
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun html(body: String, vararg headers: Pair<String, String>) =
        MockResponse.Builder().code(200).body(body).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()

    private fun redirect(to: String) = MockResponse.Builder().code(302).addHeader("Location", to).build()

    private fun enqueueLogin() {
        server.enqueue(html(fixture("login.html"), "Set-Cookie" to "JSESSIONID=abc; Path=/"))
        server.enqueue(redirect("/faces/secured/main.jsp"))
        server.enqueue(html(fixture("main_today.html")))
    }

    @Test
    fun `login posts credentials with csrf and keeps the session cookie`() = runTest {
        enqueueLogin()
        client.login(Credentials("someone", "s3cret"))

        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("POST /j_spring_security_check HTTP/1.1", post.requestLine)
        val body = post.body!!.utf8()
        assertTrue("j_username=someone" in body)
        assertTrue("j_password=s3cret" in body)
        assertTrue("_csrf=00000000-0000-0000-0000-000000000000" in body)
        assertEquals("JSESSIONID=abc", post.headers["Cookie"])
    }

    @Test
    fun `wrong password is an authentication failure`() = runTest {
        server.enqueue(html(fixture("login.html")))
        server.enqueue(redirect("/login?login_error=1&terminal=false"))
        server.enqueue(html(fixture("login.html")))

        assertFailsWith<CanteenException.AuthenticationFailed> {
            client.login(Credentials("someone", "wrong"))
        }
    }

    @Test
    fun `status is read from the main page`() = runTest {
        enqueueLogin()
        client.login(Credentials("someone", "s3cret"))

        server.enqueue(html(fixture("main_today.html")))
        assertEquals(Money(100_000), client.status().credit)
    }

    @Test
    fun `menu requests the day fragment`() = runTest {
        server.enqueue(html(fixture("day_ordered.html")))
        val menu = client.menu(LocalDate.of(2026, 10, 20))

        val request = server.takeRequest()
        assertTrue(request.requestLine.startsWith("GET /faces/secured/db/dbJidelnicekOnDayView.jsp?day=2026-10-20"))
        assertEquals(MealState.ORDERED, menu.meals.first().state)
    }

    @Test
    fun `redirect to login means the session expired`() = runTest {
        server.enqueue(redirect("/login"))
        server.enqueue(html(fixture("login.html")))

        assertFailsWith<CanteenException.SessionExpired> { client.menu(LocalDate.of(2026, 10, 20)) }
    }

    @Test
    fun `server errors surface as network errors`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())

        assertFailsWith<CanteenException.Network> { client.status() }
    }

    @Test
    fun `base url accepts what users paste`() {
        fun norm(s: String) = ICanteenClient.normalizeBaseUrl(s).toString()
        assertEquals("https://jidelna.example.cz/", norm("jidelna.example.cz"))
        assertEquals("https://jidelna.example.cz/", norm("  https://jidelna.example.cz/login?x=1 "))
        assertEquals("https://host.example.cz/demo/", norm("https://host.example.cz/demo/faces/secured/main.jsp"))
        assertFailsWith<IllegalArgumentException> { norm("http://insecure.example.cz") }
        assertFailsWith<IllegalArgumentException> { norm("  ") }
    }
}
