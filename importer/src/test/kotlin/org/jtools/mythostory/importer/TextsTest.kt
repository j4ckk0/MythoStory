package org.jtools.mythostory.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextsTest {
    @Test
    fun `slugs drop accents and punctuation`() {
        assertEquals("heracles", Texts.slug("Héraclès"))
        assertEquals("oedipus", Texts.slug("Œdipus"))
        assertEquals("leon-of-nemea", Texts.slug("  Leon of Nemea! "))
        assertEquals("", Texts.slug("Ζεύς"))
    }

    @Test
    fun `Greek names lose the transliteration Wikidata appends`() {
        assertEquals("Πηνελόπη", Texts.greek("Πηνελόπη , Pēnelopē"))
        assertEquals("Αἴας", Texts.greek("Αἴας|Aías"))
        assertEquals("Ζεύς", Texts.greek("Ζεύς"))
    }

    @Test
    fun `Wikidata descriptions become sentences`() {
        assertEquals("Daughter of Danaus.", Texts.sentence("daughter of Danaus"))
        assertEquals("King of Argos.", Texts.sentence("King of Argos."))
    }

    @Test
    fun `descriptions that only say Greek mythology are generic, in both languages`() {
        listOf(
            "Greek mythological figure", "figure in Greek mythology", "character from Greek mythology", "Greek mythical king",
            "personnage de la mythologie grecque", "figure de la mythologie grecque", "nymphe de la mythologie grecque", "",
        ).forEach { assertTrue(Texts.isGeneric(it), it) }
        listOf("daughter of Danaus", "king of Argos in Greek mythology", "fille de Danaos").forEach { assertFalse(Texts.isGeneric(it), it) }
    }

    @Test
    fun `a child's description names the parents, with French elision`() {
        assertEquals("Son of Zeus and Leda in Greek mythology.", Texts.childOf("male", listOf("Zeus", "Leda"), "en"))
        assertEquals("Fille de Zeus et d’Héra dans la mythologie grecque.", Texts.childOf("female", listOf("Zeus", "Héra"), "fr"))
        assertEquals("Enfant d’Apollon dans la mythologie grecque.", Texts.childOf(null, listOf("Apollon"), "fr"))
        assertEquals("Son of Aether, Gaia and Nyx in Greek mythology.", Texts.childOf("male", listOf("Aether", "Gaia", "Nyx"), "en"))
        assertEquals("Fils d’Éther, de Gaïa et de Nyx dans la mythologie grecque.", Texts.childOf("male", listOf("Éther", "Gaïa", "Nyx"), "fr"))
    }

    @Test
    fun `a Theoi page's title gives its name`() {
        assertEquals("Cronus (Kronos)", Theoi.title("<title>CRONUS (Kronos) - Greek Titan God of Time</title>"))
        assertEquals("Uranus (Ouranos)", Theoi.title("<title>URANUS (Ouranos)- Greek Primordial God</title>"))
        assertEquals("Medusa & Gorgons (Medousa & Gorgones)", Theoi.title("<title>MEDUSA &amp; GORGONS (Medousa &amp; Gorgones) - Monsters</title>"))
        assertEquals("Heracles", Theoi.title("<title>Heracles | Theoi Greek Mythology</title>"))
    }
}
