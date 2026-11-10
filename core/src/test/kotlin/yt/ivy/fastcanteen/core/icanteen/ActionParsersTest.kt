package yt.ivy.fastcanteen.core.icanteen

import yt.ivy.fastcanteen.core.CanteenException
import yt.ivy.fastcanteen.core.model.Money
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionParsersTest {
    @Test
    fun `order history lists each order with its issue state`() {
        val orders = OrderHistoryParser.parse(fixture("objednavky.html"))
        assertEquals(7, orders.size)

        val newest = orders.first()
        assertEquals(LocalDate.of(2026, 10, 23), newest.date)
        assertEquals("Oběd 2", newest.label)
        assertEquals(LocalDateTime.of(2026, 10, 16, 17, 36, 12), newest.orderedAt)
        assertEquals("Internet", newest.orderedVia)
        assertEquals(1, newest.orderedCount)
        assertEquals(0, newest.issuedCount)
        assertEquals(Money(4500), newest.price)

        val issued = orders.single { it.date == LocalDate.of(2026, 10, 16) }
        assertEquals(1, issued.issuedCount)
    }

    @Test
    fun `dish with plain parentheses keeps them and only numeric codes become allergens`() {
        val dishes = DayMenuParser.parseDishes("Polévka (1,3,9,) ; Ratatouille (cuketa,paprika,lilek), rýže")
        assertEquals(listOf("1", "3", "9"), dishes[0].allergens)
        assertEquals("Ratatouille (cuketa,paprika,lilek), rýže", dishes[1].name)
        assertTrue(dishes[1].allergens.isEmpty())
    }

    @Test
    fun `payments parse amounts with thousands separators`() {
        val payments = PaymentsParser.parse(fixture("platby.html"))
        assertEquals(3, payments.size)
        assertEquals(Money(100_000), payments.first().amount)
        assertEquals("Sporožiro", payments.first().method)
        assertEquals("Stravné", payments.first().kind)
        assertEquals(LocalDate.of(2026, 10, 12), payments.first().date)
    }

    @Test
    fun `money parsing handles non-breaking spaces and trailing quantity`() {
        assertEquals(Money(100_000), parseCzechMoney("1\u00a0000,00\u00a0Kč"))
        assertEquals(Money(4500), parseCzechMoney("45,00 Kč (1 ks)"))
        assertNull(parseCzechMoney("zdarma"))
    }

    @Test
    fun `tables missing from the page are unsupported`() {
        assertFailsWith<CanteenException.UnsupportedLayout> { OrderHistoryParser.parse("<html></html>") }
        assertFailsWith<CanteenException.UnsupportedLayout> { PaymentsParser.parse("<html></html>") }
    }

    @Test
    fun `successful order response passes`() {
        OrderResponseParser.check(fixture("synthetic_order_ok.html"))
    }

    @Test
    fun `refused order response carries the canteen's message`() {
        val error = assertFailsWith<CanteenException.OrderRejected> {
            OrderResponseParser.check(fixture("synthetic_order_error.html"))
        }
        assertEquals("Objednávky jsou již uzavřeny.", error.reason)
    }

    @Test
    fun `unexpected order response is unsupported`() {
        assertFailsWith<CanteenException.UnsupportedLayout> { OrderResponseParser.check("<html>hi</html>") }
    }

    @Test
    fun `password form exposes hidden fields`() {
        val fields = PasswordFormParser.hiddenFields(fixture("password_form.html"))
        assertEquals("VIEWSTATE", fields["javax.faces.ViewState"])
        assertEquals("1", fields["passwordSetting_SUBMIT"])
        assertNotNull(fields["_csrf"])
    }

    @Test
    fun `password result message is extracted from the notification`() {
        assertEquals(
            "Změna hesla se nezdařila - nezadáno správné stávající heslo",
            PasswordFormParser.resultMessage(fixture("password_rejected.html")),
        )
        assertNull(PasswordFormParser.resultMessage("<html></html>"))
    }
}
