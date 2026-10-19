package yt.ivy.librecanteen.core.icanteen

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.model.AccountStatus
import yt.ivy.librecanteen.core.model.ActionType
import yt.ivy.librecanteen.core.model.DayMenu
import yt.ivy.librecanteen.core.model.Dish
import yt.ivy.librecanteen.core.model.ExchangePutAction
import yt.ivy.librecanteen.core.model.ExchangeWithdrawAction
import yt.ivy.librecanteen.core.model.Meal
import yt.ivy.librecanteen.core.model.MealAction
import yt.ivy.librecanteen.core.model.MealState
import yt.ivy.librecanteen.core.model.MealTimings
import yt.ivy.librecanteen.core.model.Money
import yt.ivy.librecanteen.core.model.NoteAction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

private fun String.normalized(): String = replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()

internal object LoginPageParser {
    fun csrfToken(html: String): String {
        val doc = Jsoup.parse(html)
        val input = doc.selectFirst("form[action*=j_spring_security_check] input[name=_csrf]")
            ?: doc.selectFirst("input[name=_csrf]")
        return input?.attr("value")?.takeIf { it.isNotBlank() }
            ?: throw CanteenException.UnsupportedLayout("login form has no CSRF token")
    }
}

internal object StatusParser {
    fun parse(html: String): AccountStatus {
        val doc = Jsoup.parse(html)
        val credit = doc.selectFirst("#creditMeta[data-credit]")?.attr("data-credit")?.toBigDecimalOrNull()
            ?: throw CanteenException.UnsupportedLayout("credit not found")
        val first = doc.selectFirst("[id=top:status:firstName]")?.text().orEmpty()
        val last = doc.selectFirst("[id=top:status:lastName]")?.text().orEmpty()
        val until = doc.selectFirst("#PoslObj")?.text()?.normalized()?.let {
            try {
                LocalDate.parse(it, DATE_FORMAT)
            } catch (_: DateTimeParseException) {
                null
            }
        }
        return AccountStatus("$first $last".trim(), Money.of(credit), until)
    }
}

internal object DayMenuParser {
    private val ACTION_URL = Regex("""ajaxOrder\(\s*\w+\s*,\s*'([^']+)'""")
    private val TYPE_PARAM = Regex("""[?&]type=([^&]*)""")
    private val QUANTITY = Regex("""(\d+)\s*ks""")
    private val ISSUED_REASON = Regex("""je již vydan""")
    private val ALLERGENS = Regex("""\(\s*(\d{1,2}[a-z]?(?:\s*,\s*\d{1,2}[a-z]?)*)\s*,?\s*\)""")
    private val PICKUP = Regex("""(\d{1,2}:\d{2}(?::\d{2})?)\D+(\d{1,2}:\d{2}(?::\d{2})?)""")
    private val NOTE_ACTION = Regex("""'(db/dbProcessOrder\.jsp\?[^']*?type=note&note=)'\s*\+[^+]+\+\s*'([^']*)'""")
    private val EXCHANGE_PUT = Regex("""'(db/dbProcessOrder\.jsp\?[^']*?type=multiburza&amount=)'\s*\+[^+]+\+\s*'([^']*)'""")
    private val LIMIT = Regex("""(\d+)\s*/\s*(\d+)""")

    fun parse(date: LocalDate, html: String): DayMenu {
        val doc = Jsoup.parse(html)
        val content = doc.selectFirst("#orderContent$date")
            ?: throw CanteenException.UnsupportedLayout("no order content for $date")

        val rows = content.select(".typ-chodu-separator, .jidelnicekItem")
        if (rows.none { it.hasClass("jidelnicekItem") }) {
            return DayMenu(date, emptyList())
        }

        var course = ""
        val meals = mutableListOf<Meal>()
        for (row in rows) {
            if (row.hasClass("typ-chodu-separator")) {
                course = row.selectFirst(".text")?.text()?.normalized().orEmpty()
            } else {
                meals += parseMeal(course, row)
            }
        }
        return DayMenu(date, meals)
    }

