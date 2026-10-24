package org.h2lab.frnewsreader.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
class FeedParserTest {
    private fun parse(xml: String) = FeedParser.parse(xml.byteInputStream())
    private fun epoch(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun parsesRssWithNestedMediaContent() {
        val items = parse(
            """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel><title>Le Monde</title><link>https://www.lemonde.fr</link>
                <image><url>https://www.lemonde.fr/logo.png</url><title>Logo</title></image>
                <item>
                  <title><![CDATA[Argentine : non-lieu pour Javier Milei]]></title>
                  <pubDate>Sat, 24 Oct 2026 01:44:16 +0200</pubDate>
                  <description><![CDATA[Le dirigeant <b>ultralibéral</b> avait relayé le lancement.]]></description>
                  <link>https://www.lemonde.fr/international/article/2026/10/24/a_3210.html</link>
                  <media:content width="644" url="https://img.lemde.fr/a.jpg"><media:description type="plain">Légende</media:description></media:content>
                </item>
              </channel>
            </rss>""",
        )
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("Argentine : non-lieu pour Javier Milei", item.title)
        assertEquals("https://www.lemonde.fr/international/article/2026/10/24/a_3210.html", item.link)
        assertEquals("Le dirigeant ultralibéral avait relayé le lancement.", item.summary)
        assertEquals("https://img.lemde.fr/a.jpg", item.imageUrl)
        assertEquals(epoch("2026-10-24T01:44:16+02:00"), item.publishedAt)
    }

    @Test
    fun parsesCdataLinksEnclosuresCategoriesAndHtmlEntities() {
        val items = parse(
            """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <item>
                <enclosure url="https://images.marianne.net/a.jpg" type="image/jpeg"/>
                <pubDate>Sat, 24 Oct 2026 06:00:11 +0200</pubDate>
                <title>"Les Bolsonaro&nbsp;utilisent leur famille"</title>
                <link><![CDATA[https://www.marianne.net/monde/ameriques/x#utm_source=RSS]]></link>
                <description><![CDATA[<p class="first-paragraph">Texte &amp; analyse</p>]]></description>
                <category>Monde</category>
              </item>
              <item><title>Sans lien</title></item>
            </channel></rss>""",
        )
        val item = items.single()
        assertEquals("https://www.marianne.net/monde/ameriques/x#utm_source=RSS", item.link)
        assertEquals("https://images.marianne.net/a.jpg", item.imageUrl)
        assertEquals(listOf("Monde"), item.categories)
        assertEquals("Texte & analyse", item.summary)
    }

    @Test
    fun parsesDublinCoreSubjectsDatesAndImagesFromContent() {
        val item = parse(
            """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel>
              <item>
                <title>Le Br&#233;sil n'a pas dit son dernier mot</title>
                <link>https://www.monde-diplomatique.fr/carnet/2026-10-23-Bresil</link>
                <dc:subject>Politique</dc:subject><dc:subject>Br&#233;sil</dc:subject>
                <dc:date>2026-10-23T16:34:55Z</dc:date>
                <description>Les deux (&#8230;) / &lt;a href="https://x"&gt;Politique&lt;/a&gt;</description>
                <content:encoded><![CDATA[<p><img src="https://www.monde-diplomatique.fr/img.jpg" /></p>]]></content:encoded>
              </item>
            </channel></rss>""",
        ).single()
        assertEquals("Le Brésil n'a pas dit son dernier mot", item.title)
        assertEquals(listOf("Politique", "Brésil"), item.categories)
        assertEquals(epoch("2026-10-23T16:34:55Z"), item.publishedAt)
        assertEquals("Les deux (…)", item.summary)
        assertEquals("https://www.monde-diplomatique.fr/img.jpg", item.imageUrl)
    }

    @Test
    fun parsesAtomEntries() {
        val item = parse(
            """<?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Flux</title>
              <entry>
                <title type="html">IA &amp; société</title>
                <link rel="enclosure" href="https://example.com/audio.mp3"/>
                <link rel="alternate" href="https://example.com/article"/>
                <id>tag:example.com,2026:1</id>
                <updated>2026-10-24T08:00:00+02:00</updated>
                <published>2026-10-24T07:00:00+02:00</published>
                <category term="Numérique"/>
                <summary>Résumé</summary>
              </entry>
            </feed>""",
        ).single()
        assertEquals("IA & société", item.title)
        assertEquals("https://example.com/article", item.link)
        assertEquals(epoch("2026-10-24T07:00:00+02:00"), item.publishedAt)
        assertEquals(listOf("Numérique"), item.categories)
        assertNull(item.imageUrl)
    }
}
