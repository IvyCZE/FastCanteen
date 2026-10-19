package yt.ivy.librecanteen.core.icanteen

import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.core.model.NoteAction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesAllergensTest {
    @Test
    fun `ordered meal exposes an empty note and a note operation`() {
        val meal = DayMenuParser.parse(LocalDate.of(2026, 10, 20), fixture("day_ordered.html")).meals[0]
        assertEquals("", meal.note)
        val action = assertNotNull(meal.noteAction)
        assertTrue(action.prefix.startsWith("db/dbProcessOrder.jsp?"))
        assertTrue(action.prefix.endsWith("type=note&note="))
        assertTrue(action.suffix.startsWith("&week="))
    }

    @Test
    fun `note text is url encoded`() {
        val path = NoteAction("db/x.jsp?type=note&note=", "&week=").path("bez omáčky & s rýží")
        assertEquals("db/x.jsp?type=note&note=bez+om%C3%A1%C4%8Dky+%26+s+r%C3%BD%C5%BE%C3%AD&week=", path)
    }

    @Test
    fun `stored note is decoded twice because the canteen escapes it twice`() {
        // Captured from the live canteen: the note `a&b =c "d"` comes back like this inside the input's value attribute.
        val stored = "a&amp;amp;b &amp;#61;c &amp;#34;d&amp;#34;"
        val html = fixture("day_ordered.html").replace("placeholder=\"Zadejte poznámku k objednávce\" value=\"\"", "placeholder=\"x\" value=\"$stored\"")
        val meal = DayMenuParser.parse(LocalDate.of(2026, 10, 20), html).meals[0]
        assertEquals("a&b =c \"d\"", meal.note)
    }

    @Test
    fun `sanitized note drops angle bracket openers and line breaks`() {
        assertEquals("1  2", ICanteenClient.sanitizeNote("1 < 2"))
        assertEquals("a b", ICanteenClient.sanitizeNote("  a\nb  "))
    }

    @Test
    fun `meal without an order has no note operation`() {
        val meal = DayMenuParser.parse(LocalDate.of(2026, 10, 20), fixture("day_ordered.html")).meals[1]
        assertNull(meal.noteAction)
        assertNull(meal.note)
    }

    @Test
    fun `locked order in the exchange is flagged`() {
        val meal = DayMenuParser.parse(LocalDate.of(2026, 10, 12), fixture("day_in_exchange.html")).meals.single()
        assertTrue(meal.inExchange)
        assertEquals(MealState.ORDERED_LOCKED, meal.state)
    }

    @Test
    fun `ordinary meals are not in the exchange`() {
        val menu = DayMenuParser.parse(LocalDate.of(2026, 10, 20), fixture("day_ordered.html"))
        assertFalse(menu.meals.any { it.inExchange })
    }

    @Test
    fun `allergen settings list codes names and selection`() {
        val settings = AllergenSettingsParser.parse(fixture("allergens_form.html"))
        assertEquals(28, settings.allergens.size)
        assertEquals(3, settings.displayMode)
        val first = settings.allergens.first()
        assertEquals("1", first.code)
        assertEquals("Obiloviny obsahující lepek", first.name)
        assertEquals(2, settings.selectedCodes.size)
        assertTrue("1" in settings.selectedCodes)
        val wheat = settings.allergens.single { it.code == "1a" }
        assertEquals("Pšenice - Obiloviny obsahující lepek nebo jejich hybridní odrůdy a výrobky z nich", wheat.name)
    }

    @Test
    fun `page without allergen form is unsupported`() {
        assertFailsWith<CanteenException.UnsupportedLayout> { AllergenSettingsParser.parse("<html></html>") }
    }
}

class DishAllergenMatchingTest {
    private val dish = yt.ivy.librecanteen.core.model.Dish("Řízek", listOf("1", "3", "7a"))

    @Test
    fun `exact code and group codes match`() {
        assertTrue(dish.triggers(setOf("3")))
        assertTrue(dish.triggers(setOf("1a")))
        assertTrue(dish.triggers(setOf("7")))
        assertTrue(dish.triggers(setOf("7a")))
    }

    @Test
    fun `different subtype or group does not match`() {
        assertFalse(dish.triggers(setOf("7b")))
        assertFalse(dish.triggers(setOf("9", "11")))
        assertFalse(dish.triggers(emptySet()))
    }

    @Test
    fun `only the matching declared codes are reported`() {
        assertEquals(listOf("1", "7a"), dish.triggeringAllergens(setOf("1a", "7")))
        assertEquals(emptyList(), dish.triggeringAllergens(setOf("9")))
    }
}

class MenuSerializationTest {
    @Test
    fun `menu survives a json round trip`() {
        val menu = DayMenuParser.parse(LocalDate.of(2026, 10, 20), fixture("day_ordered.html"))
        val json = kotlinx.serialization.json.Json
        val text = json.encodeToString(yt.ivy.librecanteen.core.model.DayMenu.serializer(), menu)
        assertEquals(menu, json.decodeFromString(yt.ivy.librecanteen.core.model.DayMenu.serializer(), text))
    }
}
