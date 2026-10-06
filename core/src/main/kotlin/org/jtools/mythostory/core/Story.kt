package org.jtools.mythostory.core

import kotlinx.serialization.Serializable

/** A story, as it is stored and as the API serves it (frontend: story.ts). */
@Serializable
data class Story(
    val id: String,
    val title: String,
    val summary: String = "",
)

/** A story the user is about to add: the repository gives it its id. */
@Serializable
data class NewStory(
    val title: String,
    val summary: String = "",
)
