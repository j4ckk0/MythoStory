package org.jtools.mythostory.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The embedded components the help page lists (licences/embedded.json) are those externals.conf deploys: a JRE, Piper
 * or a voice changed there, the list must follow, and this test says so.
 */
class EmbeddedLicencesTest {
    private val conf = File("../externals.conf").readLines().filter { "=" in it && !it.startsWith("#") }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    private val embedded = Json.parseToJsonElement(File("src/main/resources/licences/embedded.json").readText()).jsonArray
        .associate { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["version"]!!.jsonPrimitive.content }

    private fun check(component: String, externals: List<String>, mark: (String) -> String) {
        val version = embedded[component] ?: error("licences/embedded.json lists no $component")
        externals.forEach { key ->
            val url = conf.getValue(key)
            assertTrue(mark(version) in url, "$component $version in licences/embedded.json, but externals.conf's $key is $url")
        }
    }

    @Test
    fun `the JRE, Piper and the voices are those externals_conf deploys`() {
        check("Eclipse Temurin JRE (OpenJDK)", listOf("JRE_LINUX", "JRE_WINDOWS")) { "jdk-$it+" }
        check("Piper", listOf("PIPER_LINUX", "PIPER_WINDOWS")) { "/$it/" }
        val voices = embedded.keys.filter { it.startsWith("Piper voice ") }.map { it.removePrefix("Piper voice ") }
        assertTrue(voices.any { it in conf.getValue("VOICE_FR") }, "the French voice of externals.conf (${conf["VOICE_FR"]}) is not in licences/embedded.json: $voices")
        assertTrue(voices.any { it in conf.getValue("VOICE_EN") }, "the English voice of externals.conf (${conf["VOICE_EN"]}) is not in licences/embedded.json: $voices")
    }
}
