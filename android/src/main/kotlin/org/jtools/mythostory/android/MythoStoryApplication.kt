package org.jtools.mythostory.android

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.jtools.mythostory.backend.Platform
import org.jtools.mythostory.backend.startServer
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

/**
 * Starts the server once per process, whatever becomes of the activities: on 127.0.0.1 and a free port.
 */
class MythoStoryApplication : Application() {
    /** The port the server listens on, once it does; the activity waits for it. */
    val port: CompletableFuture<Int> = CompletableFuture()

    override fun onCreate() {
        super.onCreate()
        // off the main thread: Android forbids network work there
        thread(name = "mythostory-server", isDaemon = true) {
            try {
                val server = startServer("127.0.0.1", 0, Platform.ANDROID)
                port.complete(runBlocking { server.engine.resolvedConnectors().first().port })
            } catch (e: Throwable) {
                port.completeExceptionally(e)
            }
        }
    }
}
