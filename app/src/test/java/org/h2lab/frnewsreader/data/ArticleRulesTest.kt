package org.h2lab.frnewsreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ClassifierTest {
    private fun feedOf(id: String) = SourceCatalog.sources.single { it.id == id }.feeds.single()

    @Test
    fun feedWithoutRulesUsesDefaultTag() {
        val feed = SourceFeed("https://example.com/rss", NewsCategory.INTERNATIONAL)
        assertEquals(NewsCategory.INTERNATIONAL, Classifier.classify(feed, listOf("Sport"), "https://example.com/a"))
    }

    @Test
    fun marianneArticlesAreClassifiedByCategoryAndExcludedOtherwise() {
        val feed = feedOf("marianne")
        assertEquals(NewsCategory.INTERNATIONAL, Classifier.classify(feed, listOf("Monde"), "https://www.marianne.net/monde/ameriques/x"))
        assertEquals(NewsCategory.FRANCE, Classifier.classify(feed, listOf("Société"), "https://www.marianne.net/societe/x"))
        assertEquals(NewsCategory.FRANCE, Classifier.classify(feed, listOf("Économie"), "https://www.marianne.net/economie/x"))
        assertEquals(NewsCategory.TECHNOLOGIES, Classifier.classify(feed, listOf("Société"), "https://www.marianne.net/societe/big-brother/x"))
        assertNull(Classifier.classify(feed, listOf("Culture"), "https://www.marianne.net/culture/litterature/x"))
    }

    @Test
    fun lesEchosArticlesAreClassifiedByUrlPath() {
        val feed = feedOf("lesechos")
        assertEquals(NewsCategory.TECHNOLOGIES, Classifier.classify(feed, emptyList(), "https://www.lesechos.fr/tech-medias/intelligence-artificielle/x-123"))
        assertEquals(NewsCategory.INTERNATIONAL, Classifier.classify(feed, emptyList(), "https://www.lesechos.fr/monde/europe/x-123"))
        assertEquals(NewsCategory.FRANCE, Classifier.classify(feed, emptyList(), "https://www.lesechos.fr/politique-societe/x-123"))
        assertNull(Classifier.classify(feed, emptyList(), "https://www.lesechos.fr/finance-marches/x-123"))
    }

    @Test
    fun grandContinentAndMondeDiplomatiqueUseTagsWithInternationalDefault() {
        val gc = feedOf("legrandcontinent")
        assertEquals(NewsCategory.TECHNOLOGIES, Classifier.classify(gc, listOf("Puissances de l’IA"), "https://legrandcontinent.eu/fr/2026/10/24/x/"))
        assertEquals(NewsCategory.INTERNATIONAL, Classifier.classify(gc, listOf("Élections"), "https://legrandcontinent.eu/fr/2026/10/24/x/"))
        val diplo = feedOf("mondediplo")
        assertEquals(NewsCategory.TECHNOLOGIES, Classifier.classify(diplo, listOf("Internet", "France"), "https://www.monde-diplomatique.fr/2026/10/X"))
        assertEquals(NewsCategory.FRANCE, Classifier.classify(diplo, listOf("Politique", "France"), "https://www.monde-diplomatique.fr/2026/10/X"))
        assertEquals(NewsCategory.INTERNATIONAL, Classifier.classify(diplo, listOf("Brésil"), "https://www.monde-diplomatique.fr/carnet/x"))
    }
}

class SourceCatalogTest {
    @Test
    fun everySourceFromSourcesFileIsConfigured() {
        val expected = setOf(
            "Le Monde", "Marianne", "Les Échos", "Le Figaro", "Libération",
            "Le Grand Continent", "Le Monde diplomatique", "Euronews", "franceinfo",
            "Cyberattaque.org", "The Hacker News", "BleepingComputer", "Korben",
            "ZATAZ", "LeMagIT", "CERT-FR", "Next",
        )
        assertEquals(expected, SourceCatalog.sources.map { it.name }.toSet())
    }

    @Test
    fun everyTabHasSourcesAndFeedsUseHttps() {
        val feeds = SourceCatalog.sources.flatMap { it.feeds }
        assertTrue(feeds.all { it.url.startsWith("https://") })
        val tags = feeds.flatMap { feed -> listOfNotNull(feed.defaultTag) + feed.rules.mapNotNull { it.tag } }.toSet()
        assertEquals(NewsCategory.entries.toSet(), tags)
        assertEquals(SourceCatalog.sources.size, SourceCatalog.sources.map { it.id }.toSet().size)
    }

    @Test
    fun addedTechnologySourcesUseVerifiedFeedsAndTechnologyClassification() {
        val expectedFeeds = mapOf(
            "cyberattaque" to "https://www.cyberattaque.org/feed/",
            "thehackernews" to "https://feeds.feedburner.com/TheHackersNews",
            "bleepingcomputer" to "https://www.bleepingcomputer.com/feed/",
            "korben" to "https://korben.info/feed",
            "zataz" to "https://www.zataz.com/feed/",
            "lemagit" to "https://www.lemagit.fr/rss/ContentSyndication.xml",
            "certfr" to "https://cert.cyber.gouv.fr/feed/",
            "next" to "https://next.ink/feed/",
        )
        expectedFeeds.forEach { (id, url) ->
            val source = SourceCatalog.sources.single { it.id == id }
            val feed = source.feeds.single()
            assertEquals(url, feed.url)
            assertEquals(
                NewsCategory.TECHNOLOGIES,
                Classifier.classify(feed, listOf("Security", "Actualités"), "https://example.com/article"),
            )
        }
    }
}

