package org.jtools.mythostory.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The catalog's records, as the data files under data/ hold them and as the API serves them (frontend: catalog.ts).
 * Ids are slugs ("zeus", "labours-of-heracles"), the same in every language: the records refer to each other by
 * them, and the page's URLs use them. Every text is an [I18nText].
 */

/** What a character is; the order is the one the page lists them in (its names: the frontend's i18n). */
@Serializable
enum class CharacterKind {
    @SerialName("primordial") PRIMORDIAL,
    @SerialName("titan") TITAN,
    @SerialName("olympian") OLYMPIAN,
    @SerialName("deity") DEITY,
    @SerialName("nymph") NYMPH,
    @SerialName("hero") HERO,
    @SerialName("mortal") MORTAL,
    @SerialName("creature") CREATURE,
}

@Serializable
data class Character(
    val id: String,
    val name: I18nText,
    val kind: CharacterKind,
    /** The Wikidata item ("Q34201"): what scripts/import-characters.sh knows them by, so as to never import them twice. */
    val wikidata: String? = null,
    /** One sentence: who they are. */
    val description: I18nText,
    /** The name in Greek, as the ancient texts spell it. */
    val greek: String? = null,
    /** The Roman counterpart's name, when Rome had one. */
    val roman: I18nText? = null,
    /** Ids of the parents the main tradition gives (Hesiod first, then Apollodorus); empty when none is known. */
    val parents: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
)

@Serializable
data class Story(
    val id: String,
    val title: I18nText,
    /** The story told in our own words: paragraphs separated by a blank line. */
    val summary: I18nText,
    /** Ids of the characters it involves, the main ones first. */
    val characters: List<String>,
    /** Where the story is told in the ancient texts: at least one. */
    val sources: List<Source>,
    val links: List<Link> = emptyList(),
    /** Ids of stories that echo this one beyond its cycles (a sequel elsewhere, the same figure's fate…). */
    val related: List<String> = emptyList(),
)

/** An ancient text telling the story, and where to read it online. */
@Serializable
data class Source(
    val author: I18nText,
    val work: I18nText,
    /** Book, line or section numbers, as editions number them ("453–506", "2.5.1"). */
    val passage: String? = null,
    /** A translation online, in the languages one was found in, at that passage when possible. */
    val url: I18nUrl = emptyMap(),
)

/** A page about a character or a story, in one language. */
@Serializable
data class Link(
    val kind: LinkKind,
    /** The language of the page: one of [I18n.LANGUAGES]. */
    val lang: String,
    /** The site's or the video's own name, as published: not translated. */
    val title: String,
    val url: String,
)

@Serializable
enum class LinkKind {
    /** The Wikipedia article. */
    @SerialName("wikipedia") WIKIPEDIA,
    /** A page on the subject: Theoi, World History Encyclopedia… */
    @SerialName("article") ARTICLE,
    /** A video telling the story. */
    @SerialName("video") VIDEO,
}

/** A sequence of stories read in order: the Theogony, the Trojan War… A story may belong to several. */
@Serializable
data class Cycle(
    val id: String,
    val title: I18nText,
    val description: I18nText,
    /** Ids of its stories, in their order. */
    val stories: List<String>,
)
