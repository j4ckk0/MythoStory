package org.jtools.mythostory.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every URL of the catalog answers: run by scripts/check-links.sh (-Dlinks.check=true), never by the build, which
 * must not depend on the network. YouTube answers 200 for a video that does not exist: its oEmbed endpoint, which
 * answers only for real videos, is asked instead. Wikipedia's articles — thousands, since the Wikidata import — are
 * asked of Wikipedia's API, fifty to a request, rather than one by one.
 */
class LinkCheckTest {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    @Test
    fun `every link answers`() {
        assumeTrue(System.getProperty("links.check") == "true", "the links are checked by scripts/check-links.sh")
        val catalog = Catalog.load()
        val urls = (catalog.characters.flatMap { c -> c.links.map { it.url to "character ${c.id}" } } +
            catalog.stories.flatMap { s -> s.links.map { it.url to "story ${s.id}" } + s.sources.flatMap { it.url.values }.map { it to "story ${s.id}" } } +
            catalog.groups.flatMap { g -> g.links.map { it.url to "group ${g.id}" } + g.sources.flatMap { it.url.values }.map { it to "group ${g.id}" } })
            .groupBy({ it.first }, { it.second })

        val (articles, pages) = urls.keys.partition { WIKIPEDIA.matches(it) }
        val pool = Executors.newFixedThreadPool(8)
        val pageChecks = pages.map { url -> pool.submit<String?> { check(url)?.let { "$it: $url" } } }
        // one request at a time: Wikimedia's API refuses a client that sends many at once
        val articleFailures = articles.groupBy { WIKIPEDIA.find(it)!!.groupValues[1] }
            .flatMap { (language, ofLanguage) -> ofLanguage.chunked(50).flatMap { checkArticles(language, it) } }
        val failures = (pageChecks.mapNotNull { it.get() } + articleFailures)
            .map { failure -> failure + " (" + urls.getValue(failure.substringAfter(": ")).distinct().joinToString() + ")" }
        pool.shutdown()
        println("${urls.size} URLs checked, ${failures.size} failing")
        if (failures.isNotEmpty()) fail(failures.sorted().joinToString("\n", "failing links:\n"))
    }

    /** The articles of one Wikipedia that do not exist, as "missing: <url>", asking its API about them at once. */
    private fun checkArticles(language: String, articles: List<String>): List<String> {
        val titleOf = articles.associateBy { java.net.URLDecoder.decode(WIKIPEDIA.find(it)!!.groupValues[2], "UTF-8").replace('_', ' ') }
        val query = "https://$language.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1&titles=" +
            java.net.URLEncoder.encode(titleOf.keys.joinToString("|"), "UTF-8")
        var status = "no answer"
        repeat(4) { attempt ->
            try {
                val request = HttpRequest.newBuilder(URI(query)).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(60)).GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                status = "HTTP ${response.statusCode()}"
                if (response.statusCode() == 200) {
                    val result = kotlinx.serialization.json.Json.parseToJsonElement(response.body()).jsonObject["query"]!!.jsonObject
                    // the titles as Wikipedia normalised them, then as the redirects lead them, to the pages found
                    fun renames(key: String) = result[key]?.jsonArray.orEmpty()
                        .associate { it.jsonObject["from"]!!.jsonPrimitive.content to it.jsonObject["to"]!!.jsonPrimitive.content }
                    val normalized = renames("normalized")
                    val redirects = renames("redirects")
                    val missing = result["pages"]!!.jsonArray.map { it.jsonObject }
                        .filter { it["missing"] != null || it["invalid"] != null }
                        .map { it["title"]!!.jsonPrimitive.content }.toSet()
                    return titleOf.filter { (title, _) ->
                        val normal = normalized[title] ?: title
                        (redirects[normal] ?: normal) in missing
                    }.map { (_, url) -> "missing: $url" }
                }
            } catch (e: Exception) {
                status = e.javaClass.simpleName
            }
            Thread.sleep(3000L * (attempt + 1))
        }
        return articles.map { "$status: $it" }
    }

    /** null when the URL answers, the reason otherwise. */
    private fun check(url: String): String? {
        val asked = if (YOUTUBE.containsMatchIn(url)) "https://www.youtube.com/oembed?format=json&url=" + java.net.URLEncoder.encode(url, "UTF-8") else url
        repeat(3) { attempt ->
            try {
                val request = HttpRequest.newBuilder(URI(asked.substringBefore('#')))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(30))
                    .GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.discarding())
                val status = response.statusCode()
                if (status < 400) return null
                // Cloudflare's bot challenge (World History Encyclopedia…): the page exists, a script cannot tell more
                if (status == 403 && response.headers().firstValue("cf-mitigated").orElse("") == "challenge") {
                    println("unverifiable (bot challenge): $url")
                    return null
                }
                if (status != 429 && status < 500) return "HTTP $status"
            } catch (e: Exception) {
                if (attempt == 2) return e.javaClass.simpleName
            }
            Thread.sleep(2000L * (attempt + 1))
        }
        return "no answer"
    }

    private companion object {
        val YOUTUBE = Regex("^https://(www\\.)?(youtube\\.com|youtu\\.be)/")
        val WIKIPEDIA = Regex("^https://([a-z]+)\\.wikipedia\\.org/wiki/([^#?]+)$")
        /** Wikimedia's policy: a User-Agent naming the tool and how to reach its authors. */
        const val USER_AGENT = "MythoStory-link-check/0.1 (https://github.com/j4ckk0; j4ckk057@gmail.com)"
    }
}
