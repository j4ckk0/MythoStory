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

    @Test
    fun `every story belongs to a cycle, so that the page lists it`() {
        val inCycles = catalog.cycles.flatMap { it.stories }.toSet()
        assertEquals(emptyList(), catalog.stories.map { it.id }.filterNot { it in inCycles })
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
}
