package org.jtools.mythostory.core

/**
 * The application's i18n languages. Every text of the catalog is an [I18nText], which [Catalog.problems] requires in
 * every one of [I18n.LANGUAGES], and in no other; the page's own texts are the frontend's (i18n/en.ts, fr.ts).
 */
object I18n {
    /** ISO 639-1 codes; the first is the fallback when a text lacks a language (it never should). */
    val LANGUAGES = listOf("en", "fr")
}

/** A text in every language of [I18n.LANGUAGES], by code: {"en": "Heracles", "fr": "Héraclès"}. */
typealias I18nText = Map<String, String>

/** The URL of the same resource in the languages it exists in — some, not necessarily all, of [I18n.LANGUAGES]. */
typealias I18nUrl = Map<String, String>
