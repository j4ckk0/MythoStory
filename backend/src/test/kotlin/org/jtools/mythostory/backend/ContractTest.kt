package org.jtools.mythostory.backend

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import org.jtools.mythostory.core.Catalog
import org.jtools.mythostory.core.Character
import org.jtools.mythostory.core.CharacterKind
import org.jtools.mythostory.core.Cycle
import org.jtools.mythostory.core.Era
import org.jtools.mythostory.core.Group
import org.jtools.mythostory.core.Link
import org.jtools.mythostory.core.LinkKind
import org.jtools.mythostory.core.Place
import org.jtools.mythostory.core.Source
import org.jtools.mythostory.core.Story
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The API's records as the page reads them: the TypeScript interfaces of frontend/src/app (catalog.ts, help.ts),
 * written by hand, against the Kotlin classes the server serializes (core: Model.kt, backend). For each record, the
 * same fields; and every field the server may leave out — one with a default, which kotlinx.serialization does not
 * write — optional in TypeScript, so that the page never takes for granted what may not come. For each enum, the same
 * values. (The tests run from the root of the repository.)
 */
class ContractTest {
    private val catalogTs = File("frontend/src/app/catalog/catalog.ts").readText()
    private val helpTs = File("frontend/src/app/help/help.ts").readText()

    /** A TypeScript interface's fields: each one's name, and whether it is optional (`name?:`). */
    private fun fields(source: String, name: String): Map<String, Boolean> {
        val body = Regex("export interface $name \\{(.*?)\\n\\}", RegexOption.DOT_MATCHES_ALL).find(source)?.groupValues?.get(1)
            ?: error("no interface $name")
        return Regex("^  (\\w+)(\\?)?:", RegexOption.MULTILINE).findAll(body).associate { it.groupValues[1] to it.groupValues[2].isNotEmpty() }
    }

    private fun check(serializer: KSerializer<*>, source: String, tsName: String) {
        val descriptor: SerialDescriptor = serializer.descriptor
        val kotlin = (0 until descriptor.elementsCount).associate { descriptor.getElementName(it) to descriptor.isElementOptional(it) }
        val ts = fields(source, tsName)
        assertEquals(kotlin.keys, ts.keys, "$tsName's fields, in Kotlin (${descriptor.serialName}) and in TypeScript")
        kotlin.filterValues { it }.keys.forEach { field ->
            assertTrue(ts.getValue(field), "$tsName.$field may be left out by the server (a default in Kotlin): optional in TypeScript, $field?:")
        }
    }

    @Test
    fun `the catalog's records are the same in Kotlin and in TypeScript`() {
        check(Catalog.serializer(), catalogTs, "Catalog")
        check(Character.serializer(), catalogTs, "Character")
        check(Story.serializer(), catalogTs, "Story")
        check(Group.serializer(), catalogTs, "Group")
        check(Cycle.serializer(), catalogTs, "Cycle")
        check(Era.serializer(), catalogTs, "Era")
        check(Place.serializer(), catalogTs, "Place")
        check(Source.serializer(), catalogTs, "Source")
        check(Link.serializer(), catalogTs, "Link")
    }

    @Test
    fun `the server's other answers are the same in Kotlin and in TypeScript`() {
        check(Info.serializer(), catalogTs, "Info")
        check(Voice.serializer(), catalogTs, "Voice")
        check(Licences.serializer(), helpTs, "Licences")
        check(ThirdPartyComponent.serializer(), helpTs, "ThirdPartyComponent")
    }

    @Test
    fun `the enums have the same values in Kotlin and in TypeScript`() {
        for ((serializer, name) in listOf(CharacterKind.serializer() to "CharacterKind", LinkKind.serializer() to "LinkKind")) {
            val descriptor = serializer.descriptor
            val kotlin = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
            val union = Regex("export type $name = (.*?);").find(catalogTs)?.groupValues?.get(1) ?: error("no type $name")
            assertEquals(kotlin, Regex("'([^']+)'").findAll(union).map { it.groupValues[1] }.toList(), "$name's values, in their order")
        }
    }
}
