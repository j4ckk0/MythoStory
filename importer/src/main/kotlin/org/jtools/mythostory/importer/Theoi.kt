package org.jtools.mythostory.importer

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors

/**
 * The Theoi Project's pages Wikidata names (P3545), checked: some ids are older than Theoi's reorganisation and lead
 * nowhere. Each page that answers gives its name, from its title: "CRONUS (Kronos) - Greek Titan God…" → "Cronus (Kronos)".
 */
class Theoi(private val client: HttpClient) {
    /** The page names, by Theoi id ("Titan/TitanKronos"), of the ids whose page answers. */
    fun names(ids: Collection<String>): Map<String, String> {
        val pool = Executors.newFixedThreadPool(8)
        try {
            return ids.distinct().map { id -> pool.submit<Pair<String, String>?> { name(id)?.let { id to it } } }
                .mapNotNull { it.get() }
                .toMap()
        } finally {
            pool.shutdown()
        }
    }

    private fun name(id: String): String? {
        val request = HttpRequest.newBuilder(URI(url(id))).header("User-Agent", Sparql.USER_AGENT).timeout(Duration.ofSeconds(30)).GET().build()
        repeat(3) { attempt ->
            try {
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) return null
                return title(response.body())
            } catch (e: Exception) {
                if (attempt == 2) return null
                Thread.sleep(2000)
            }
        }
        return null
    }

    companion object {
        fun url(id: String) = "https://www.theoi.com/$id.html"

        /** The name a Theoi page's title gives: before its " - " or " | ", the words in capitals capitalised. */
        fun title(page: String): String? {
            val title = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(page)?.groupValues?.get(1)
                ?.replace("&amp;", "&")?.trim()
                ?: return null
            val name = title.split(Regex("\\s*-\\s+|\\s+\\|\\s+"), limit = 2)[0].trim()
            return name.split(Regex("\\s+")).joinToString(" ") { word ->
                if (word.any { it.isLetter() } && word == word.uppercase()) word.lowercase().replaceFirstChar { it.titlecase() } else word
            }.ifBlank { null }
        }
    }
}
