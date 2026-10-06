package org.jtools.mythostory.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything MythoStory knows: the characters, the stories and the cycles, read from the data files the core jar
 * carries (src/main/resources/data, listed by data/index.json) — the same on the desktop and on Android.
 */
@Serializable
data class Catalog(
    val characters: List<Character>,
    val stories: List<Story>,
    val cycles: List<Cycle>,
    val eras: List<Era>,
    val places: List<Place>,
) {
    /** What is wrong with the catalog, one sentence per fault; empty when it is sound. */
    fun problems(): List<String> = buildList {
        val characterIds = characters.map { it.id }.toSet()
        val storyIds = stories.map { it.id }.toSet()
        val eraIds = eras.map { it.id }.toSet()
        val placeIds = places.map { it.id }.toSet()
        duplicates(characters.map { it.id }).forEach { add("character $it is defined twice") }
        duplicates(stories.map { it.id }).forEach { add("story $it is defined twice") }
        duplicates(cycles.map { it.id }).forEach { add("cycle $it is defined twice") }
        duplicates(eras.map { it.id }).forEach { add("era $it is defined twice") }
        duplicates(places.map { it.id }).forEach { add("place $it is defined twice") }
        duplicates(characters.mapNotNull { it.wikidata }).forEach { add("Wikidata item $it is two characters") }
        characters.mapNotNull { it.wikidata }.filterNot { WIKIDATA.matches(it) }.forEach { add("$it is not a Wikidata item (Q and digits)") }
        (characters.map { it.id } + stories.map { it.id } + cycles.map { it.id } + eras.map { it.id } + places.map { it.id })
            .filterNot { SLUG.matches(it) }.forEach { add("$it is not a slug (a-z, 0-9 and dashes)") }

        for (c in characters) {
            checkI18n("character ${c.id}: its name", c.name)
            checkI18n("character ${c.id}: its description", c.description)
            c.roman?.let { checkI18n("character ${c.id}: its Roman name", it) }
            c.parents.filterNot { it in characterIds }.forEach { add("character ${c.id}: no parent $it") }
            if (c.id in c.parents) add("character ${c.id} is its own parent")
            c.links.forEach { checkLink("character ${c.id}", it) }
        }
        for (s in stories) {
            checkI18n("story ${s.id}: its title", s.title)
            checkI18n("story ${s.id}: its summary", s.summary)
            if (s.sources.isEmpty()) add("story ${s.id} has no source")
            if (s.characters.isEmpty()) add("story ${s.id} involves no character")
            s.characters.filterNot { it in characterIds }.forEach { add("story ${s.id}: no character $it") }
            s.related.filterNot { it in storyIds }.forEach { add("story ${s.id}: no related story $it") }
            for (source in s.sources) {
                checkI18n("story ${s.id}: a source's author", source.author)
                checkI18n("story ${s.id}: a source's work", source.work)
                source.url.forEach { (lang, url) ->
                    if (lang !in I18n.LANGUAGES) add("story ${s.id}: source URL $url in $lang, which is not an i18n language")
                    if (!url.startsWith("https://") && !url.startsWith("http://")) add("story ${s.id}: source URL $url is not http(s)")
                }
            }
            s.links.forEach { checkLink("story ${s.id}", it) }
            if (s.era !in eraIds) add("story ${s.id}: no era ${s.era}")
            s.generation?.let { if (it !in 0..20) add("story ${s.id}: generation $it is not between 0 and 20 before the Trojan War") }
            s.dating?.let { checkI18n("story ${s.id}: its dating", it) }
            s.places.filterNot { it in placeIds }.forEach { add("story ${s.id}: no place $it") }
            if (s.route && s.places.size < 2) add("story ${s.id}: a route needs two places at least")
        }
        for (e in eras) {
            checkI18n("era ${e.id}: its title", e.title)
            checkI18n("era ${e.id}: its description", e.description)
            e.dating?.let { checkI18n("era ${e.id}: its dating", it) }
        }
        val placesUsed = stories.flatMap { it.places }.toSet()
        for (p in places) {
            checkI18n("place ${p.id}: its name", p.name)
            p.identification?.let { checkI18n("place ${p.id}: its identification", it) }
            if (p.lat !in -90.0..90.0 || p.lon !in -180.0..180.0) add("place ${p.id}: ${p.lat}, ${p.lon} is not on Earth")
            if (!WIKIDATA.matches(p.wikidata)) add("place ${p.id}: ${p.wikidata} is not a Wikidata item")
            if (p.id !in placesUsed) add("place ${p.id} is in no story")
        }
        for (c in cycles) {
            checkI18n("cycle ${c.id}: its title", c.title)
            checkI18n("cycle ${c.id}: its description", c.description)
            c.stories.filterNot { it in storyIds }.forEach { add("cycle ${c.id}: no story $it") }
            duplicates(c.stories).forEach { add("cycle ${c.id} lists $it twice") }
        }
    }

    /** A text in every i18n language, none blank. */
    private fun MutableList<String>.checkI18n(what: String, text: I18nText) {
        I18n.LANGUAGES.filter { text[it].isNullOrBlank() }.forEach { add("$what lacks its \"$it\" i18n text") }
        (text.keys - I18n.LANGUAGES.toSet()).forEach { add("$what is in \"$it\", which is not an i18n language") }
    }

    /** [IllegalStateException] naming every fault, when there are some. */
    fun requireSound() {
        val problems = problems()
        check(problems.isEmpty()) { "the catalog is not sound:\n  " + problems.joinToString("\n  ") }
    }

    private fun MutableList<String>.checkLink(owner: String, link: Link) {
        if (link.lang !in I18n.LANGUAGES) add("$owner: link ${link.url} is in \"${link.lang}\", which is not an i18n language")
        if (!link.url.startsWith("https://") && !link.url.startsWith("http://")) add("$owner: link ${link.url} is not http(s)")
        if (link.title.isBlank()) add("$owner: link ${link.url} has no title")
    }

    companion object {
        private val SLUG = Regex("[a-z0-9]+(-[a-z0-9]+)*")
        private val WIKIDATA = Regex("Q[1-9][0-9]*")
        private val json = Json { ignoreUnknownKeys = false }

        private fun duplicates(ids: List<String>) = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        /** The catalog the core jar carries; [IllegalStateException] naming every fault when it is not sound. */
        fun load(): Catalog = read(::resource).also { it.requireSound() }

        /**
         * The catalog of data files read by [file] — by their path relative to data/, index.json first —, unchecked:
         * the classpath's for [load], a directory's for the importer, which checks it with what it adds.
         */
        fun read(file: (String) -> String): Catalog {
            val index = json.decodeFromString<Index>(file("index.json"))
            return Catalog(
                characters = index.characters.flatMap { json.decodeFromString<List<Character>>(file(it)) },
                stories = index.stories.flatMap { json.decodeFromString<List<Story>>(file(it)) },
                cycles = json.decodeFromString(file(index.cycles)),
                eras = json.decodeFromString(file(index.eras)),
                places = json.decodeFromString(file(index.places)),
            )
        }

        private fun resource(path: String): String =
            (Catalog::class.java.classLoader.getResourceAsStream("data/$path") ?: error("no data/$path in the classpath"))
                .use { it.readBytes().decodeToString() }
    }

    /** data/index.json: the data files, relative to data/. */
    @Serializable
    data class Index(val characters: List<String>, val stories: List<String>, val cycles: String, val eras: String, val places: String)
}
