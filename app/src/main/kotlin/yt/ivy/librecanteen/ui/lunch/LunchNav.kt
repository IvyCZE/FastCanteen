package yt.ivy.librecanteen.ui.lunch

import yt.ivy.librecanteen.data.CalendarMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Date arithmetic behind the week and month views. Weekends are included, because some canteens serve on them. */
object LunchNav {
    fun isWeekend(day: LocalDate): Boolean = day.dayOfWeek >= DayOfWeek.SATURDAY

    fun monday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Monday to Sunday of the week containing [day]. */
    fun week(day: LocalDate): List<LocalDate> = monday(day).let { m -> (0L..6L).map { m.plusDays(it) } }

    fun monthDays(month: YearMonth): List<LocalDate> = (1..month.lengthOfMonth()).map { month.atDay(it) }

    fun next(anchor: LocalDate, mode: CalendarMode): LocalDate =
        if (mode == CalendarMode.WEEK) anchor.plusWeeks(1) else anchor.plusDays(1)

    fun previous(anchor: LocalDate, mode: CalendarMode): LocalDate =
        if (mode == CalendarMode.WEEK) anchor.minusWeeks(1) else anchor.minusDays(1)

    /** Same day of the month, or the last day of a shorter month. */
    fun shiftMonth(anchor: LocalDate, months: Long): LocalDate = anchor.plusMonths(months)

    /** Moves to [month], keeping the day of the month where possible; today when [month] is the current one. */
    fun moveToMonth(anchor: LocalDate, month: YearMonth, today: LocalDate): LocalDate =
        if (month == YearMonth.from(today)) today else month.atDay(minOf(anchor.dayOfMonth, month.lengthOfMonth()))

    /** Days to load for the current view; in month view the selected day comes first. */
    fun visibleDays(anchor: LocalDate, mode: CalendarMode): List<LocalDate> =
        if (mode == CalendarMode.WEEK) week(anchor)
        else monthDays(YearMonth.from(anchor)).let { days -> days.filter { it == anchor } + days.filter { it != anchor } }

    /** Calendar grid cells, Monday first; null cells pad the first and last week. */
    fun monthCells(month: YearMonth): List<LocalDate?> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val days = monthDays(month)
        val cells = List<LocalDate?>(leading) { null } + days
        return cells + List<LocalDate?>((7 - cells.size % 7) % 7) { null }
    }
}
