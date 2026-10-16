package yt.ivy.librecanteen.ui.lunch

import yt.ivy.librecanteen.data.CalendarMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Date arithmetic behind the week and month views; canteens only serve on weekdays. */
object LunchNav {
    fun isWeekend(day: LocalDate): Boolean = day.dayOfWeek >= DayOfWeek.SATURDAY

    /** From Saturday on, the interesting day is the coming Monday. */
    fun initialAnchor(today: LocalDate): LocalDate =
        if (isWeekend(today)) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today

    fun monday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekdays(day: LocalDate): List<LocalDate> = monday(day).let { m -> (0L..4L).map { m.plusDays(it) } }

    fun monthWeekdays(month: YearMonth): List<LocalDate> =
        (1..month.lengthOfMonth()).map { month.atDay(it) }.filterNot(::isWeekend)

    fun nextWeekday(day: LocalDate): LocalDate =
        generateSequence(day.plusDays(1)) { it.plusDays(1) }.first { !isWeekend(it) }

    fun previousWeekday(day: LocalDate): LocalDate =
        generateSequence(day.minusDays(1)) { it.minusDays(1) }.first { !isWeekend(it) }

    fun next(anchor: LocalDate, mode: CalendarMode): LocalDate =
        if (mode == CalendarMode.WEEK) anchor.plusWeeks(1) else nextWeekday(anchor)

    fun previous(anchor: LocalDate, mode: CalendarMode): LocalDate =
        if (mode == CalendarMode.WEEK) anchor.minusWeeks(1) else previousWeekday(anchor)

    /** Same day of the month if possible, otherwise the nearest weekday that stays in the target month. */
    fun shiftMonth(anchor: LocalDate, months: Long): LocalDate {
        val target = anchor.plusMonths(months)
        if (!isWeekend(target)) return target
        val friday = target.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
        return if (YearMonth.from(friday) == YearMonth.from(target)) friday else target.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    }

    /** Days to load for the current view; in month view the selected day comes first. */
    fun visibleDays(anchor: LocalDate, mode: CalendarMode): List<LocalDate> =
        if (mode == CalendarMode.WEEK) weekdays(anchor)
        else monthWeekdays(YearMonth.from(anchor)).let { days -> days.filter { it == anchor } + days.filter { it != anchor } }

    /** Calendar grid cells, Monday first; null cells pad the first and last week. */
    fun monthCells(month: YearMonth): List<LocalDate?> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells = List<LocalDate?>(leading) { null } + days
        return cells + List<LocalDate?>((7 - cells.size % 7) % 7) { null }
    }
}
