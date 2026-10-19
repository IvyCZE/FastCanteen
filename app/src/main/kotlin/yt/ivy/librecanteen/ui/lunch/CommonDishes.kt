package yt.ivy.librecanteen.ui.lunch

import yt.ivy.librecanteen.core.model.Meal

/** Finds what every meal of a day starts with, e.g. a soup that comes with both lunches. */
object CommonDishes {
    /**
     * How many leading dishes all [meals] share. Needs at least two meals, and every meal keeps at least one
     * dish of its own, so a card is never left empty.
     */
    fun leadingCount(meals: List<Meal>): Int {
        if (meals.size < 2) return 0
        val limit = meals.minOf { it.dishes.size } - 1
        var count = 0
        while (count < limit && meals.all { it.dishes[count] == meals[0].dishes[count] }) count++
        return count
    }
}
