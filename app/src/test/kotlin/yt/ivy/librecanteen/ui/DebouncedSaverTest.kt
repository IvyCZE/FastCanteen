package yt.ivy.librecanteen.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import yt.ivy.librecanteen.ui.settings.DebouncedSaver
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedSaverTest {
    @Test
    fun `quick changes become one save with the latest value`() = runTest {
        val saved = mutableListOf<Int>()
        val saver = DebouncedSaver<Int>(this, 1000) { saved += it }

        saver.submit(1)
        advanceTimeBy(600)
        saver.submit(2)
        advanceTimeBy(600)
        saver.submit(3)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(emptyList(), saved)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(3), saved)
    }

    @Test
    fun `a change during a save is saved afterwards without interrupting it`() = runTest {
        val saved = mutableListOf<Int>()
        var running = 0
        var overlap = false
        val saver = DebouncedSaver<Int>(this, 1000) {
            running++
            if (running > 1) overlap = true
            delay(5000)
            saved += it
            running--
        }

        saver.submit(1)
        advanceTimeBy(1500) // the save of 1 is under way
        saver.submit(2)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), saved)
        assertEquals(false, overlap)
    }

    @Test
    fun `flush saves a pending value immediately and does nothing when nothing is pending`() = runTest {
        val saved = mutableListOf<Int>()
        val saver = DebouncedSaver<Int>(this, 1000) { saved += it }

        saver.flush()
        assertEquals(emptyList(), saved)

        saver.submit(7)
        saver.flush()
        assertEquals(listOf(7), saved)

        advanceUntilIdle()
        assertEquals(listOf(7), saved)
    }
}
