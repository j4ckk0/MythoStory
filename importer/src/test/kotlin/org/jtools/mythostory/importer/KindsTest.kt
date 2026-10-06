package org.jtools.mythostory.importer

import org.jtools.mythostory.core.CharacterKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KindsTest {
    @Test
    fun `the most specific class wins, a nymph being a deity and a mythical creature too`() {
        assertEquals(CharacterKind.NYMPH, Kinds.byClasses(setOf("Q2239243", "Q22989102", "Q3346693")))
        assertEquals(CharacterKind.DEITY, Kinds.byClasses(setOf("Q2239243", "Q22989102")))
        assertEquals(CharacterKind.CREATURE, Kinds.byClasses(setOf("Q2239243")))
        assertNull(Kinds.byClasses(emptySet()))
    }

    @Test
    fun `a god's child by a mortal is a demigod, two gods' child a deity, anyone else a mortal`() {
        assertEquals(CharacterKind.HERO, Kinds.byParents(listOf(CharacterKind.OLYMPIAN, CharacterKind.MORTAL)))
        assertEquals(CharacterKind.HERO, Kinds.byParents(listOf(CharacterKind.OLYMPIAN, null)))
        assertEquals(CharacterKind.DEITY, Kinds.byParents(listOf(CharacterKind.TITAN, CharacterKind.NYMPH)))
        assertEquals(CharacterKind.MORTAL, Kinds.byParents(listOf(CharacterKind.MORTAL, null)))
        assertEquals(CharacterKind.MORTAL, Kinds.byParents(emptyList()))
    }
}
