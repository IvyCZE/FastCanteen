package yt.ivy.fastcanteen.ui

import yt.ivy.fastcanteen.data.CalendarMode
import yt.ivy.fastcanteen.ui.lunch.LunchNav
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LunchNavTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `day arrows move one day and week arrows move whole weeks`() {
        assertEquals(d("2026-10-17"), LunchNav.next(d("2026-10-16"), CalendarMode.MONTH))
        assertEquals(d("2026-10-18"), LunchNav.previous(d("2026-10-19"), CalendarMode.MONTH))
        assertEquals(d("2026-10-28"), LunchNav.next(d("2026-10-21"), CalendarMode.WEEK))
        assertEquals(d("2026-10-14"), LunchNav.previous(d("2026-10-21"), CalendarMode.WEEK))
    }

    @Test
    fun `week lists monday to sunday`() {
        assertEquals(
            listOf("2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18").map(::d),
            LunchNav.week(d("2026-10-14")),
        )
        assertEquals(LunchNav.week(d("2026-10-12")), LunchNav.week(d("2026-10-18")))
    }

    @Test
    fun `month shift keeps the day or the last day of a shorter month`() {
        assertEquals(d("2026-11-14"), LunchNav.shiftMonth(d("2026-10-14"), 1))
        assertEquals(d("2026-02-28"), LunchNav.shiftMonth(d("2026-01-31"), 1))
        assertEquals(d("2025-12-31"), LunchNav.shiftMonth(d("2026-01-31"), -1))
    }

    @Test
    fun `picking a month keeps the day, clamps it, and selects today in the current month`() {
        val today = d("2026-10-19")
        assertEquals(d("2026-02-28"), LunchNav.moveToMonth(d("2026-01-31"), YearMonth.of(2026, 2), today))
        assertEquals(d("2027-03-12"), LunchNav.moveToMonth(d("2026-10-12"), YearMonth.of(2027, 3), today))
        assertEquals(today, LunchNav.moveToMonth(d("2026-09-02"), YearMonth.of(2026, 10), today))
    }

    @Test
    fun `visible days in month view start with the selected day and include weekends`() {
        val days = LunchNav.visibleDays(d("2026-10-14"), CalendarMode.MONTH)
        assertEquals(d("2026-10-14"), days.first())
        assertEquals(31, days.size)
        assertEquals(31, days.toSet().size)
    }

    @Test
    fun `month grid is padded to whole weeks starting on monday`() {
        val cells = LunchNav.monthCells(YearMonth.of(2026, 10))
        assertEquals(0, cells.size % 7)
        assertNull(cells[0])
        assertNull(cells[1])
        assertNull(cells[2])
        assertEquals(d("2026-10-01"), cells[3])
        assertEquals(d("2026-10-31"), cells.last { it != null })
    }
}
