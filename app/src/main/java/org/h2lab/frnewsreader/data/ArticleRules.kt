package org.h2lab.frnewsreader.data

import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TextNormalizer {
    private val diacritics = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace('’', '\'')
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()
}

object Classifier {
    fun classify(feed: SourceFeed, categories: List<String>, link: String): NewsCategory? {
        if (feed.rules.isEmpty()) return feed.defaultTag
        val terms = (categories.map(TextNormalizer::normalize) + pathSegments(link)).toSet()
        val match = feed.rules.firstOrNull { rule -> terms.any { it in rule.keywords } }
        return if (match != null) match.tag else feed.defaultTag
    }

    private fun pathSegments(link: String): List<String> =
        runCatching { URI(link).path.orEmpty() }.getOrDefault("")
            .split('/')
            .filter { it.isNotBlank() }
            .map(TextNormalizer::normalize)
}

object UrlNormalizer {
    private val trackingPrefixes = listOf("utm_", "xtor", "at_", "fbclid", "gclid")

    /** Removes fragments and tracking parameters so that the same article is deduplicated. */
    fun normalize(link: String): String {
        val trimmed = link.trim()
        return runCatching {
            val uri = URI(trimmed)
            val query = uri.rawQuery
                ?.split('&')
                ?.filter { param -> param.isNotBlank() && trackingPrefixes.none { param.lowercase(Locale.ROOT).startsWith(it) } }
                ?.joinToString("&")
                ?.ifBlank { null }
            URI(uri.scheme?.lowercase(Locale.ROOT), uri.rawAuthority?.lowercase(Locale.ROOT), uri.rawPath, null, null)
                .toString()
                .let { base -> if (query != null) "$base?$query" else base }
        }.getOrDefault(trimmed.substringBefore('#'))
    }
}

object DateParser {
    private val whitespace = Regex("\\s+")
    private val rfcFormatters = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm Z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ENGLISH),
    )

    fun parse(text: String?): Long? {
        val value = text?.trim()?.replace(whitespace, " ")?.ifEmpty { null } ?: return null
        runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(value).toEpochMilli() }
        for (formatter in rfcFormatters) {
            runCatching { return ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli() }
        }
        return null
    }
}

object TextCleaner {
    private val whitespace = Regex("\\s+")
    private val wordpressTrailer = Regex("L['’]article .+ est apparu en premier sur .+$")
    private const val MAX_SUMMARY_LENGTH = 320

    fun cleanSummary(text: String): String {
        var result = text.replace('\uFFFC', ' ').replace(whitespace, " ").trim()
        result = result.replace(wordpressTrailer, "").trim()
        // Le Monde diplomatique appends its tag list after "(…) / ".
        val tagsSeparator = result.indexOf("(…) / ")
        if (tagsSeparator >= 0) result = result.substring(0, tagsSeparator + 3)
        if (result.length > MAX_SUMMARY_LENGTH) {
            result = result.take(MAX_SUMMARY_LENGTH).substringBeforeLast(' ').trimEnd(',', ';', ':') + "…"
        }
        return result
    }
}

object ArticleMerger {
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    const val MAX_PER_CATEGORY = 300

    data class Result(val articles: List<Article>, val newCount: Int)

    fun merge(existing: List<Article>, fresh: List<Article>, now: Long): Result {
        val byId = LinkedHashMap<String, Article>()
        existing.forEach { byId[it.id] = it }
        val existingIds = byId.keys.toSet()
        val freshIds = mutableSetOf<String>()
        fresh.forEach { article ->
            byId[article.id] = article
            freshIds += article.id
        }
        val articles = byId.values
            .filter { it.publishedAt >= now - MAX_AGE_MS }
            .sortedByDescending { it.publishedAt }
            .groupBy { it.category }
            .flatMap { (_, list) -> list.take(MAX_PER_CATEGORY) }
            .sortedByDescending { it.publishedAt }
        val keptIds = articles.mapTo(HashSet()) { it.id }
        return Result(articles, freshIds.count { it !in existingIds && it in keptIds })
    }
}

sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val value: Int) : RelativeTime
    data class Hours(val value: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class Days(val value: Int) : RelativeTime
    data class Date(val epochMillis: Long) : RelativeTime

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        fun of(time: Long, now: Long): RelativeTime {
            val elapsed = (now - time).coerceAtLeast(0)
            return when {
                elapsed < MINUTE -> JustNow
                elapsed < HOUR -> Minutes((elapsed / MINUTE).toInt())
                elapsed < DAY -> Hours((elapsed / HOUR).toInt())
                elapsed < 2 * DAY -> Yesterday
                elapsed < 7 * DAY -> Days((elapsed / DAY).toInt())
                else -> Date(time)
            }
        }
    }
}
