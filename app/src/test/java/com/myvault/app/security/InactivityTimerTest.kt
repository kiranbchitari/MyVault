package com.myvault.app.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InactivityTimerTest {
    private val timeout = 60_000L

    @Test
    fun `fires once the timeout passes without interaction`() = runTest {
        val timer = InactivityTimer { currentTime }
        var fired = false
        launch { timer.awaitTimeout(timeout); fired = true }
        runCurrent()

        advanceTimeBy(timeout - 1)
        runCurrent()
        assertFalse(fired)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(fired)
    }

    @Test
    fun `interaction postpones the timeout`() = runTest {
        val timer = InactivityTimer { currentTime }
        var fired = false
        launch { timer.awaitTimeout(timeout); fired = true }
        runCurrent()

        repeat(5) {
            advanceTimeBy(timeout - 1_000)
            runCurrent()
            timer.touch()
        }
        assertFalse(fired)

        advanceTimeBy(timeout - 1)
        runCurrent()
        assertFalse(fired)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(fired)
    }

    @Test
    fun `waiting starts a fresh window`() = runTest {
        val timer = InactivityTimer { currentTime }
        advanceTimeBy(10 * timeout)   // long idle period before the vault is unlocked
        var fired = false
        launch { timer.awaitTimeout(timeout); fired = true }
        runCurrent()
        assertFalse(fired)
        advanceTimeBy(timeout)
        runCurrent()
        assertTrue(fired)
    }

    @Test
    fun `a cancelled wait never fires`() = runTest {
        val timer = InactivityTimer { currentTime }
        var fired = false
        val job = launch { timer.awaitTimeout(timeout); fired = true }
        runCurrent()
        job.cancel()
        advanceTimeBy(2 * timeout)
        runCurrent()
        assertFalse(fired)
    }
}
