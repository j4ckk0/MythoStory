package org.jtools.mythostory.importer

import java.text.Normalizer

/** The texts the importer writes: ids, descriptions, names. */
object Texts {
    /** "Héraclès" → "heracles", "Leon of Nemea" → "leon-of-nemea"; "" when nothing of it is a letter or a digit. */
    fun slug(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace("œ", "oe").replace("æ", "ae").replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    /** A Greek label without the transliteration Wikidata sometimes appends: "Πηνελόπη , Pēnelopē" → "Πηνελόπη". */
    fun greek(label: String): String = label.split(Regex("\\s*[,|]\\s*"))[0].trim()

    /** A Wikidata description as a sentence: "daughter of Danaus" → "Daughter of Danaus." */
    fun sentence(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val capitalized = trimmed.replaceFirstChar { it.titlecase() }
        return if (capitalized.last() in ".!?") capitalized else "$capitalized."
    }

    /**
     * A description that says no more than "a figure of Greek mythology", which a description made from the parents
     * beats: "Greek mythological figure", "personnage de la mythologie grecque"…
     */
    fun isGeneric(description: String): Boolean {
        val d = slug(description)
        return d.isEmpty() || GENERIC.any { it.matches(d) }
    }

    private val GENERIC = listOf(
        Regex("(a-|an-|the-)?(greek-)?(mythological|mythical|legendary)?-?(greek-)?(figure|character|personage|person|being|mortal|hero|heroine|woman|man|king|queen|princess|prince|deity|god|goddess|nymph)?-?((in|of|from)-(the-)?)?(ancient-)?(greek-)?(myth|mythology|mythologies|legend|legends)?(-(figure|character|personage))?"),
        Regex("(un-|une-)?(personnage|figure|heros|heroine|divinite|dieu|deesse|nymphe|roi|reine|princesse|prince)?-?((de-la|des|du|dans-la)-)?(mythologie|legende|mythe)s?-grecques?"),
    )

    /** "Son of Zeus and Leda in Greek mythology." / "Fils de Zeus et de Léda dans la mythologie grecque." — from the gender ("male", "female" or null) and the parents' names. */
    fun childOf(gender: String?, parents: List<String>, language: String): String = when (language) {
        "fr" -> {
            val child = when (gender) { "male" -> "Fils"; "female" -> "Fille"; else -> "Enfant" }
            "$child ${list(parents.map(::of), "et")} dans la mythologie grecque."
        }
        else -> {
            val child = when (gender) { "male" -> "Son"; "female" -> "Daughter"; else -> "Child" }
            "$child of ${list(parents, "and")} in Greek mythology."
        }
    }

    /** "A, B and C" */
    private fun list(items: List<String>, and: String): String =
        if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " $and " + items.last()

    /** "Figure of Greek mythology." in each language, when nothing better is known. */
    fun figure(language: String): String = when (language) {
        "fr" -> "Personnage de la mythologie grecque."
        else -> "Figure of Greek mythology."
    }

    /** French "de", elided before a vowel or a mute h: "d’Héra", "d’Apollon", "de Zeus". */
    private fun of(name: String): String {
        val first = Normalizer.normalize(name.take(1), Normalizer.Form.NFD).firstOrNull()?.lowercaseChar()
        return if (first != null && first in "aeiouyh") "d’$name" else "de $name"
    }
}
