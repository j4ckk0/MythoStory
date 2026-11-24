package org.jtools.mythostory.importer

import kotlin.test.Test
import kotlin.test.assertEquals

/** The imported characters' ids, which are their pages' addresses: stable from one import to the next. */
class IdsTest {
    @Test
    fun `a first import suffixes the names several characters share, and a name with no Latin letter becomes the item`() {
        val ids = WikidataImport.ids(mapOf("Q1" to "medusa", "Q2" to "medusa", "Q3" to "ladon", "Q4" to ""), taken = emptySet(), previous = emptyMap())
        assertEquals(mapOf("Q1" to "medusa-q1", "Q2" to "medusa-q2", "Q3" to "ladon", "Q4" to "q4"), ids)
    }

    @Test
    fun `a character keeps its id when a newcomer shares its name, and the newcomer is the one suffixed`() {
        val ids = WikidataImport.ids(mapOf("Q1" to "medusa", "Q9" to "medusa"), taken = emptySet(), previous = mapOf("Q1" to "medusa"))
        assertEquals(mapOf("Q1" to "medusa", "Q9" to "medusa-q9"), ids)
    }

    @Test
    fun `a character keeps its id when the others sharing its name are gone, or when its name changed`() {
        val ids = WikidataImport.ids(mapOf("Q1" to "medusa", "Q3" to "drakon"), taken = emptySet(), previous = mapOf("Q1" to "medusa-q1", "Q3" to "ladon"))
        assertEquals(mapOf("Q1" to "medusa-q1", "Q3" to "ladon"), ids)
    }

    @Test
    fun `a curated character takes the id it is given, the imported one then suffixed`() {
        val ids = WikidataImport.ids(mapOf("Q1" to "medusa"), taken = setOf("medusa"), previous = mapOf("Q1" to "medusa"))
        assertEquals(mapOf("Q1" to "medusa-q1"), ids)
    }
}
