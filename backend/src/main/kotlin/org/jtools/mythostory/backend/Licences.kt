package org.jtools.mythostory.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jtools.mythostory.core.I18nText

/** A piece of software or data MythoStory carries, and its licence (an SPDX identifier when it has one). */
@Serializable
data class Component(
    val name: String,
    val version: String,
    val licence: String,
    val url: String? = null,
    /** What it does in MythoStory: for the embedded components, which the help page explains. */
    val role: I18nText? = null,
    /** Its Maven coordinates, for a library. */
    val artifact: String? = null,
)

/**
 * The licences the help page lists (frontend: help page): the embedded components — the JRE, Piper and its voices,
 * a curated list, licences/embedded.json, which the desktop carries — and the libraries the server's jar carries,
 * licences/THIRD-PARTY.txt, which Maven's license plugin writes from their poms at every build. On Android, neither:
 * the app carries no JRE nor Piper, and its libraries are packaged by Gradle.
 */
@Serializable
data class Licences(val embedded: List<Component>, val libraries: List<Component>) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): Licences = Licences(
            embedded = resource("licences/embedded.json")?.let { json.decodeFromString<List<Component>>(it) }.orEmpty(),
            libraries = resource("licences/THIRD-PARTY.txt")?.let(::libraries).orEmpty(),
        )

        /** The license plugin's lines: "     (Licence) (Other licence) Name (group:artifact:version - url)". */
        fun libraries(thirdParty: String): List<Component> =
            thirdParty.lines().mapNotNull { line ->
                val match = LINE.matchEntire(line.trim()) ?: return@mapNotNull null
                val (licences, name, group, artifact, version, url) = match.destructured
                Component(
                    name = name,
                    version = version,
                    licence = Regex("\\((.+?)\\)").findAll(licences).map { spdx(it.groupValues[1]) }.distinct().joinToString(" OR "),
                    url = url.takeIf { it.startsWith("http") },
                    artifact = "$group:$artifact",
                )
            }

        /** The names poms give a licence, as SPDX identifiers where the name is a well-known one. */
        fun spdx(name: String): String = when {
            Regex("(?i)apache.*2").containsMatchIn(name) -> "Apache-2.0"
            Regex("(?i)^(the )?mit( license)?$").matches(name) -> "MIT"
            Regex("(?i)eclipse public license.*2").containsMatchIn(name) -> "EPL-2.0"
            else -> name
        }

        private val LINE = Regex("""((?:\([^)]+\)\s*)+)(.+?) \(([^:\s]+):([^:\s]+):(\S+) - (.*)\)""")

        private fun resource(path: String): String? =
            Licences::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
    }
}
