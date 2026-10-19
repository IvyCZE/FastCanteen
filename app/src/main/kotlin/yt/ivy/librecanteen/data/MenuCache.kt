package yt.ivy.librecanteen.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import yt.ivy.librecanteen.core.model.DayMenu
import java.io.File
import java.time.LocalDate

/** Last known menus, so the week can be browsed without a connection. */
interface MenuCache {
    fun get(day: LocalDate): DayMenu?
    fun put(menu: DayMenu)
    fun clear()
}

object NoMenuCache : MenuCache {
    override fun get(day: LocalDate): DayMenu? = null
    override fun put(menu: DayMenu) = Unit
    override fun clear() = Unit
}

/**
 * Stores menus in one JSON file. The server-signed operations are dropped on the way in:
 * they are tied to a past session and must never be replayed from disk.
 */
class FileMenuCache(
    private val file: File,
    private val today: () -> LocalDate = LocalDate::now,
) : MenuCache {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(DayMenu.serializer())
    private var menus: MutableMap<LocalDate, DayMenu>? = null

    @Synchronized
    override fun get(day: LocalDate): DayMenu? = load()[day]

    @Synchronized
    override fun put(menu: DayMenu) {
        val all = load()
        all[menu.date] = menu.withoutOperations()
        val oldest = today().minusDays(RETENTION_DAYS)
        all.keys.removeAll { it.isBefore(oldest) }
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(json.encodeToString(serializer, all.values.toList()))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    @Synchronized
    override fun clear() {
        menus = mutableMapOf()
        file.delete()
    }

    private fun load(): MutableMap<LocalDate, DayMenu> = menus ?: run {
        val read = runCatching {
            json.decodeFromString(serializer, file.readText()).associateBy { it.date }.toMutableMap()
        }.getOrElse { mutableMapOf() }
        menus = read
        read
    }

    private fun DayMenu.withoutOperations() =
        copy(meals = meals.map { it.copy(action = null, noteAction = null, exchangePut = null, exchangeWithdraw = null) })

    private companion object {
        const val RETENTION_DAYS = 21L
    }
}
