package yt.ivy.fastcanteen.core.icanteen

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import yt.ivy.fastcanteen.core.CanteenException
import yt.ivy.fastcanteen.core.model.MealState
import yt.ivy.fastcanteen.core.model.exchangedQuantity
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExchangeTest {
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

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private suspend fun loadOffer() = run {
        server.enqueue(ok(fixture("burza_with_meal.html")))
        client.exchangeOffers().single().also { server.takeRequest() }
    }

    @Test
    fun `the exchange list is parsed with the signed operation`() = runTest {
        val offer = loadOffer()

        assertEquals(LocalDate.of(2026, 10, 19), offer.date)
        assertEquals("Oběd 1", offer.label)
        assertEquals("Kuchyň", offer.pickupPoint)
        assertEquals(1, offer.quantity)
        assertEquals(listOf("Polévka hovězí s těstovinou", "Rajská omáčka, hovězí maso, těstoviny"), offer.dishes.map { it.name })
        assertEquals(listOf("1", "3", "9"), offer.dishes[0].allergens)
        assertTrue(offer.path.startsWith("db/dbProcessOrder.jsp?") && "type=getburza" in offer.path && "vydejna=1" in offer.path, offer.path)
    }

    @Test
    fun `an empty exchange gives an empty list`() = runTest {
        server.enqueue(ok(fixture("burza_empty.html")))

        assertEquals(emptyList(), client.exchangeOffers())
    }

    @Test
    fun `a page without the table is an unsupported layout`() = runTest {
        server.enqueue(ok("<html><body>nothing</body></html>"))

        assertFailsWith<CanteenException.UnsupportedLayout> { client.exchangeOffers() }
    }

    @Test
    fun `taking a meal calls the signed operation and confirms the new order on the day`() = runTest {
        val offer = loadOffer()
        server.enqueue(ok(fixture("day_closed.html")))
        server.enqueue(ok("<html>main page</html>"))
        server.enqueue(ok(fixture("day_ordered_burza_offer.html")))

        val updated = client.takeFromExchange(offer)

        server.takeRequest()
        val operation = server.takeRequest().requestLine
        assertTrue(operation.startsWith("GET /faces/secured/db/dbProcessOrder.jsp?") && "type=getburza" in operation, operation)
        assertEquals(MealState.ORDERED_LOCKED, updated.meals[0].state)
    }

    @Test
    fun `a refusal from the canteen is reported with its wording`() = runTest {
        val offer = loadOffer()
        server.enqueue(ok(fixture("day_closed.html")))
        server.enqueue(ok(fixture("synthetic_order_error.html")))

        assertFailsWith<CanteenException.OrderRejected> { client.takeFromExchange(offer) }
        assertEquals(2, server.requestCount - 1)
    }

    @Test
    fun `a take that leaves the order unchanged is not reported as success`() = runTest {
        val offer = loadOffer()
        server.enqueue(ok(fixture("day_closed.html")))
        server.enqueue(ok("<html>main page</html>"))
        server.enqueue(ok(fixture("day_closed.html")))

        assertFailsWith<CanteenException.OrderNotApplied> { client.takeFromExchange(offer) }
    }

    @Test
    fun `operations other than taking from the exchange are never sent`() = runTest {
        val offer = loadOffer().copy(path = "db/dbProcessOrder.jsp?time=1&token=T&ID=1&day=2026-10-19&type=delete")

        assertFailsWith<CanteenException.UnsupportedLayout> { client.takeFromExchange(offer) }
        assertEquals(1, server.requestCount)
    }
}

class OwnExchangeTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ICanteenClient
    private val day = LocalDate.of(2026, 10, 19)

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

    private suspend fun load(name: String) = run {
        server.enqueue(ok(fixture(name)))
        client.menu(day).meals[0].also { server.takeRequest() }
    }

    @Test
    fun `an ordered meal that can be offered carries the put operation`() = runTest {
        val meal = load("day_ordered_burza_offer.html")

        val put = assertNotNull(meal.exchangePut)
        assertEquals(1, put.maxQuantity)
        assertTrue("type=multiburza&amount=1&" in put.path(1), put.path(1))
        assertNull(meal.exchangeWithdraw)
        assertEquals(0, meal.exchangedQuantity)
        assertFalse(meal.inExchange)
        assertFailsWith<IllegalArgumentException> { put.path(2) }
    }

    @Test
    fun `a meal in the exchange carries the withdraw operation`() = runTest {
        val meal = load("day_ordered_in_exchange.html")

        assertTrue(meal.inExchange)
        assertEquals(1, meal.exchangedQuantity)
        assertTrue("type=minusburza" in meal.exchangeWithdraw!!.path)
        assertNull(meal.exchangePut)
    }

    @Test
    fun `putting a meal into the exchange is confirmed by the day`() = runTest {
        val meal = load("day_ordered_burza_offer.html")
        server.enqueue(ok(fixture("synthetic_order_ok.html")))
        server.enqueue(ok(fixture("day_ordered_in_exchange.html")))

        val updated = client.putIntoExchange(day, meal, 1)

        val operation = server.takeRequest().requestLine
        assertTrue("type=multiburza&amount=1&" in operation, operation)
        assertEquals(1, updated.meals[0].exchangedQuantity)
    }

    @Test
    fun `a put that leaves the day unchanged is not reported as success`() = runTest {
        val meal = load("day_ordered_burza_offer.html")
        server.enqueue(ok(fixture("synthetic_order_ok.html")))
        server.enqueue(ok(fixture("day_ordered_burza_offer.html")))

        assertFailsWith<CanteenException.OrderNotApplied> { client.putIntoExchange(day, meal, 1) }
    }

    @Test
    fun `withdrawing a meal is confirmed by the day`() = runTest {
        val meal = load("day_ordered_in_exchange.html")
        server.enqueue(ok(fixture("synthetic_order_ok.html")))
        server.enqueue(ok(fixture("day_ordered_burza_offer.html")))

        val updated = client.withdrawFromExchange(day, meal)

        val operation = server.takeRequest().requestLine
        assertTrue("type=minusburza" in operation, operation)
        assertEquals(0, updated.meals[0].exchangedQuantity)
    }

    @Test
    fun `a withdraw that was too late is reported and nothing else is sent`() = runTest {
        val meal = load("day_ordered_in_exchange.html")
        server.enqueue(ok(fixture("synthetic_order_error.html")))

        assertFailsWith<CanteenException.OrderRejected> { client.withdrawFromExchange(day, meal) }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `meals without the operations are refused before any request`() = runTest {
        server.enqueue(ok(fixture("day_closed.html")))
        val meal = client.menu(day).meals[0]
        server.takeRequest()

        assertFailsWith<IllegalArgumentException> { client.putIntoExchange(day, meal, 1) }
        assertFailsWith<IllegalArgumentException> { client.withdrawFromExchange(day, meal) }
        assertEquals(1, server.requestCount)
    }
}