    private fun parseMeal(course: String, item: Element): Meal {
        val button = item.selectFirst("a.button-link-main")
            ?: throw CanteenException.UnsupportedLayout("meal without order button")
        val label = item.selectFirst(".smallBoldTitle")?.text()?.normalized()
            ?: throw CanteenException.UnsupportedLayout("meal without label")
        val description = item.selectFirst(".jidWrapCenter")?.text()?.normalized().orEmpty()

        val path = ACTION_URL.find(button.attr("onclick"))?.groupValues?.get(1)
        val rawType = path?.let { TYPE_PARAM.find(it)?.groupValues?.get(1) }
        val type = when (rawType) {
            "make" -> ActionType.MAKE
            "delete" -> ActionType.DELETE
            "reorder" -> ActionType.REORDER
            "none" -> ActionType.NONE
            else -> ActionType.OTHER
        }
        val action = path?.let { MealAction(type, it) }

        val checkbox = item.selectFirst("a.order-checkbox")
        val ordered = checkbox?.hasClass("checked") == true || checkbox?.attr("aria-checked") == "true"
        val disabled = button.hasClass("disabled")

        // Unknown action types are treated as locked so the UI never offers an operation we cannot interpret.
        val state = when {
            disabled || type == ActionType.NONE || type == ActionType.OTHER ->
                if (ordered) MealState.ORDERED_LOCKED else MealState.LOCKED
            type == ActionType.DELETE -> MealState.ORDERED
            type == ActionType.REORDER -> MealState.REORDERABLE
            else -> MealState.ORDERABLE
        }

        val quantity = QUANTITY.find(button.selectFirst(".warning")?.text()?.normalized().orEmpty())
            ?.groupValues?.get(1)?.toInt()
            ?: if (ordered) 1 else 0

        val exchangePut = item.selectFirst("button[id^=burza-amount][id$=submit]")?.attr("onclick")
            ?.let { EXCHANGE_PUT.find(it) }
            ?.let { found ->
                val max = item.selectFirst("input[id^=burza-amount]")?.attr("max")?.toIntOrNull() ?: 1
                ExchangePutAction(found.groupValues[1], found.groupValues[2], max)
            }
        val exchangeWithdraw = item.selectFirst("a[onclick*=type=minusburza]")?.let { link ->
            val withdrawPath = ACTION_URL.find(link.attr("onclick"))?.groupValues?.get(1)
            withdrawPath?.let { ExchangeWithdrawAction(it, QUANTITY.find(link.text())?.groupValues?.get(1)?.toInt() ?: 1) }
        }

        val reason = item.selectFirst("[id^=reason-]")?.text()?.normalized()?.takeIf { it.isNotEmpty() }

        return Meal(
            course = course,
            label = label,
            dishes = parseDishes(description),
            price = button.attr("data-price").toBigDecimalOrNull()?.let { Money.of(it) },
            state = state,
            quantity = quantity,
            lockedReason = if (state == MealState.LOCKED || state == MealState.ORDERED_LOCKED) reason else null,
            timings = parseTimings(item),
            action = action,
            inExchange = exchangeWithdraw != null || item.selectFirst("i.fa-exchange") != null,
            // The canteen stores notes HTML-escaped and escapes them again in the input, so one more decode is needed.
            note = item.selectFirst("input[id^=note][id$=text]")?.attr("value")?.let { Parser.unescapeEntities(it, false) }?.trim(),
            noteAction = item.selectFirst("input[id^=note][id$=submit]")?.attr("onclick")?.let { NOTE_ACTION.find(it) }
                ?.let { NoteAction(it.groupValues[1], it.groupValues[2]) },
            exchangePut = exchangePut,
            exchangeWithdraw = exchangeWithdraw,
            issued = state == MealState.ORDERED_LOCKED && reason?.let { ISSUED_REASON.containsMatchIn(it) } == true,
        )
    }

    fun parseDishes(description: String): List<Dish> =
        description.split(';').mapNotNull { part ->
            val allergens = ALLERGENS.findAll(part).flatMap { m ->
                m.groupValues[1].split(',').map { it.trim() }
            }.toList()
            val name = part.replace(ALLERGENS, "").normalized().replace(Regex("\\s+,"), ",")
            name.takeIf { it.isNotEmpty() }?.let { Dish(it, allergens) }
        }

    private fun parseTimings(item: Element): MealTimings {
        // The same card is rendered twice (desktop and mobile); the first occurrence is enough.
        val firstSeen = LinkedHashMap<String, String>()
        item.select(".timing-row").forEach { row ->
            val key = row.selectFirst(".timing-label")?.text()?.normalized().orEmpty()
            if (key.isNotEmpty() && key !in firstSeen) {
                firstSeen[key] = row.children().lastOrNull { !it.hasClass("timing-label") }?.text()?.normalized().orEmpty()
            }
        }

        val pickup = firstSeen["výdej:"]?.let { PICKUP.find(it) }
        val limit = firstSeen["limit objednávek:"]?.let { LIMIT.find(it) }
        return MealTimings(
            pickupFrom = pickup?.groupValues?.get(1)?.let(::parseTime),
            pickupTo = pickup?.groupValues?.get(2)?.let(::parseTime),
            orderUntil = firstSeen["objednat do:"]?.let(::parseDateTime),
            cancelUntil = firstSeen["zrušit do:"]?.let(::parseDateTime),
            ordersCount = limit?.groupValues?.get(1)?.toInt(),
            ordersLimit = limit?.groupValues?.get(2)?.toInt(),
        )
    }

    private fun parseTime(s: String): LocalTime? =
        try {
            LocalTime.parse(if (s.count { it == ':' } == 1) "$s:00" else s.padStart(8, '0'))
        } catch (_: DateTimeParseException) {
            null
        }

    private fun parseDateTime(s: String): LocalDateTime? =
        try {
            LocalDateTime.parse(s, DATE_TIME_FORMAT)
        } catch (_: DateTimeParseException) {
            null
        }
}
