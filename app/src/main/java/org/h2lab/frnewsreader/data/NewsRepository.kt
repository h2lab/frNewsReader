package org.h2lab.frnewsreader.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class RefreshResult(
    val newArticles: Int,
    val failedSources: List<String>,
    val totalSources: Int,
)

class NewsRepository(
    private val store: ArticleStore,
    private val client: OkHttpClient,
    private val sources: List<NewsSource> = SourceCatalog.sources,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow<NewsSnapshot?>(null)

    /** Null until the local cache has been loaded. */
    val snapshot: StateFlow<NewsSnapshot?> = _snapshot.asStateFlow()

    suspend fun load() {
        mutex.withLock { currentSnapshot() }
    }

    suspend fun refresh(): RefreshResult = mutex.withLock {
        val now = clock()
        val results = coroutineScope {
            sources.flatMap { source ->
                source.feeds.map { feed ->
                    async(Dispatchers.IO) {
                        source to runCatching { fetchFeed(source, feed, now) }
                            .onFailure { Log.w(TAG, "Échec du flux ${feed.url}", it) }
                    }
                }
            }.awaitAll()
        }
        val failedSources = results
            .groupBy({ it.first }, { it.second })
            .filterValues { feedResults -> feedResults.all { it.isFailure } }
            .keys
            .map { it.name }
        val fresh = results.flatMap { it.second.getOrDefault(emptyList()) }

        val current = currentSnapshot()
        val merged = ArticleMerger.merge(current.articles, fresh, now)
        val keptIds = merged.articles.mapTo(HashSet()) { it.id }
        val updated = NewsSnapshot(
            articles = merged.articles,
            lastRefreshAt = if (failedSources.size < sources.size) now else current.lastRefreshAt,
            readIds = current.readIds.filterTo(HashSet()) { it in keptIds },
        )
        persist(updated)
        RefreshResult(merged.newCount, failedSources, sources.size)
    }

    suspend fun markRead(articleId: String) = mutex.withLock {
        val current = currentSnapshot()
        if (articleId !in current.readIds) persist(current.copy(readIds = current.readIds + articleId))
    }

    private suspend fun currentSnapshot(): NewsSnapshot {
        _snapshot.value?.let { return it }
        val cached = withContext(Dispatchers.IO) { store.load() }
        val sourceIds = sources.mapTo(HashSet()) { it.id }
        val articles = cached.articles.filter { it.sourceId in sourceIds }
        val articleIds = articles.mapTo(HashSet()) { it.id }
        val updated = cached.copy(
            articles = articles,
            readIds = cached.readIds.filterTo(HashSet()) { it in articleIds },
        )
        if (updated != cached) persist(updated) else _snapshot.value = updated
        return updated
    }

    private suspend fun persist(snapshot: NewsSnapshot) {
        _snapshot.value = snapshot
        withContext(Dispatchers.IO) { store.save(snapshot) }
    }

    private fun fetchFeed(source: NewsSource, feed: SourceFeed, now: Long): List<Article> {
        val request = Request.Builder()
            .url(feed.url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.5")
            .header("Accept-Language", "fr-FR,fr;q=0.9")
            .build()
        val items = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            FeedParser.parse(response.body.byteStream())
        }
        return items.take(MAX_ITEMS_PER_FEED).mapNotNull { item ->
            val category = Classifier.classify(feed, item.categories, item.link) ?: return@mapNotNull null
            val link = UrlNormalizer.normalize(item.link)
            Article(
                id = "${category.name}|$link",
                sourceId = source.id,
                sourceName = source.name,
                category = category,
                title = item.title,
                summary = item.summary,
                link = link,
                imageUrl = item.imageUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                // Some feeds publish wrong time zones: never show an article in the future.
                publishedAt = (item.publishedAt ?: now).coerceAtMost(now),
            )
        }
    }

    private companion object {
        const val TAG = "NewsRepository"
        const val MAX_ITEMS_PER_FEED = 60
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) frNewsReader/1.0"
    }
}
