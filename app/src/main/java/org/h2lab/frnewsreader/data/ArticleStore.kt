package org.h2lab.frnewsreader.data

import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the articles in the app private storage so they remain readable offline. */
class ArticleStore(file: File) {
    private val atomicFile = AtomicFile(file)

    fun load(): NewsSnapshot = runCatching {
        if (!atomicFile.baseFile.exists()) return NewsSnapshot()
        val json = JSONObject(atomicFile.readFully().decodeToString())
        val articles = json.optJSONArray("articles") ?: JSONArray()
        val readIds = json.optJSONArray("readIds") ?: JSONArray()
        NewsSnapshot(
            articles = (0 until articles.length()).mapNotNull { index -> articles.getJSONObject(index).toArticle() },
            lastRefreshAt = if (json.has("lastRefreshAt")) json.getLong("lastRefreshAt") else null,
            readIds = (0 until readIds.length()).mapTo(HashSet()) { readIds.getString(it) },
        )
    }.getOrElse { error ->
        Log.w(TAG, "Impossible de lire le cache local", error)
        NewsSnapshot()
    }

    fun save(snapshot: NewsSnapshot) {
        val json = JSONObject()
            .put("articles", JSONArray(snapshot.articles.map { it.toJson() }))
            .put("readIds", JSONArray(snapshot.readIds.toList()))
        snapshot.lastRefreshAt?.let { json.put("lastRefreshAt", it) }
        val stream = atomicFile.startWrite()
        try {
            stream.write(json.toString().encodeToByteArray())
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun Article.toJson() = JSONObject()
        .put("id", id)
        .put("sourceId", sourceId)
        .put("sourceName", sourceName)
        .put("category", category.name)
        .put("title", title)
        .put("summary", summary)
        .put("link", link)
        .put("imageUrl", imageUrl ?: JSONObject.NULL)
        .put("publishedAt", publishedAt)

    private fun JSONObject.toArticle(): Article? = runCatching {
        Article(
            id = getString("id"),
            sourceId = getString("sourceId"),
            sourceName = getString("sourceName"),
            category = NewsCategory.valueOf(getString("category")),
            title = getString("title"),
            summary = optString("summary"),
            link = getString("link"),
            imageUrl = if (isNull("imageUrl")) null else optString("imageUrl"),
            publishedAt = getLong("publishedAt"),
        )
    }.getOrNull()

    private companion object {
        const val TAG = "ArticleStore"
    }
}
