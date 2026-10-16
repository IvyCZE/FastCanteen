package yt.ivy.librecanteen.core.icanteen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import yt.ivy.librecanteen.core.CanteenException
import yt.ivy.librecanteen.core.CanteenProvider
import yt.ivy.librecanteen.core.model.AccountStatus
import yt.ivy.librecanteen.core.model.Credentials
import yt.ivy.librecanteen.core.model.DayMenu
import java.io.IOException
import java.time.LocalDate

/**
 * Scrapes an iCanteen installation. Everything is read from server-rendered HTML,
 * so unknown markup is reported as [CanteenException.UnsupportedLayout] instead of being guessed.
 */
public class ICanteenClient(
    private val baseUrl: HttpUrl,
    httpClient: OkHttpClient = OkHttpClient(),
) : CanteenProvider {

    private val http = httpClient.newBuilder().cookieJar(MemoryCookieJar()).build()

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
        val csrf = LoginPageParser.csrfToken(page)
        val form = FormBody.Builder().add("_csrf", csrf).build()
        call(Request.Builder().url(url("logout")).post(form).build()) { }
    }

    override suspend fun status(): AccountStatus =
        StatusParser.parse(fetch(url("faces/secured/main.jsp")))

    override suspend fun menu(day: LocalDate): DayMenu {
        val url = url("faces/secured/db/dbJidelnicekOnDayView.jsp") {
            addQueryParameter("day", day.toString())
            addQueryParameter("terminal", "false")
            addQueryParameter("rating", "")
            addQueryParameter("printer", "false")
            addQueryParameter("keyboard", "false")
        }
        return DayMenuParser.parse(day, fetch(url))
    }

    private fun url(path: String, configure: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        baseUrl.newBuilder().addPathSegments(path).apply(configure).build()

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
