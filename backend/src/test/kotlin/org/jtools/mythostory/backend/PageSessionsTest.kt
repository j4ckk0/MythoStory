package org.jtools.mythostory.backend

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PageSessionsTest {
    @Test
    fun `the last page closed, the server is told after the grace period`() = runTest {
        var told = 0
        val sessions = PageSessions(this, 10.seconds) { told++ }
        val first = sessions.opened()
        val second = sessions.opened()
        sessions.closed(first)
        advanceTimeBy(30.seconds)
        assertEquals(0, told) // one page still open
        sessions.closed(second)
        advanceTimeBy(9.seconds)
        assertEquals(0, told)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, told)
    }

    @Test
    fun `a page reopened within the grace period, as on a reload, keeps the server`() = runTest {
        var told = 0
        val sessions = PageSessions(this, 10.seconds) { told++ }
        sessions.closed(sessions.opened())
        advanceTimeBy(2.seconds)
        sessions.opened()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(0, told)
        assertEquals(1, sessions.count)
    }

    @Test
    fun `a page closed twice, by its beacon then by its stream breaking, counts once`() = runTest {
        var told = 0
        val sessions = PageSessions(this, 10.seconds) { told++ }
        val leaving = sessions.opened()
        sessions.opened()
        sessions.closed(leaving)
        sessions.closed(leaving)
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(0, told)
        assertEquals(1, sessions.count)
    }

    @Test
    fun `no page ever opened, the server waits`() = runTest {
        var told = 0
        PageSessions(this, 10.seconds) { told++ }
        advanceTimeBy(60.seconds)
        assertEquals(0, told)
    }
}
