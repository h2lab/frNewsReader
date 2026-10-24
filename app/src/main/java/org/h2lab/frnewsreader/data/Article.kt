package org.h2lab.frnewsreader.data

data class Article(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val category: NewsCategory,
    val title: String,
    val summary: String,
    val link: String,
    val imageUrl: String?,
    val publishedAt: Long,
)

data class NewsSnapshot(
    val articles: List<Article> = emptyList(),
    val lastRefreshAt: Long? = null,
    val readIds: Set<String> = emptySet(),
)
