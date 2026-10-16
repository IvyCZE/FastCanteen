package yt.ivy.librecanteen.core.icanteen

import org.jsoup.Jsoup
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.model.Allergen
import yt.ivy.librecanteen.core.model.AllergenSettings
import yt.ivy.librecanteen.core.model.Money
import yt.ivy.librecanteen.core.model.OrderRecord
import yt.ivy.librecanteen.core.model.Payment
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val DATE_CELL = Regex("""\d{1,2}\.\d{1,2}\.\d{4}""")

private fun String.clean(): String = replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()

private fun parseDate(s: String): LocalDate? =
    try {
        LocalDate.parse(s.clean(), DATE)
    } catch (_: DateTimeParseException) {
        null
    }

/** Parses amounts such as `1 000,00 Kč` or `45,00 Kč (1 ks)`; only the first amount is read. */
internal fun parseCzechMoney(text: String): Money? {
    val match = Regex("""-?\d[\d\s\u00a0]*,\d{2}""").find(text) ?: return null
    val number = match.value.filter { it.isDigit() || it == '-' || it == ',' }.replace(',', '.')
    return number.toBigDecimalOrNull()?.let { Money.of(it) }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }

internal object OrderHistoryParser {
    private val ORDERED_AT = Regex("""(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s*(?:\((.*?)\))?""")
    private val COUNT = Regex("""(\d+)""")

    fun parse(html: String): List<OrderRecord> {
        val table = Jsoup.parse(html).selectFirst("table.tableDataShow")
            ?: throw CanteenException.UnsupportedLayout("order history table not found")
        return table.select("tr").mapNotNull { row ->
            val cells = row.select("> td").map { it.text().clean() }
            if (cells.size != 8) return@mapNotNull null
            val date = parseDate(cells[0]) ?: return@mapNotNull null
            val orderedAt = ORDERED_AT.find(cells[3])
            OrderRecord(
                date = date,
                label = cells[1],
                dishes = DayMenuParser.parseDishes(cells[2]),
                orderedAt = orderedAt?.groupValues?.get(1)?.let {
                    try {
                        LocalDateTime.parse(it, DATE_TIME)
                    } catch (_: DateTimeParseException) {
                        null
                    }
                },
                orderedVia = orderedAt?.groupValues?.get(2)?.takeIf { it.isNotEmpty() },
                pickupPoint = cells[4],
                orderedCount = COUNT.find(cells[5])?.value?.toInt() ?: 0,
                issuedCount = COUNT.find(cells[6])?.value?.toInt() ?: 0,
                price = parseCzechMoney(cells[7]),
            )
        }
    }
}

internal object PaymentsParser {
    fun parse(html: String): List<Payment> {
        val table = Jsoup.parse(html).selectFirst("table.tableDataShow")
            ?: throw CanteenException.UnsupportedLayout("payments table not found")
        return table.select("tr").mapNotNull { row ->
            val cells = row.select("> td").map { it.text().clean() }
            if (cells.size != 5 || !DATE_CELL.matches(cells[0])) return@mapNotNull null
            Payment(
                date = parseDate(cells[0]) ?: return@mapNotNull null,
                method = cells[1],
                kind = cells[2],
                amount = parseCzechMoney(cells[3]) ?: return@mapNotNull null,
                note = cells[4],
            )
        }
    }
}

internal object OrderResponseParser {
    private val MESSAGE = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    /**
     * An order request is answered either with the refreshed credit markup (success)
     * or with a JSON object carrying a `message` (refusal).
     */
    fun check(body: String) {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val message = MESSAGE.find(trimmed)?.groupValues?.get(1)?.let(::unescapeJson)
            throw CanteenException.OrderRejected(message ?: "The canteen refused the request")
        }
        if (Jsoup.parse(trimmed).selectFirst("#kreditInclude") == null) {
            throw CanteenException.UnsupportedLayout("unexpected order response")
        }
    }

    private fun unescapeJson(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                out.append(c)
                i++
                continue
            }
            when (val n = s[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'u' -> {
                    val hex = s.substring(i + 2, minOf(i + 6, s.length))
                    out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                    i += 4
                }
                else -> out.append(n)
            }
            i += 2
        }
        return out.toString()
    }
}

internal object PasswordFormParser {
    /** Hidden inputs of the password form (CSRF token, JSF view state, submit marker). */
    fun hiddenFields(html: String): Map<String, String> {
        val form = Jsoup.parse(html).selectFirst("form#passwordSetting")
            ?: throw CanteenException.UnsupportedLayout("password form not found")
        val fields = form.select("input[type=hidden][name]").associate { it.attr("name") to it.attr("value") }
        if ("_csrf" !in fields || "javax.faces.ViewState" !in fields) {
            throw CanteenException.UnsupportedLayout("password form is missing hidden fields")
        }
        return fields
    }

    /** The canteen reports the outcome of a settings form in a notification script. */
    fun resultMessage(html: String): String? {
        val text = Regex("""notify\(\s*"create"\s*,\s*\{.*?text:\s*'(.*?)'\s*\}""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return null
        val items = Jsoup.parse(text).select("li").map { it.text().clean() }.filter { it.isNotEmpty() }
        return items.joinToString(" ").ifEmpty { Jsoup.parse(text).text().clean() }.ifEmpty { null }
    }
}

/** The allergen form as submitted by the browser: field names plus the current values. */
internal class AllergenForm(
    val settings: AllergenSettings,
    val fieldByCode: Map<String, String>,
    val modeField: String,
    val hidden: Map<String, String>,
)

internal object AllergenSettingsParser {
    private val LABEL = Regex("""^(\d{1,2}[a-z]?)\s+-\s+(.*?)(?:\s+-\s*)?$""")

    fun parse(html: String): AllergenSettings = parseForm(html).settings

    fun parseForm(html: String): AllergenForm {
        val form = Jsoup.parse(html).selectFirst("form#alergens")
            ?: throw CanteenException.UnsupportedLayout("allergen form not found")
        val fields = LinkedHashMap<String, String>()
        val allergens = form.select("input[type=checkbox]").map { box ->
            val label = form.selectFirst("label[for=${box.id()}]")?.text()?.clean().orEmpty()
            val match = LABEL.find(label) ?: throw CanteenException.UnsupportedLayout("allergen label: $label")
            fields[match.groupValues[1]] = box.attr("name")
            Allergen(match.groupValues[1], match.groupValues[2], box.hasAttr("checked"))
        }
        if (allergens.isEmpty()) throw CanteenException.UnsupportedLayout("allergen form has no allergens")
        val select = form.selectFirst("select[name]")
            ?: throw CanteenException.UnsupportedLayout("allergen form has no display mode")
        val mode = select.selectFirst("option[selected]")?.attr("value")?.toIntOrNull() ?: 1
        val hidden = form.select("input[type=hidden][name]").associate { it.attr("name") to it.attr("value") }
        if ("_csrf" !in hidden || "javax.faces.ViewState" !in hidden) {
            throw CanteenException.UnsupportedLayout("allergen form is missing hidden fields")
        }
        return AllergenForm(AllergenSettings(allergens, mode), fields, select.attr("name"), hidden)
    }
}
