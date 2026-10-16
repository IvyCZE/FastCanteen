package yt.ivy.librecanteen.core.icanteen

import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.model.ActionType
import yt.ivy.librecanteen.core.model.Dish
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.core.model.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun fixture(name: String): String =
    checkNotNull(ParsersTest::class.java.getResource("/fixtures/icanteen/2.22/$name")) { "missing fixture $name" }
        .readText()

class ParsersTest {
    @Test
    fun `login page exposes csrf token`() {
        assertEquals("00000000-0000-0000-0000-000000000000", LoginPageParser.csrfToken(fixture("login.html")))
    }

    @Test
    fun `login page without form is unsupported`() {
        assertFailsWith<CanteenException.UnsupportedLayout> { LoginPageParser.csrfToken("<html></html>") }
    }

    @Test
    fun `status reads name, credit and last orderable day`() {
        val status = StatusParser.parse(fixture("main_today.html"))
        assertEquals("Petr Novák", status.displayName)
        assertEquals(Money(100_000), status.credit)
        assertEquals(LocalDate.of(2026, 10, 23), status.orderableUntil)
    }

    @Test
    fun `status without credit is unsupported`() {
        assertFailsWith<CanteenException.UnsupportedLayout> { StatusParser.parse("<html></html>") }
    }

    @Test
    fun `day with a cancellable order and a reorderable alternative`() {
        val menu = DayMenuParser.parse(LocalDate.of(2026, 10, 20), fixture("day_ordered.html"))
        assertEquals(2, menu.meals.size)

        val first = menu.meals[0]
        assertEquals("Obědy", first.course)
        assertEquals("Oběd 1", first.label)
        assertEquals(MealState.ORDERED, first.state)
        assertEquals(1, first.quantity)
        assertEquals(Money(4500), first.price)
        assertEquals(ActionType.DELETE, first.action?.type)
        assertTrue(first.action!!.path.startsWith("db/dbProcessOrder.jsp?"))
        assertNull(first.lockedReason)
        assertEquals(LocalTime.of(11, 0), first.timings.pickupFrom)
        assertEquals(LocalTime.of(15, 0), first.timings.pickupTo)
        assertEquals(LocalDateTime.of(2026, 10, 19, 13, 0), first.timings.orderUntil)
        assertEquals(LocalDateTime.of(2026, 10, 20, 7, 0), first.timings.cancelUntil)
        assertEquals(313, first.timings.ordersCount)
        assertEquals(320, first.timings.ordersLimit)

        val second = menu.meals[1]
        assertEquals("Oběd 2", second.label)
        assertEquals(MealState.REORDERABLE, second.state)
        assertEquals(0, second.quantity)
        assertEquals(ActionType.REORDER, second.action?.type)
    }

    @Test
    fun `closed day locks both meals with the canteen's reason`() {
        val menu = DayMenuParser.parse(LocalDate.of(2026, 10, 19), fixture("day_closed.html"))
        assertEquals(listOf(MealState.LOCKED, MealState.LOCKED), menu.meals.map { it.state })
        assertEquals("Objednávky jsou již uzavřeny.", menu.meals[0].lockedReason)
    }

    @Test
    fun `main page contains the same day markup, including an issued order`() {
        val menu = DayMenuParser.parse(LocalDate.of(2026, 10, 16), fixture("main_today.html"))
        assertEquals(MealState.ORDERED_LOCKED, menu.meals[0].state)
        assertEquals(1, menu.meals[0].quantity)
        assertEquals("Objednávka je již vydaná.", menu.meals[0].lockedReason)
        assertEquals(MealState.LOCKED, menu.meals[1].state)
    }

    @Test
    fun `dishes are split with allergens extracted`() {
        val menu = DayMenuParser.parse(LocalDate.of(2026, 10, 16), fixture("main_today.html"))
        assertEquals(
            listOf(
                Dish("Rajská polévka s těstovinou", listOf("1", "3", "9")),
                Dish("Hovězí guláš, těstoviny", emptyList()),
            ),
            menu.meals[0].dishes,
        )
    }

    @Test
    fun `allergen codes with letters are kept`() {
        val dishes = DayMenuParser.parseDishes("Chléb ( 1a, 1b ) ; Máslo (7)")
        assertEquals(listOf("1a", "1b"), dishes[0].allergens)
        assertEquals(listOf("7"), dishes[1].allergens)
    }

    @Test
    fun `day without menu is empty rather than an error`() {
        val menu = DayMenuParser.parse(LocalDate.of(2026, 10, 17), fixture("day_empty.html"))
        assertTrue(menu.meals.isEmpty())
    }

    @Test
    fun `unrelated markup is unsupported`() {
        assertFailsWith<CanteenException.UnsupportedLayout> {
            DayMenuParser.parse(LocalDate.of(2026, 10, 17), "<html><body>nope</body></html>")
        }
    }

    @Test
    fun `fixtures contain no real order tokens`() {
        val action = DayMenuParser.parse(LocalDate.of(2026, 10, 20), fixture("day_ordered.html")).meals[0].action
        assertNotNull(action)
        assertTrue("token=TOKEN" in action.path)
    }
}
