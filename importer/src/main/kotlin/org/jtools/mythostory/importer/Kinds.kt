package org.jtools.mythostory.importer

import org.jtools.mythostory.core.CharacterKind

/**
 * What kind a Wikidata character is: by the classes it is an instance of — the first of [RULES] it reaches through
 * "subclass of" wins —, else by its parents — a god's child by a mortal is a demigod —, else a mortal.
 */
object Kinds {
    /** Wikidata's classes for each kind, most specific first: a nymph is a deity too, a deity a mythical creature. */
    val RULES: List<Pair<String, CharacterKind>> = listOf(
        "Q878099" to CharacterKind.PRIMORDIAL, // Greek primordial deity
        "Q45713" to CharacterKind.TITAN, // titan
        "Q113103481" to CharacterKind.OLYMPIAN, // Olympian god
        "Q3346693" to CharacterKind.NYMPH, // Greek nymph (naiads, oceanids, dryads…)
        "Q83203" to CharacterKind.CREATURE, // centaur
        "Q191192" to CharacterKind.CREATURE, // Giants
        "Q128430" to CharacterKind.CREATURE, // cyclops
        "Q175255" to CharacterKind.CREATURE, // Hecatoncheires
        "Q163709" to CharacterKind.CREATURE, // satyr
        "Q1134605" to CharacterKind.CREATURE, // Telchines
        "Q9335858" to CharacterKind.CREATURE, // sphinx
        "Q24334299" to CharacterKind.CREATURE, // mythical animal
        "Q22989102" to CharacterKind.DEITY, // Greek deity
        "Q178885" to CharacterKind.DEITY, // deity
        "Q1157625" to CharacterKind.DEITY, // daemon
        "Q23015925" to CharacterKind.HERO, // demigod of Greek mythology
        "Q466470" to CharacterKind.HERO, // demigod
        "Q12502038" to CharacterKind.HERO, // Greek heroes
        "Q1347516" to CharacterKind.HERO, // eponymous hero
        "Q2239243" to CharacterKind.CREATURE, // mythical creature: last, the broadest
    )

    private val GODS = setOf(CharacterKind.PRIMORDIAL, CharacterKind.TITAN, CharacterKind.OLYMPIAN, CharacterKind.DEITY, CharacterKind.NYMPH)

    /** The kind its classes say, or null when they say nothing: [RULES]' classes it is an instance of, through subclasses. */
    fun byClasses(roots: Set<String>): CharacterKind? = RULES.firstOrNull { (root, _) -> root in roots }?.second

    /**
     * The kind of a character its classes say nothing of, by its parents' kinds (those known): a god's and a mortal's
     * child — or a god's child whose other parent is unknown — is a demigod, two gods' child a deity, anyone else a mortal.
     */
    fun byParents(parents: List<CharacterKind?>): CharacterKind {
        val gods = parents.count { it in GODS }
        return when {
            gods == 0 -> CharacterKind.MORTAL
            gods >= 2 -> CharacterKind.DEITY
            else -> CharacterKind.HERO
        }
    }
}
