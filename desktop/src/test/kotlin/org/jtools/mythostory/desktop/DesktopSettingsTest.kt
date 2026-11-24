package org.jtools.mythostory.desktop

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

/** What several files say of the desktop program, and must say alike (the tests run from the root of the repository). */
class DesktopSettingsTest {
    @Test
    fun `the port is the same in the pom, mythostory ini, the program and the page's development proxy`() {
        val pom = Regex("<jtools.app.port>(\\d+)</jtools.app.port>").find(File("pom.xml").readText())!!.groupValues[1].toInt()
        val ini = Properties().apply { File("desktop/src/dist/mythostory.ini").reader().use { load(it) } }.getProperty("port").toInt()
        val proxy = Regex("\"target\":\\s*\"http://localhost:(\\d+)\"").find(File("frontend/proxy.conf.json").readText())!!.groupValues[1].toInt()
        assertEquals(DEFAULT_PORT, pom, "pom.xml's jtools.app.port")
        assertEquals(DEFAULT_PORT, ini, "mythostory.ini's port")
        assertEquals(DEFAULT_PORT, proxy, "frontend/proxy.conf.json's target")
    }
}
