package yt.ivy.librecanteen.data

import yt.ivy.librecanteen.core.model.ActionType
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Dish
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.MealAction
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.core.model.MealTimings
import yt.ivy.librecanteen.core.model.Money
import yt.ivy.librecanteen.core.model.NoteAction
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MenuCacheTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val file = File(createTempDirectory("cache").toFile(), "menus.json")

    private fun menu(day: LocalDate) = DayMenu(
        day,
        listOf(
            Meal(
                course = "Obědy", label = "Oběd 1", dishes = listOf(Dish("Polévka", listOf("1", "9"))),
                price = Money(4500), state = MealState.ORDERED, quantity = 1, lockedReason = null,
                timings = MealTimings(null, null, LocalDateTime.of(2026, 10, 19, 13, 0), null, 3, 320),
                action = MealAction(ActionType.DELETE, "db/dbProcessOrder.jsp?token=SECRET"),
                note = "bez cibule", noteAction = NoteAction("a", "b"),
            ),
        ),
    )

    @Test
    fun `menu is restored by a new cache instance without server operations`() {
        FileMenuCache(file) { today }.put(menu(today))

        val restored = assertNotNull(FileMenuCache(file) { today }.get(today))
        val meal = restored.meals.single()
        assertEquals("bez cibule", meal.note)
        assertEquals(Money(4500), meal.price)
        assertNull(meal.action)
        assertNull(meal.noteAction)
        assertEquals(false, file.readText().contains("SECRET"))
    }

    @Test
    fun `old days are dropped`() {
        val cache = FileMenuCache(file) { today }
        cache.put(menu(today.minusDays(30)))
        cache.put(menu(today))

        assertNull(FileMenuCache(file) { today }.get(today.minusDays(30)))
        assertNotNull(FileMenuCache(file) { today }.get(today))
    }

    @Test
    fun `clear removes stored data`() {
        val cache = FileMenuCache(file) { today }
        cache.put(menu(today))
        cache.clear()

        assertNull(FileMenuCache(file) { today }.get(today))
    }

    @Test
    fun `corrupt file is treated as empty`() {
        file.writeText("not json")
        assertNull(FileMenuCache(file) { today }.get(today))
    }
}
