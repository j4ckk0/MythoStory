package org.jtools.mythostory.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.jtools.mythostory.core.Catalog
import org.jtools.mythostory.core.Character
import org.jtools.mythostory.core.CharacterKind
import org.jtools.mythostory.core.Link
import org.jtools.mythostory.core.LinkKind
import java.io.File
import java.net.URLEncoder
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Imports the characters of Greek mythology from Wikidata — every instance of "mythological Greek character"
 * (Q22988604) and its subclasses — into data/characters/wikidata/<kind>.json, which it rewrites whole, and lists
 * those files in data/index.json. The curated characters (the other files) are never touched: a Wikidata item one
 * of them already is is not imported.
 *
 * Each imported character gets its names in English and French (from Wikidata's labels, one language standing in for
 * the other when it has none), its Greek name, its kind (Kinds), its parents among the catalog's characters, a
 * description (Wikidata's, or one made from its parents when Wikidata's says no more than "Greek mythological
 * figure"), and links: Wikipedia in both languages when the articles exist, and the Theoi Project's page when it
 * answers. The catalog, with what was imported, must be sound (Catalog.problems) before anything is written.
 *
 *   mythostory-importer [--all] [--data DIR]
 *     --all       also the characters without a Wikipedia article in English or French (names, little else)
 *     --data DIR  the data directory (default core/src/main/resources/data)
 */
fun main(args: Array<String>) {
    var all = false
    var data = File("core/src/main/resources/data")
    val rest = args.iterator()
    while (rest.hasNext()) {
        when (val arg = rest.next()) {
            "--all" -> all = true
            "--data" -> data = File(if (rest.hasNext()) rest.next() else usage("--data takes a directory"))
            else -> usage("unknown argument: $arg")
        }
    }
    require(data.resolve("index.json").isFile) { "no ${data.resolve("index.json")}: run from the project root, or give --data" }
    WikidataImport(data, all).run()
}

private fun usage(message: String): Nothing {
    System.err.println("$message\nUsage: mythostory-importer [--all] [--data DIR]")
    exitProcess(2)
}

class WikidataImport(private val data: File, private val all: Boolean) {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()
    private val sparql = Sparql(client)
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  "; encodeDefaults = false; explicitNulls = false }

    /** One Wikidata item, as the queries describe it. */
    private class Item(val qid: String, val row: Map<String, String>) {
        val parents = mutableListOf<String>()
        val roots = mutableSetOf<String>()
        operator fun get(name: String): String? = row[name]?.takeIf { it.isNotBlank() }
    }

    fun run() {
        val catalog = Catalog.read { data.resolve(it).readText() }
        val curated = curatedFiles().flatMap { json.decodeFromString<List<Character>>(data.resolve(it).readText()) }
        val curatedByQid = curated.filter { it.wikidata != null }.associateBy { it.wikidata!! }
        val curatedWikipedia = curated.flatMap { c -> c.links.filter { it.kind == LinkKind.WIKIPEDIA }.map { it.url } }.toSet()

        println("== asking Wikidata for the characters of Greek mythology${if (all) "" else " with a Wikipedia article"}")
        val items = sparql.select(itemsQuery()).groupBy { it["item"]!! }.map { (qid, rows) -> Item(qid, rows.first()) }.associateBy { it.qid }
        sparql.select(parentsQuery()).forEach { row -> items[row["item"]]?.parents?.add(row["parent"]!!) }
        sparql.select(rootsQuery()).forEach { row -> items[row["item"]]?.roots?.add(row["root"]!!) }
        println("== ${items.size} characters on Wikidata, ${curatedByQid.size} of them curated already")

        // the curated ones stay as they are: by their Wikidata item, or by their Wikipedia article for a curated
        // character without one
        val imported = items.values.filter { it.qid !in curatedByQid && wikipedia(it, "en") !in curatedWikipedia }
            .sortedBy { it.qid.drop(1).toLong() }

        // the kinds: by classes first, then by parents, curated parents' kinds included, until nothing changes
        val kinds = HashMap<String, CharacterKind>()
        curatedByQid.forEach { (qid, c) -> kinds[qid] = c.kind }
        imported.forEach { item -> Kinds.byClasses(item.roots)?.let { kinds[item.qid] = it } }
        repeat(3) { imported.filter { it.roots.isEmpty() }.forEach { kinds[it.qid] = Kinds.byParents(it.parents.map(kinds::get)) } }

        val names = imported.associate { it.qid to names(it) }
        val ids = ids(imported, names, curated.map { it.id }.toSet())
        val idByQid = ids + curatedByQid.mapValues { it.value.id }
        val nameByQid = names + curatedByQid.mapValues { it.value.name }

        println("== checking the Theoi Project's pages Wikidata names")
        val theoi = Theoi(client).names(imported.mapNotNull { it["theoi"] })
        println("== ${theoi.size} of ${imported.count { it["theoi"] != null }} Theoi pages answer")

        val characters = imported.map { item ->
            val parents = item.parents.distinct().filter { it in idByQid && it != item.qid }
            Character(
                id = ids.getValue(item.qid),
                name = names.getValue(item.qid),
                kind = kinds.getValue(item.qid),
                wikidata = item.qid,
                description = mapOf(
                    "en" to description(item, "en", parents.map { nameByQid.getValue(it).getValue("en") }),
                    "fr" to description(item, "fr", parents.map { nameByQid.getValue(it).getValue("fr") }),
                ),
                greek = item["grc"] ?: item["el"],
                parents = parents.map(idByQid::getValue),
                links = listOfNotNull(
                    wikipedia(item, "en")?.let { Link(LinkKind.WIKIPEDIA, "en", "Wikipedia — ${item["enwiki"]}", it) },
                    wikipedia(item, "fr")?.let { Link(LinkKind.WIKIPEDIA, "fr", "Wikipédia — ${item["frwiki"]}", it) },
                    item["theoi"]?.let { id -> theoi[id]?.let { Link(LinkKind.ARTICLE, "en", "Theoi Project — $it", Theoi.url(id)) } },
                ),
            )
        }
        write(catalog, curated, characters)
    }

    private fun index() = json.decodeFromString<Catalog.Index>(data.resolve("index.json").readText())

    /** The curated characters' files: every one but the previous import's, which this one replaces. */
    private fun curatedFiles() = index().characters.filterNot { it.startsWith(IMPORTED) }

    private fun names(item: Item): Map<String, String> {
        val en = item["en"] ?: item["mul"] ?: item["fr"] ?: item["enwiki"] ?: item["frwiki"] ?: item.qid
        val fr = item["fr"] ?: item["mul"] ?: en
        return mapOf("en" to en, "fr" to fr)
    }

    /** Wikidata's description when it says more than "a figure of Greek mythology", else one made from the parents. */
    private fun description(item: Item, language: String, parents: List<String>): String {
        val wikidata = item["${language}Desc"]
        if (wikidata != null && !Texts.isGeneric(wikidata)) return Texts.sentence(wikidata)
        if (parents.isNotEmpty()) return Texts.childOf(GENDERS[item["sex"]], parents, language)
        return Texts.figure(language)
    }

    private fun wikipedia(item: Item, language: String): String? =
        item["${language}wiki"]?.let { title ->
            // as Wikipedia writes them: (), commas and apostrophes left readable
            "https://$language.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), Charsets.UTF_8)
                .replace("%28", "(").replace("%29", ")").replace("%2C", ",").replace("%27", "'")
        }

    /** The ids: the English name as a slug, followed by the Wikidata item when several characters would share it. */
    private fun ids(items: List<Item>, names: Map<String, Map<String, String>>, taken: Set<String>): Map<String, String> {
        val slugs = items.associate { it.qid to Texts.slug(names.getValue(it.qid).getValue("en")) }
        val counts = slugs.values.groupingBy { it }.eachCount()
        return slugs.mapValues { (qid, slug) ->
            if (slug.isEmpty()) qid.lowercase()
            else if (slug in taken || counts.getValue(slug) > 1) "$slug-${qid.lowercase()}"
            else slug
        }
    }

    /** Checks the catalog with the imported characters in, then writes them, one file per kind, and the index. */
    private fun write(catalog: Catalog, curated: List<Character>, imported: List<Character>) {
        Catalog(curated + imported, catalog.stories, catalog.cycles).requireSound()

        data.resolve(IMPORTED).deleteRecursively()
        data.resolve(IMPORTED).mkdirs()
        val files = CharacterKind.entries.mapNotNull { kind ->
            val ofKind = imported.filter { it.kind == kind }.sortedBy { it.id }
            if (ofKind.isEmpty()) return@mapNotNull null
            val file = "$IMPORTED${kind.name.lowercase()}.json"
            data.resolve(file).writeText(json.encodeToString(ofKind) + "\n")
            println("== $file: ${ofKind.size} characters")
            file
        }
        data.resolve("index.json").writeText(json.encodeToString(index().copy(characters = curatedFiles() + files)) + "\n")
        println("== ${imported.size} characters imported, ${imported.sumOf { it.links.size }} links")
    }

    private fun itemsQuery() = """
        SELECT ?item ?en ?fr ?mul ?grc ?el ?enDesc ?frDesc ?enwiki ?frwiki ?sex ?theoi WHERE {
          ?item wdt:P31/wdt:P279* wd:Q22988604 .
          OPTIONAL { ?item rdfs:label ?en FILTER(LANG(?en) = "en") }
          OPTIONAL { ?item rdfs:label ?fr FILTER(LANG(?fr) = "fr") }
          OPTIONAL { ?item rdfs:label ?mul FILTER(LANG(?mul) = "mul") }
          OPTIONAL { ?item rdfs:label ?grc FILTER(LANG(?grc) = "grc") }
          OPTIONAL { ?item rdfs:label ?el FILTER(LANG(?el) = "el") }
          OPTIONAL { ?item schema:description ?enDesc FILTER(LANG(?enDesc) = "en") }
          OPTIONAL { ?item schema:description ?frDesc FILTER(LANG(?frDesc) = "fr") }
          OPTIONAL { ?enArticle schema:about ?item ; schema:isPartOf <https://en.wikipedia.org/> ; schema:name ?enwiki }
          OPTIONAL { ?frArticle schema:about ?item ; schema:isPartOf <https://fr.wikipedia.org/> ; schema:name ?frwiki }
          OPTIONAL { ?item wdt:P21 ?sex }
          OPTIONAL { ?item wdt:P3545 ?theoi }
          ${if (all) "" else "FILTER(BOUND(?enwiki) || BOUND(?frwiki))"}
        }
    """.trimIndent()

    /** Father (P22) and mother (P25). */
    private fun parentsQuery() = """
        SELECT DISTINCT ?item ?parent WHERE {
          ?item wdt:P31/wdt:P279* wd:Q22988604 .
          { ?item wdt:P22 ?parent } UNION { ?item wdt:P25 ?parent }
        }
    """.trimIndent()

    /** The classes of Kinds.RULES each character is an instance of, through subclasses. */
    private fun rootsQuery() = """
        SELECT DISTINCT ?item ?root WHERE {
          VALUES ?root { ${Kinds.RULES.joinToString(" ") { "wd:${it.first}" }} }
          ?item wdt:P31/wdt:P279* wd:Q22988604 ; wdt:P31/wdt:P279* ?root .
        }
    """.trimIndent()

    private companion object {
        /** The imported characters' files, under data/: never edited by hand, the next import rewrites them. */
        const val IMPORTED = "characters/wikidata/"
        val GENDERS = mapOf("Q6581097" to "male", "Q6581072" to "female")
    }
}
