package yt.ivy.fastcanteen.ui

import yt.ivy.fastcanteen.core.model.Dish
import yt.ivy.fastcanteen.core.model.Meal
import yt.ivy.fastcanteen.core.model.MealState
import yt.ivy.fastcanteen.core.model.MealTimings
import yt.ivy.fastcanteen.ui.lunch.CommonDishes
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonDishesTest {
    private fun dish(name: String) = Dish(name, emptyList())

    private val soup = Dish("Polévka hovězí", listOf("1", "3"))

    private fun meal(vararg dishes: Dish) = Meal(
        course = "Obědy", label = "Oběd", dishes = dishes.toList(), price = null, state = MealState.ORDERABLE,
        quantity = 0, lockedReason = null, action = null, timings = MealTimings(null, null, null, null, null, null),
    )

    @Test
    fun `a soup shared by both meals is found`() {
        assertEquals(1, CommonDishes.leadingCount(listOf(meal(soup, dish("Řízek")), meal(soup, dish("Rizoto")))))
    }

    @Test
    fun `different soups or allergens share nothing`() {
        assertEquals(0, CommonDishes.leadingCount(listOf(meal(soup, dish("Řízek")), meal(dish("Polévka čočková"), dish("Rizoto")))))
        assertEquals(0, CommonDishes.leadingCount(listOf(meal(soup, dish("Řízek")), meal(soup.copy(allergens = listOf("1")), dish("Rizoto")))))
    }

    @Test
    fun `a single meal never has shared dishes`() {
        assertEquals(0, CommonDishes.leadingCount(listOf(meal(soup, dish("Řízek")))))
        assertEquals(0, CommonDishes.leadingCount(emptyList()))
    }

    @Test
    fun `every meal keeps at least one dish`() {
        assertEquals(0, CommonDishes.leadingCount(listOf(meal(soup), meal(soup, dish("Rizoto")))))
        assertEquals(0, CommonDishes.leadingCount(listOf(meal(soup), meal(soup))))
    }

    @Test
    fun `only the shared prefix counts`() {
        val a = meal(soup, dish("Salát"), dish("Řízek"))
        val b = meal(soup, dish("Salát"), dish("Rizoto"))
        assertEquals(2, CommonDishes.leadingCount(listOf(a, b)))
    }
}
