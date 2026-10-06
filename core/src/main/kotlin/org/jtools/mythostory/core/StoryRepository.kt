package org.jtools.mythostory.core

/** Where the stories are kept: one implementation per kind of storage, the same for every platform. */
interface StoryRepository {
    fun all(): List<Story>

    /** Adds the story under a new id; [IllegalArgumentException] when its title is blank. */
    fun add(story: NewStory): Story
}
