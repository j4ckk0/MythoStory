package org.jtools.mythostory.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The Wikidata Query Service: a SPARQL query's rows, each variable's value by name, entities as their id ("Q34201"). */
class Sparql(private val client: HttpClient) {
    fun select(query: String): List<Map<String, String>> {
        val request = HttpRequest.newBuilder(URI(ENDPOINT))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/sparql-results+json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofMinutes(2))
            .POST(HttpRequest.BodyPublishers.ofString("query=" + URLEncoder.encode(query, Charsets.UTF_8)))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Wikidata answered ${response.statusCode()}: ${response.body().take(500)}" }
        return Json.parseToJsonElement(response.body()).jsonObject["results"]!!.jsonObject["bindings"]!!.jsonArray.map { row ->
            row.jsonObject.mapValues { (_, value) -> value.jsonObject["value"]!!.jsonPrimitive.content.removePrefix(ENTITY) }
        }
    }

    companion object {
        private const val ENDPOINT = "https://query.wikidata.org/sparql"
        private const val ENTITY = "http://www.wikidata.org/entity/"
        /** Wikimedia's policy: a User-Agent naming the tool and how to reach its authors. */
        const val USER_AGENT = "MythoStory-importer/0.1 (https://github.com/j4ckk0; j4ckk057@gmail.com)"
    }
}
