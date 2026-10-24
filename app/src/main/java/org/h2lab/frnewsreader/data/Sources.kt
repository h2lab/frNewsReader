package org.h2lab.frnewsreader.data

import org.h2lab.frnewsreader.data.NewsCategory.FRANCE
import org.h2lab.frnewsreader.data.NewsCategory.INTERNATIONAL
import org.h2lab.frnewsreader.data.NewsCategory.TECHNOLOGIES

/**
 * Associates article tags (RSS categories or URL path segments) with a tab.
 * A null [tag] excludes matching articles.
 */
class TagRule(val tag: NewsCategory?, keywords: Collection<String>) {
    val keywords: Set<String> = keywords.map(TextNormalizer::normalize).toSet()
}

data class SourceFeed(
    val url: String,
    val defaultTag: NewsCategory?,
    val rules: List<TagRule> = emptyList(),
)

data class NewsSource(
    val id: String,
    val name: String,
    val color: Long,
    val feeds: List<SourceFeed>,
)

private fun rule(tag: NewsCategory?, vararg keywords: String) = TagRule(tag, keywords.toList())

object SourceCatalog {
    val sources: List<NewsSource> = listOf(
        NewsSource(
            id = "lemonde", name = "Le Monde", color = 0xFF1A1A1A,
            feeds = listOf(
                SourceFeed("https://www.lemonde.fr/politique/rss_full.xml", FRANCE),
                SourceFeed("https://www.lemonde.fr/societe/rss_full.xml", FRANCE),
                SourceFeed("https://www.lemonde.fr/international/rss_full.xml", INTERNATIONAL),
                SourceFeed("https://www.lemonde.fr/pixels/rss_full.xml", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "marianne", name = "Marianne", color = 0xFFE30613,
            feeds = listOf(
                SourceFeed(
                    "https://www.marianne.net/rss.xml",
                    defaultTag = null,
                    rules = listOf(
                        rule(TECHNOLOGIES, "big-brother", "numerique", "high-tech"),
                        rule(INTERNATIONAL, "monde"),
                        rule(FRANCE, "politique", "societe", "economie"),
                    ),
                ),
            ),
        ),
        NewsSource(
            id = "lesechos", name = "Les Échos", color = 0xFFD7282F,
            feeds = listOf(
                SourceFeed(
                    "https://syndication.lesechos.fr/rss/rss_une.xml",
                    defaultTag = null,
                    rules = listOf(
                        rule(TECHNOLOGIES, "tech-medias"),
                        rule(INTERNATIONAL, "monde"),
                        rule(FRANCE, "politique-societe", "economie-france", "regions"),
                    ),
                ),
            ),
        ),
        NewsSource(
            id = "lefigaro", name = "Le Figaro", color = 0xFF163860,
            feeds = listOf(
                SourceFeed("https://www.lefigaro.fr/rss/figaro_actualite-france.xml", FRANCE),
                SourceFeed("https://www.lefigaro.fr/rss/figaro_politique.xml", FRANCE),
                SourceFeed("https://www.lefigaro.fr/rss/figaro_international.xml", INTERNATIONAL),
                SourceFeed("https://www.lefigaro.fr/rss/figaro_secteur_high-tech.xml", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "liberation", name = "Libération", color = 0xFFE60000,
            feeds = listOf(
                SourceFeed("https://www.liberation.fr/arc/outboundfeeds/rss-all/category/politique/?outputType=xml", FRANCE),
                SourceFeed("https://www.liberation.fr/arc/outboundfeeds/rss-all/category/societe/?outputType=xml", FRANCE),
                SourceFeed("https://www.liberation.fr/arc/outboundfeeds/rss-all/category/international/?outputType=xml", INTERNATIONAL),
                SourceFeed("https://www.liberation.fr/arc/outboundfeeds/rss-all/category/economie/economie-numerique/?outputType=xml", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "legrandcontinent", name = "Le Grand Continent", color = 0xFF2B2B2B,
            feeds = listOf(
                SourceFeed(
                    "https://legrandcontinent.eu/fr/feed/",
                    defaultTag = INTERNATIONAL,
                    rules = listOf(
                        rule(TECHNOLOGIES, "numerique", "puissances de l'ia", "intelligence artificielle", "technologie", "cyber"),
                    ),
                ),
            ),
        ),
        NewsSource(
            id = "mondediplo", name = "Le Monde diplomatique", color = 0xFF8B1A1A,
            feeds = listOf(
                SourceFeed(
                    "https://www.monde-diplomatique.fr/rss/",
                    defaultTag = INTERNATIONAL,
                    rules = listOf(
                        rule(TECHNOLOGIES, "internet", "numerique", "informatique", "technologie", "intelligence artificielle", "reseaux sociaux"),
                        rule(FRANCE, "france"),
                    ),
                ),
            ),
        ),
        NewsSource(
            id = "euronews", name = "Euronews", color = 0xFF0050A0,
            feeds = listOf(
                SourceFeed("https://fr.euronews.com/rss?format=mrss&level=theme&name=news", INTERNATIONAL),
                SourceFeed("https://fr.euronews.com/rss?format=mrss&level=vertical&name=next", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "franceinfo", name = "franceinfo", color = 0xFFF5A623,
            feeds = listOf(
                SourceFeed("https://www.francetvinfo.fr/france.rss", FRANCE),
                SourceFeed("https://www.francetvinfo.fr/monde.rss", INTERNATIONAL),
                SourceFeed("https://www.francetvinfo.fr/internet.rss", TECHNOLOGIES),
                SourceFeed("https://www.francetvinfo.fr/sciences/high-tech.rss", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "cyberattaque", name = "Cyberattaque.org", color = 0xFF164E63,
            feeds = listOf(
                SourceFeed("https://www.cyberattaque.org/feed/", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "thehackernews", name = "The Hacker News", color = 0xFF164579,
            feeds = listOf(
                SourceFeed("https://feeds.feedburner.com/TheHackersNews", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "bleepingcomputer", name = "BleepingComputer", color = 0xFF165D9C,
            feeds = listOf(
                SourceFeed("https://www.bleepingcomputer.com/feed/", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "korben", name = "Korben", color = 0xFF9A3412,
            feeds = listOf(
                SourceFeed("https://korben.info/feed", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "zataz", name = "ZATAZ", color = 0xFF991B1B,
            feeds = listOf(
                SourceFeed("https://www.zataz.com/feed/", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            id = "lemagit", name = "LeMagIT", color = 0xFF315D25,
            feeds = listOf(
                SourceFeed("https://www.lemagit.fr/rss/ContentSyndication.xml", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            // cert.ssi.gouv.fr redirects to the current CERT-FR domain.
            id = "certfr", name = "CERT-FR", color = 0xFF000091,
            feeds = listOf(
                SourceFeed("https://cert.cyber.gouv.fr/feed/", TECHNOLOGIES),
            ),
        ),
        NewsSource(
            // Next INpact (nextinpact.com, listed as nextimpact.com) is now Next.
            id = "next", name = "Next", color = 0xFF6B21A8,
            feeds = listOf(
                SourceFeed("https://next.ink/feed/", TECHNOLOGIES),
            ),
        ),
    )
}
