package yt.ivy.fastcanteen.core.icanteen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import yt.ivy.fastcanteen.core.CanteenException
import yt.ivy.fastcanteen.core.CanteenProvider
import yt.ivy.fastcanteen.core.model.AccountStatus
import yt.ivy.fastcanteen.core.model.AllergenSettings
import yt.ivy.fastcanteen.core.model.ActionType
import yt.ivy.fastcanteen.core.model.Credentials
import yt.ivy.fastcanteen.core.model.DayMenu
import yt.ivy.fastcanteen.core.model.ExchangeOffer
import yt.ivy.fastcanteen.core.model.Meal
import yt.ivy.fastcanteen.core.model.MealState
import yt.ivy.fastcanteen.core.model.exchangedQuantity
import yt.ivy.fastcanteen.core.model.OrderRecord
import yt.ivy.fastcanteen.core.model.Payment
import java.io.IOException
import java.time.LocalDate

/**
 * Scrapes an iCanteen installation. Everything is read from server-rendered HTML,
 * so unknown markup is reported as [CanteenException.UnsupportedLayout] instead of being guessed.
 *
 * Operations that change state are serialized, and their outcome is checked against the server
 * rather than assumed, because the canteen answers them with loosely structured fragments.
 */
public class ICanteenClient(
    private val baseUrl: HttpUrl,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : CanteenProvider {

    private val http = httpClient.newBuilder().cookieJar(MemoryCookieJar()).build()
    private val mutations = Mutex()

    override suspend fun login(credentials: Credentials) {
        val loginPage = fetch(url("login"), checkSession = false)
        val form = FormBody.Builder()
            .add("j_username", credentials.username)
            .add("j_password", credentials.password)
            .add("terminal", "false")
            .add("_csrf", LoginPageParser.csrfToken(loginPage))
            .add("targetUrl", "/faces/secured/main.jsp")
            .build()
        val request = Request.Builder().url(url("j_spring_security_check")).post(form).build()
        val finalUrl = call(request) { it.request.url }
        if (finalUrl.encodedPath.endsWith("/login") || finalUrl.queryParameter("login_error") != null) {
            throw CanteenException.AuthenticationFailed()
        }
    }

    override suspend fun logout() {
        val page = fetch(url("faces/secured/main.jsp"))
        val form = FormBody.Builder().add("_csrf", LoginPageParser.csrfToken(page)).build()
        call(Request.Builder().url(url("logout")).post(form).build()) { }
    }

    override suspend fun status(): AccountStatus =
        StatusParser.parse(fetch(url("faces/secured/main.jsp")))

    override suspend fun menu(day: LocalDate): DayMenu = DayMenuParser.parse(day, fetch(dayUrl(day)))

    override suspend fun order(day: LocalDate, meal: Meal): DayMenu = mutations.withLock {
        val shown = meal.action
        require(shown != null && meal.state in ORDERABLE_STATES) { "Meal cannot be changed: ${meal.state}" }
        if (!shown.path.startsWith(ORDER_ENDPOINT)) {
            throw CanteenException.UnsupportedLayout("unexpected order operation")
        }

        val action = currentMeal(day, meal).action
        // The meal must still offer the same change; anything else means it was changed elsewhere.
        if (action == null || action.type != shown.type) throw CanteenException.ChangedMeanwhile()
        if (!action.path.startsWith(ORDER_ENDPOINT)) {
            throw CanteenException.UnsupportedLayout("unexpected order operation")
        }

        OrderResponseParser.check(fetch("${baseUrl}faces/secured/${action.path}".toHttpUrl()))

        val updated = DayMenuParser.parse(day, fetch(dayUrl(day)))
        val after = updated.meals.firstOrNull { it.label == meal.label && it.course == meal.course }
        val applied = when (action.type) {
            ActionType.MAKE, ActionType.REORDER ->
                after?.state == MealState.ORDERED || after?.state == MealState.ORDERED_LOCKED
            ActionType.DELETE ->
                after != null && after.state != MealState.ORDERED && after.state != MealState.ORDERED_LOCKED
            else -> false
        }
        if (!applied) throw CanteenException.OrderNotApplied()
        updated
    }

    override suspend fun setNote(day: LocalDate, meal: Meal, note: String): DayMenu = mutations.withLock {
        val shown = meal.noteAction ?: throw IllegalArgumentException("Meal cannot carry a note")
        if (!shown.prefix.startsWith(ORDER_ENDPOINT)) {
            throw CanteenException.UnsupportedLayout("unexpected note operation")
        }

        val action = currentMeal(day, meal).noteAction ?: throw CanteenException.ChangedMeanwhile()
        if (!action.prefix.startsWith(ORDER_ENDPOINT)) {
            throw CanteenException.UnsupportedLayout("unexpected note operation")
        }

        val clean = sanitizeNote(note)
        val body = fetch("${baseUrl}faces/secured/${action.path(clean)}".toHttpUrl())
        // Refusals are JSON; the shape of a success is not relied on, the re-read below decides.
        if (body.trim().startsWith("{")) OrderResponseParser.check(body)

        val updated = DayMenuParser.parse(day, fetch(dayUrl(day)))
        val after = updated.meals.firstOrNull { it.label == meal.label && it.course == meal.course }
        if (after?.note != clean) throw CanteenException.OrderNotApplied()
        updated
    }

    override suspend fun exchangeOffers(): List<ExchangeOffer> =
        ExchangeParser.parse(fetch(url("faces/secured/burza.jsp")))

    override suspend fun takeFromExchange(offer: ExchangeOffer): DayMenu = mutations.withLock {
        if (!offer.path.startsWith(ORDER_ENDPOINT) || "type=getburza" !in offer.path) {
            throw CanteenException.UnsupportedLayout("unexpected exchange operation")
        }

        val before = DayMenuParser.parse(offer.date, fetch(dayUrl(offer.date)))
            .meals.firstOrNull { it.label == offer.label }?.quantity ?: 0

        // The link comes from the exchange page read just now, and the offer must still be on it.
        val current = exchangeOffers().firstOrNull { withoutSignature(it.path) == withoutSignature(offer.path) }
            ?: throw CanteenException.ChangedMeanwhile()
        if (!current.path.startsWith(ORDER_ENDPOINT) || "type=getburza" !in current.path) {
            throw CanteenException.UnsupportedLayout("unexpected exchange operation")
        }

        // Success redirects to the main page; refusals are JSON.
        val body = fetch("${baseUrl}faces/secured/${current.path}".toHttpUrl())
        if (body.trim().startsWith("{")) OrderResponseParser.check(body)

        val updated = DayMenuParser.parse(offer.date, fetch(dayUrl(offer.date)))
        val after = updated.meals.firstOrNull { it.label == offer.label }
        if (after == null || after.quantity <= before) throw CanteenException.OrderNotApplied()
        updated
    }

    override suspend fun putIntoExchange(day: LocalDate, meal: Meal, quantity: Int): DayMenu = mutations.withLock {
        val shown = meal.exchangePut ?: throw IllegalArgumentException("Meal cannot be put into the exchange")
        if (!shown.prefix.startsWith(ORDER_ENDPOINT) || "type=multiburza" !in shown.prefix) {
            throw CanteenException.UnsupportedLayout("unexpected exchange operation")
        }
        val current = currentMeal(day, meal)
        val action = current.exchangePut ?: throw CanteenException.ChangedMeanwhile()
        if (quantity > action.maxQuantity) throw CanteenException.ChangedMeanwhile()
        if (!action.prefix.startsWith(ORDER_ENDPOINT) || "type=multiburza" !in action.prefix) {
            throw CanteenException.UnsupportedLayout("unexpected exchange operation")
        }
        changeExchange(day, meal, action.path(quantity)) { after -> after.exchangedQuantity > current.exchangedQuantity }
    }

    override suspend fun withdrawFromExchange(day: LocalDate, meal: Meal): DayMenu = mutations.withLock {
        val shown = meal.exchangeWithdraw ?: throw IllegalArgumentException("Meal is not in the exchange")
        if (!shown.path.startsWith(ORDER_ENDPOINT) || "type=minusburza" !in shown.path) {
            throw CanteenException.UnsupportedLayout("unexpected exchange operation")
        }
        val current = currentMeal(day, meal)
        val action = current.exchangeWithdraw ?: throw CanteenException.ChangedMeanwhile()
        if (!action.path.startsWith(ORDER_ENDPOINT) || "type=minusburza" !in action.path) {
            throw CanteenException.UnsupportedLayout("unexpected exchange operation")
        }
        changeExchange(day, meal, action.path) { after -> after.exchangedQuantity < current.exchangedQuantity }
    }

    /**
     * Reads [day] again and returns [meal] as the server shows it now. Some canteens honor only the page that was
     * loaded last and otherwise answer that several browser windows are open, while the app loads a whole week at
     * once, so the link of an earlier read is stale. A change is therefore built from this read, which is the
     * newest one. It is not a retry: nothing has been sent yet.
     */
    private suspend fun currentMeal(day: LocalDate, meal: Meal): Meal =
        DayMenuParser.parse(day, fetch(dayUrl(day))).meals
            .firstOrNull { it.label == meal.label && it.course == meal.course }
            ?: throw CanteenException.ChangedMeanwhile()

    private suspend fun changeExchange(day: LocalDate, meal: Meal, path: String, applied: (Meal) -> Boolean): DayMenu {
        // The success body is a credit fragment; refusals are JSON. The re-read below decides.
        val body = fetch("${baseUrl}faces/secured/$path".toHttpUrl())
        if (body.trim().startsWith("{")) OrderResponseParser.check(body)

        val updated = DayMenuParser.parse(day, fetch(dayUrl(day)))
        val after = updated.meals.firstOrNull { it.label == meal.label && it.course == meal.course }
        if (after == null || !applied(after)) throw CanteenException.OrderNotApplied()
        return updated
    }

    override suspend fun allergenSettings(): AllergenSettings =
        AllergenSettingsParser.parse(fetch(url("web/setting")))

    override suspend fun saveAllergenSettings(codes: Set<String>, displayMode: Int): AllergenSettings = mutations.withLock {
        require(displayMode in 1..6) { "Unknown display mode" }
        val settingsUrl = url("web/setting")
        val form = AllergenSettingsParser.parseForm(fetch(settingsUrl))
        val unknown = codes - form.fieldByCode.keys
        require(unknown.isEmpty()) { "Unknown allergens: $unknown" }

        val body = FormBody.Builder().apply {
            form.hidden.forEach { (name, value) -> add(name, value) }
            codes.forEach { add(form.fieldByCode.getValue(it), "true") }
            add(form.modeField, displayMode.toString())
            add("alergens:confirm", "Uložit")
        }.build()
        call(Request.Builder().url(settingsUrl).post(body).build()) { it.body.string() }

        // The response is only a notification, so the stored values are read back.
        val after = AllergenSettingsParser.parse(fetch(settingsUrl))
        if (after.selectedCodes != codes || after.displayMode != displayMode) throw CanteenException.OrderNotApplied()
        after
    }

    override suspend fun orderHistory(): List<OrderRecord> =
        OrderHistoryParser.parse(fetch(url("faces/secured/objednavky.jsp")))

    override suspend fun payments(): List<Payment> =
        PaymentsParser.parse(fetch(url("faces/secured/platby.jsp")))

    override suspend fun changePassword(current: Credentials, newPassword: String): Unit = mutations.withLock {
        require(newPassword.isNotBlank()) { "New password is empty" }
        require(newPassword != current.password) { "New password equals the current one" }

        val settingsUrl = url("web/setting")
        val hidden = PasswordFormParser.hiddenFields(fetch(settingsUrl))
        val form = FormBody.Builder().apply {
            hidden.forEach { (name, value) -> add(name, value) }
            add("passwordSetting:StravnikPasswordCurrent", current.password)
            add("passwordSetting:StravnikPassword", newPassword)
            add("passwordSetting:StravnikPasswordSecond", newPassword)
            add("passwordSetting:confirm", "Změnit heslo")
        }.build()
        val response = call(Request.Builder().url(settingsUrl).post(form).build()) { it.body.string() }
        val message = PasswordFormParser.resultMessage(response)

        // The result message is free text, so the outcome is decided by whether the new password works.
        val verifier = ICanteenClient(baseUrl, httpClient)
        try {
            verifier.login(Credentials(current.username, newPassword))
        } catch (_: CanteenException.AuthenticationFailed) {
            throw CanteenException.PasswordChangeRejected(message ?: "The canteen did not accept the new password")
        }
        runCatching { verifier.logout() }
    }

    private fun url(path: String, configure: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        baseUrl.newBuilder().addPathSegments(path).apply(configure).build()

    private fun dayUrl(day: LocalDate): HttpUrl = url("faces/secured/db/dbJidelnicekOnDayView.jsp") {
        addQueryParameter("day", day.toString())
        addQueryParameter("terminal", "false")
        addQueryParameter("rating", "")
        addQueryParameter("printer", "false")
        addQueryParameter("keyboard", "false")
    }

    private suspend fun fetch(url: HttpUrl, checkSession: Boolean = true): String =
        call(Request.Builder().url(url).get().build()) { response ->
            // An expired session is answered with a redirect to the login page.
            if (checkSession && response.request.url.encodedPath.endsWith("/login")) {
                throw CanteenException.SessionExpired()
            }
            response.body.string()
        }

    private suspend fun <T> call(request: Request, handle: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    handle(response)
                }
            } catch (e: IOException) {
                throw CanteenException.Network(e)
            }
        }

    public companion object {
        /**
         * The canteen cuts everything from a `<` that opens a tag-like word, which would make the stored
         * note differ from what the user typed, so `<` is dropped up front.
         */
        public fun sanitizeNote(note: String): String = note.replace("<", "").replace(Regex("[\\r\\n]+"), " ").trim()

        private const val ORDER_ENDPOINT = "db/dbProcessOrder.jsp?"

        /** The operation without the per-page `time` and `token`, to tell whether two reads show the same offer. */
        private fun withoutSignature(path: String) = path.replace(Regex("(?<=[?&])(time|token)=[^&]*&?"), "")
        private val ORDERABLE_STATES = setOf(MealState.ORDERED, MealState.ORDERABLE, MealState.REORDERABLE)

        /**
         * Accepts what users type or paste: `jidelna.example.cz`, `https://jidelna.example.cz/`
         * or an installation under a sub-path such as `https://host/demo`.
         */
        public fun normalizeBaseUrl(input: String): HttpUrl {
            val trimmed = input.trim()
            require(trimmed.isNotEmpty()) { "Canteen address is empty" }
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val parsed = withScheme.toHttpUrl()
            require(parsed.scheme == "https") { "Only https addresses are supported" }
            val segments = parsed.pathSegments.filter { it.isNotEmpty() }
            val path = segments.takeWhile { it != "faces" && it != "web" }
                .dropLastWhile { it == "login" || it.endsWith(".jsp") }
            return parsed.newBuilder().encodedPath("/").query(null).fragment(null).apply {
                if (path.isNotEmpty()) {
                    path.forEach { addPathSegment(it) }
                    addPathSegment("")
                }
            }.build()
        }
    }
}

private class MemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { new ->
            store.removeAll { it.name == new.name && it.domain == new.domain && it.path == new.path }
            if (new.expiresAt > System.currentTimeMillis()) store += new
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
}
