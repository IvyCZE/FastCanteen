package yt.ivy.fastcanteen.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collects quick successive changes into one write: [save] runs with the latest submitted value once
 * [delayMs] have passed without a new one. Writes never overlap, and a change made during a write is
 * saved afterwards. [save] must handle its own errors.
 */
class DebouncedSaver<T : Any>(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val save: suspend (T) -> Unit,
) {
    private val lock = Mutex()
    private var timer: Job? = null
    private var pending: T? = null

    fun submit(value: T) {
        pending = value
        timer?.cancel()
        // The write runs in its own job so that a later submit cancels only the waiting, never a write in progress.
        timer = scope.launch {
            delay(delayMs)
            scope.launch { write() }
        }
    }

    /** Writes a pending value now and waits for any write in progress. */
    suspend fun flush() {
        timer?.cancel()
        write()
    }

    private suspend fun write() = lock.withLock {
        val value = pending ?: return@withLock
        pending = null
        save(value)
    }
}
