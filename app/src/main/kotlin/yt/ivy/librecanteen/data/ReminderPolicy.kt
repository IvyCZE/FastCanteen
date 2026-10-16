package yt.ivy.librecanteen.data

import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.MealState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Decides when to nudge the user to order lunches for the coming week. */
object ReminderPolicy {
    /** Reminders start on Thursday, so there is still time to order before the weekend. */
    fun isReminderDay(today: LocalDate): Boolean = today.dayOfWeek >= DayOfWeek.THURSDAY

    fun nextMonday(today: LocalDate): LocalDate = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    /** True when the week has meals that can still be ordered and none is ordered yet. */
    fun needsReminder(week: List<DayMenu>): Boolean {
        val meals = week.flatMap { it.meals }
        val canOrder = meals.any { it.state == MealState.ORDERABLE || it.state == MealState.REORDERABLE }
        val anyOrdered = meals.any { it.state == MealState.ORDERED || it.state == MealState.ORDERED_LOCKED }
        return canOrder && !anyOrdered
    }
}
