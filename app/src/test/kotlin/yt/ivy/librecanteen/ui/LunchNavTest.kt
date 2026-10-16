package yt.ivy.librecanteen.ui

import yt.ivy.librecanteen.data.CalendarMode
import yt.ivy.librecanteen.ui.lunch.LunchNav
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LunchNavTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `weekend starts on the coming monday`() {
        assertEquals(d("2026-10-19"), LunchNav.initialAnchor(d("2026-10-17")))
        assertEquals(d("2026-10-19"), LunchNav.initialAnchor(d("2026-10-18")))
        assertEquals(d("2026-10-16"), LunchNav.initialAnchor(d("2026-10-16")))
    }

    @Test
    fun `day arrows skip weekends and week arrows move whole weeks`() {
        assertEquals(d("2026-10-19"), LunchNav.next(d("2026-10-16"), CalendarMode.MONTH))
        assertEquals(d("2026-10-16"), LunchNav.previous(d("2026-10-19"), CalendarMode.MONTH))
        assertEquals(d("2026-10-28"), LunchNav.next(d("2026-10-21"), CalendarMode.WEEK))
        assertEquals(d("2026-10-14"), LunchNav.previous(d("2026-10-21"), CalendarMode.WEEK))
    }

    @Test
    fun `week lists monday to friday`() {
        assertEquals(
            listOf("2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16").map(::d),
            LunchNav.weekdays(d("2026-10-14")),
        )
    }

    @Test
    fun `month shift keeps the day or lands on a weekday of the target month`() {
        assertEquals(d("2026-11-12"), LunchNav.shiftMonth(d("2026-10-12"), 1))
        // 2026-11-14 is a Saturday
        assertEquals(d("2026-11-13"), LunchNav.shiftMonth(d("2026-10-14"), 1))
        // 2026-08-01 is a Saturday and Friday before it is in July, so the Monday after is used
        assertEquals(d("2026-08-03"), LunchNav.shiftMonth(d("2026-07-01"), 1))
    }

    @Test
    fun `visible days in month view start with the selected day and skip weekends`() {
        val days = LunchNav.visibleDays(d("2026-10-14"), CalendarMode.MONTH)
        assertEquals(d("2026-10-14"), days.first())
        assertEquals(22, days.size)
        assertEquals(false, days.any { LunchNav.isWeekend(it) })
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
