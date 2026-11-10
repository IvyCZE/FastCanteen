package yt.ivy.fastcanteen.data

import yt.ivy.fastcanteen.core.model.DayMenu
import yt.ivy.fastcanteen.core.model.Meal
import yt.ivy.fastcanteen.core.model.MealState
import yt.ivy.fastcanteen.core.model.MealTimings
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderPolicyTest {
    private fun day(vararg states: MealState) = DayMenu(
        LocalDate.of(2026, 10, 26),
        states.map {
            Meal("Obědy", "Oběd", emptyList(), null, it, 0, null, MealTimings(null, null, null, null, null, null), null)
        },
    )

    @Test
    fun `reminds when meals are open and nothing is ordered`() {
        assertTrue(ReminderPolicy.needsReminder(listOf(day(MealState.ORDERABLE, MealState.ORDERABLE), day(MealState.ORDERABLE))))
    }

    @Test
    fun `stays quiet when something is ordered`() {
        assertFalse(ReminderPolicy.needsReminder(listOf(day(MealState.ORDERED, MealState.REORDERABLE), day(MealState.ORDERABLE))))
    }

    @Test
    fun `stays quiet when nothing can be ordered`() {
        assertFalse(ReminderPolicy.needsReminder(listOf(day(MealState.LOCKED), day())))
        assertFalse(ReminderPolicy.needsReminder(emptyList()))
    }

    @Test
    fun `reminder days start on thursday and target the next monday`() {
        assertFalse(ReminderPolicy.isReminderDay(LocalDate.of(2026, 10, 21)))
        assertTrue(ReminderPolicy.isReminderDay(LocalDate.of(2026, 10, 22)))
        assertTrue(ReminderPolicy.isReminderDay(LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2026, 10, 26), ReminderPolicy.nextMonday(LocalDate.of(2026, 10, 22)))
        assertEquals(LocalDate.of(2026, 10, 26), ReminderPolicy.nextMonday(LocalDate.of(2026, 10, 25)))
    }
}
