package org.jtools.mythostory.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The catalog's records, as the data files under data/ hold them and as the API serves them (frontend: catalog.ts,
 * which backend's ContractTest keeps the same). Ids are slugs ("zeus", "labours-of-heracles"), the same in every
 * language: the records refer to each other by them, and the page's URLs use them. Every text is an [I18nText].
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
    /** The Wikidata item ("Q34201"): the importer (scripts/linux/import-characters.sh) matches characters by it, so as never to import one twice. */
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
    /** Ids of the groups it involves as a group: the Argonauts in the voyage of the Argo, the Seven against Thebes… */
    val groups: List<String> = emptyList(),
    /** Where the story is told in the ancient texts: at least one. */
    val sources: List<Source>,
    val links: List<Link> = emptyList(),
    /** Ids of stories that echo this one beyond its cycles (a sequel elsewhere, the same figure's fate…). */
    val related: List<String> = emptyList(),
    /** The id of its era, on the timeline. */
    val era: String,
    /**
     * How many generations before the Trojan War it happens (0: the war's own), as the genealogies count them —
     * Perseus is Heracles' great-grandfather, Heracles' son fought at Troy; null when the tradition does not say.
     */
    val generation: Int? = null,
    /** The date ancient chronographers gave it, when they did: "1529/1528 BC according to the Parian Chronicle". */
    val dating: I18nText? = null,
    /** Ids of the places it happens in, on the map; in the order of the journey when [route] is true. */
    val places: List<String> = emptyList(),
    /** Whether [places] are a journey, drawn as a route: the Argo's voyage, Heracles' labours… */
    val route: Boolean = false,
)

/**
 * A group the myths name as one: the Argonauts, the Muses, the Seven against Thebes… Its members are characters of the
 * catalog, as an ancient list gives them ([sources]); a member may belong to several groups (Castor: an Argonaut and
 * a hunter of Calydon). The groups a character belongs to are derived from here.
 */
@Serializable
data class Group(
    val id: String,
    val name: I18nText,
    /** Who they are, in a sentence or two: what makes them a group, and how many the tradition counts. */
    val description: I18nText,
    /** Ids of its members, in the order of the list it comes from. */
    val members: List<String>,
    /** The Wikidata item of the group itself ("Q165510": the Argonauts). */
    val wikidata: String? = null,
    /** Where the ancient texts list its members; none for a group no single text lists. */
    val sources: List<Source> = emptyList(),
    val links: List<Link> = emptyList(),
)

/** A period of the mythic chronology: the timeline's sections, in the order of data/eras.json. */
@Serializable
data class Era(
    val id: String,
    val title: I18nText,
    val description: I18nText,
    /** What ancient chronographers dated in it, when they did. */
    val dating: I18nText? = null,
)

/** A place on the map: a real one, or a mythical one the ancients identified with a real one ([identification]). */
@Serializable
data class Place(
    val id: String,
    val name: I18nText,
    val lat: Double,
    val lon: Double,
    /** The Wikidata item the coordinates come from. */
    val wikidata: String,
    /** Set when the place is mythical, or its location disputed: who identified it with this one, and how surely. */
    val identification: I18nText? = null,
)

/** An ancient text — one that tells a story, or lists a group's members — and where to read it online. */
@Serializable
data class Source(
    val author: I18nText,
    val work: I18nText,
    /** Book, line or section numbers, as editions number them ("453–506", "2.5.1"). */
    val passage: String? = null,
    /** A translation online, in the languages one was found in, at that passage when possible. */
    val url: I18nUrl = emptyMap(),
)

/** A page about a character, a group or a story, in one language. */
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
