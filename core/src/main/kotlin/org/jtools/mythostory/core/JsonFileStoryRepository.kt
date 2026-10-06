package org.jtools.mythostory.core

import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * The stories in one JSON file — under the user's home on the desktop, in the app's private storage on Android.
 * Every change rewrites the file through a temporary one renamed into place, so that a crash never leaves it half written.
 */
class JsonFileStoryRepository(private val file: File) : StoryRepository {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val lock = Any()
    private var stories: List<Story> =
        if (file.isFile) json.decodeFromString(file.readText()) else emptyList()

    override fun all(): List<Story> = synchronized(lock) { stories }

    override fun add(story: NewStory): Story {
        require(story.title.isNotBlank()) { "a story needs a title" }
        val added = Story(UUID.randomUUID().toString(), story.title.trim(), story.summary.trim())
        synchronized(lock) {
            stories = stories + added
            save()
        }
        return added
    }

    private fun save() {
        file.absoluteFile.parentFile.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(stories))
        if (!tmp.renameTo(file)) {
            // Windows refuses to rename over an existing file
            file.delete()
            check(tmp.renameTo(file)) { "cannot write $file" }
        }
    }
}
