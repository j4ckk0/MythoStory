package org.jtools.mythostory.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogTest {
    private val catalog = Catalog.load()

    @Test
    fun `the catalog the jar carries is sound`() {
        assertEquals(emptyList(), catalog.problems())
        assertTrue(catalog.stories.isNotEmpty() && catalog.characters.isNotEmpty() && catalog.cycles.isNotEmpty())
    }

    @Test
    fun `every data file is listed by the index, so that none is forgotten`() {
        val data = File("core/src/main/resources/data")
        val index = data.resolve("index.json").readText()
        data.walkTopDown().filter { it.isFile && it.name != "index.json" }.forEach {
            assertContains(index, "\"${it.relativeTo(data).invariantSeparatorsPath}\"")
        }
    }

    private fun both(text: String): I18nText = I18n.LANGUAGES.associateWith { text }

    @Test
    fun `dangling references, duplicates, missing sources, unused places, lone groups and i18n gaps are caught`() {
        val zeus = Character("zeus", both("Zeus"), CharacterKind.OLYMPIAN, description = both("King of the gods"), parents = listOf("cronus"))
        val story = Story(
            "a-story", both("A story"), mapOf("en" to "Once upon a time"), listOf("zeus", "nobody"),
            sources = emptyList(), links = listOf(Link(LinkKind.VIDEO, "de", "A video", "https://example.org")), related = listOf("none"),
            era = "heroes", places = listOf("troy", "atlantis"),
        )
        val troy = Place("troy", both("Troy"), 39.96, 26.24, "Q22647")
        val delphi = Place("delphi", both("Delphi"), 38.48, 22.5, "Q75459")
        val argonauts = Group("argonauts", both("Argonauts"), mapOf("en" to "Jason's crew"), listOf("zeus", "zeus", "jason"))
        val alone = Group("alone", both("Alone"), both("A group of one"), listOf("zeus"))
        val problems = Catalog(
            listOf(zeus, zeus), listOf(story.copy(groups = listOf("argonauts", "muses"))), listOf(Cycle("c", both("C"), both("A cycle"), listOf("a-story", "gone"))),
            eras = listOf(Era("heroes", both("Heroes"), both("The age of heroes"))), places = listOf(troy, delphi), groups = listOf(argonauts, alone),
        ).problems()
        assertEquals(
            setOf(
                "character zeus is defined twice",
                "character zeus: no parent cronus",
                "story a-story has no source",
                "story a-story: its summary lacks its \"fr\" i18n text",
                "story a-story: link https://example.org is in \"de\", which is not an i18n language",
                "story a-story: no character nobody",
                "story a-story: no related story none",
                "cycle c: no story gone",
                "story a-story: no place atlantis",
                "place delphi is in no story",
                "story a-story: no group muses",
                "group argonauts: its description lacks its \"fr\" i18n text",
                "group argonauts: no member jason",
                "group argonauts lists zeus twice",
                "group alone has fewer than two members",
            ),
            problems.toSet(),
        )
    }

    @Test
    fun `a story in no cycle, an entry listed twice, a story related to itself are caught`() {
        val zeus = Character("zeus", both("Zeus"), CharacterKind.OLYMPIAN, description = both("King of the gods"), parents = listOf("cronus", "cronus"))
        val cronus = Character("cronus", both("Cronus"), CharacterKind.TITAN, description = both("The Titan king"))
        val source = Source(both("Hesiod"), both("Theogony"))
        val troy = Place("troy", both("Troy"), 39.96, 26.24, "Q22647")
        val ithaca = Place("ithaca", both("Ithaca"), 38.4, 20.7, "Q187232")
        val told = Story(
            "told", both("Told"), both("Once"), listOf("zeus", "zeus"), groups = listOf("gods", "gods"), sources = listOf(source),
            related = listOf("told", "untold", "untold"), era = "gods", places = listOf("troy", "troy"),
        )
        val journey = Story("journey", both("Journey"), both("Away and back"), listOf("cronus"), sources = listOf(source), era = "gods",
            places = listOf("ithaca", "troy", "ithaca"), route = true)
        val untold = Story("untold", both("Untold"), both("Never listed"), listOf("cronus"), sources = listOf(source), era = "gods")
        val problems = Catalog(
            listOf(zeus, cronus), listOf(told, journey, untold), listOf(Cycle("c", both("C"), both("A cycle"), listOf("told", "journey"))),
            eras = listOf(Era("gods", both("Gods"), both("The age of gods"))), places = listOf(troy, ithaca),
            groups = listOf(Group("gods", both("Gods"), both("The gods"), listOf("zeus", "cronus"))),
        ).problems()
        assertEquals(
            setOf(
                "character zeus lists parent cronus twice",
                "story told lists character zeus twice",
                "story told lists group gods twice",
                "story told lists related story untold twice",
                "story told is related to itself",
                "story told lists place troy twice",
                "story untold is in no cycle: the page would show it nowhere",
            ),
            problems.toSet(),
            "a route passing a place twice is a journey home, not a fault",
        )
    }
}
