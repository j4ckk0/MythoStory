package org.jtools.mythostory.core

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonFileStoryRepositoryTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `a story added is there, trimmed, after a restart`() {
        val file = File(dir, "data/stories.json")
        val added = JsonFileStoryRepository(file).add(NewStory("  The Odyssey ", "A long way home"))

        val reopened = JsonFileStoryRepository(file).all()
        assertEquals(listOf(added), reopened)
        assertEquals("The Odyssey", reopened.single().title)
    }

    @Test
    fun `no file, no stories`() {
        assertTrue(JsonFileStoryRepository(File(dir, "none.json")).all().isEmpty())
    }

    @Test
    fun `a story without a title is refused`() {
        assertFailsWith<IllegalArgumentException> { JsonFileStoryRepository(File(dir, "s.json")).add(NewStory(" ")) }
    }
}