class UrlNormalizerTest {
    @Test
    fun removesTrackingParametersAndFragments() {
        assertEquals(
            "https://www.marianne.net/monde/x",
            UrlNormalizer.normalize("https://www.marianne.net/monde/x#utm_source=RSS-MARIANNE&utm_medium=Flux-Rss"),
        )
        assertEquals(
            "https://www.franceinfo.fr/monde/a_8251921.html",
            UrlNormalizer.normalize("https://www.franceinfo.fr/monde/a_8251921.html#xtor=RSS-3-%5Bgeneral%5D"),
        )
        assertEquals(
            "https://example.com/a?id=4",
            UrlNormalizer.normalize("https://EXAMPLE.com/a?utm_campaign=x&id=4&xtor=RSS"),
        )
    }

    @Test
    fun keepsUnparsableLinksWithoutFragment() {
        assertEquals("https://example.com/a b", UrlNormalizer.normalize("https://example.com/a b#frag"))
    }
}

class DateParserTest {
    private fun epoch(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun parsesRfc822Variants() {
        assertEquals(epoch("2026-10-24T06:38:51+01:00"), DateParser.parse("Sat, 24 Oct 2026 06:38:51 +0100 "))
        assertEquals(epoch("2026-10-24T07:00:00+02:00"), DateParser.parse("Sat, 24 Oct 2026 07:00:00 +0200"))
        assertEquals(epoch("2026-10-24T04:30:00Z"), DateParser.parse("Sat, 24 Oct 2026 04:30:00 GMT"))
        assertEquals(epoch("2026-10-02T06:07:29Z"), DateParser.parse("Fri, 2 Oct 2026 06:07:29 +0000"))
    }

    @Test
    fun parsesIsoDates() {
        assertEquals(epoch("2026-10-23T16:34:55Z"), DateParser.parse("2026-10-23T16:34:55Z"))
        assertEquals(epoch("2026-10-23T16:34:55+02:00"), DateParser.parse("2026-10-23T16:34:55+02:00"))
    }

    @Test
    fun returnsNullForInvalidDates() {
        assertNull(DateParser.parse(null))
        assertNull(DateParser.parse(""))
        assertNull(DateParser.parse("hier soir"))
    }
}

class TextCleanerTest {
    @Test
    fun removesWordpressTrailerAndDiploTags() {
        assertEquals(
            "L’ancien président démocrate était resté en retrait",
            TextCleaner.cleanSummary("L’ancien président démocrate était resté en retrait L’article Obama s’engage est apparu en premier sur Le Grand Continent."),
        )
        assertEquals(
            "A la veille du scrutin, les deux (…)",
            TextCleaner.cleanSummary("A la veille du scrutin, les deux (…) / Politique, Parti politique, Brésil"),
        )
    }

    @Test
    fun truncatesLongSummariesOnWordBoundary() {
        val summary = TextCleaner.cleanSummary("mot ".repeat(200))
        assertTrue(summary.length <= 321)
        assertTrue(summary.endsWith("mot…"))
    }
}

class ArticleMergerTest {
    private val now = 1_800_000_000_000L

    private fun article(id: String, publishedAt: Long, category: NewsCategory = NewsCategory.FRANCE, title: String = id) =
        Article(id, "src", "Source", category, title, "", "https://example.com/$id", null, publishedAt)

    @Test
    fun mergesDeduplicatesAndCountsNewArticles() {
        val existing = listOf(article("a", now - 3_000), article("b", now - 2_000))
        val fresh = listOf(article("b", now - 2_000, title = "B mis à jour"), article("c", now - 1_000))
        val result = ArticleMerger.merge(existing, fresh, now)
        assertEquals(listOf("c", "b", "a"), result.articles.map { it.id })
        assertEquals("B mis à jour", result.articles[1].title)
        assertEquals(1, result.newCount)
    }

    @Test
    fun dropsArticlesOlderThanRetention() {
        val result = ArticleMerger.merge(
            existing = listOf(article("old", now - ArticleMerger.MAX_AGE_MS - 1)),
            fresh = listOf(article("recent", now)),
            now = now,
        )
        assertEquals(listOf("recent"), result.articles.map { it.id })
    }

    @Test
    fun limitsArticlesPerCategory() {
        val fresh = (0 until ArticleMerger.MAX_PER_CATEGORY + 20).map { article("f$it", now - it) } +
            article("tech", now - 10_000, NewsCategory.TECHNOLOGIES)
        val result = ArticleMerger.merge(emptyList(), fresh, now)
        assertEquals(ArticleMerger.MAX_PER_CATEGORY, result.articles.count { it.category == NewsCategory.FRANCE })
        assertTrue(result.articles.any { it.id == "tech" })
        assertEquals(ArticleMerger.MAX_PER_CATEGORY + 1, result.newCount)
    }
}

class RelativeTimeTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun computesRelativeBuckets() {
        assertEquals(RelativeTime.JustNow, RelativeTime.of(now - 30_000, now))
        assertEquals(RelativeTime.JustNow, RelativeTime.of(now + hour, now))
        assertEquals(RelativeTime.Minutes(5), RelativeTime.of(now - 5 * minute, now))
        assertEquals(RelativeTime.Hours(3), RelativeTime.of(now - 3 * hour - minute, now))
        assertEquals(RelativeTime.Yesterday, RelativeTime.of(now - 30 * hour, now))
        assertEquals(RelativeTime.Days(4), RelativeTime.of(now - 4 * day, now))
        assertEquals(RelativeTime.Date(now - 10 * day), RelativeTime.of(now - 10 * day, now))
    }
}
