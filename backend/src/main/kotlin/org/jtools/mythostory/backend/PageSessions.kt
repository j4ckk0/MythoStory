package org.jtools.mythostory.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration

/**
 * The pages open on the server, each holding its /api/session socket: when the last one closes and none opens again
 * within [grace] — a reload, a tab closed while another opens —, [onLastClosed] is called. Nothing happens before a
 * first page has opened: a server started without a browser waits as long as it takes.
 *
 * A page is closed either when it says so (a beacon as it goes away) or when its socket closes (as it goes away too,
 * or as its browser crashes; a hung page, once its pings go unanswered); whichever comes first counts, the other is
 * ignored.
 */
class PageSessions(
    private val scope: CoroutineScope,
    private val grace: Duration,
    private val onLastClosed: () -> Unit,
) {
    private val open = HashSet<String>()
    private var countdown: Job? = null

    /** How many pages are open (the tests ask). */
    val count: Int
        @Synchronized get() = open.size

    /**
     * A page opened: its session's id, which it closes with. Synchronized, as [closed]: the session sockets and the
     * beacons' requests come on the server's threads, at the same time.
     */
    @Synchronized
    fun opened(): String {
        val id = UUID.randomUUID().toString()
        open += id
        countdown?.cancel()
        countdown = null
        return id
    }

    /** A page closed, by its beacon or its socket: the second of the two is ignored; the last page closed starts the countdown. */
    @Synchronized
    fun closed(id: String) {
        if (!open.remove(id) || open.isNotEmpty()) return
        countdown = scope.launch {
            delay(grace)
            onLastClosed()
        }
    }
}
