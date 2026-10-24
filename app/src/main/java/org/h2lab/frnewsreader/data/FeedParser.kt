package org.h2lab.frnewsreader.data

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class FeedItem(
    val title: String,
    val link: String,
    val summary: String,
    val imageUrl: String?,
    val publishedAt: Long?,
    val categories: List<String>,
)

/** Parses RSS 2.0, RSS 1.0 (RDF) and Atom feeds. */
object FeedParser {
    private const val RELAXED_FEATURE = "http://xmlpull.org/v1/doc/features.html#relaxed"
    private val imgTag = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val imageExtension = Regex("\\.(jpe?g|png|webp|gif)(\\?.*)?$", RegexOption.IGNORE_CASE)

    fun parse(input: InputStream): List<FeedItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        runCatching { parser.setFeature(RELAXED_FEATURE, true) }
        parser.setInput(input, null)

        val items = mutableListOf<FeedItem>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase()
                if (name == "item" || name == "entry") readItem(parser, isAtom = name == "entry")?.let(items::add)
            }
            event = parser.next()
        }
        return items
    }

    private fun readItem(parser: XmlPullParser, isAtom: Boolean): FeedItem? {
        val depth = parser.depth
        var title = ""
        var link = ""
        var guid = ""
        var description = ""
        var content = ""
        var published: String? = null
        var updated: String? = null
        var image: String? = null
        var imagePriority = Int.MAX_VALUE
        val categories = mutableListOf<String>()

        fun offerImage(url: String?, priority: Int) {
            if (!url.isNullOrBlank() && priority < imagePriority) {
                image = url.trim()
                imagePriority = priority
            }
        }

        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event != XmlPullParser.START_TAG) continue

            when (parser.name.lowercase()) {
                "title" -> title = readText(parser)
                "link" -> {
                    val href = parser.getAttributeValue(null, "href")
                    if (href != null) {
                        val rel = parser.getAttributeValue(null, "rel")
                        if ((rel == null || rel == "alternate") && link.isBlank()) link = href
                        skip(parser)
                    } else {
                        readText(parser).takeIf { it.isNotBlank() }?.let { link = it }
                    }
                }
                "guid", "id" -> guid = readText(parser)
                "description", "summary" -> description = readText(parser)
                "content:encoded" -> content = readText(parser)
                "content" -> if (isAtom) content = readText(parser) else skip(parser)
                "pubdate", "published", "dc:date" -> published = readText(parser)
                "updated" -> updated = readText(parser)
                "category" -> {
                    val term = parser.getAttributeValue(null, "term")
                    val text = readText(parser)
                    (term ?: text).takeIf { it.isNotBlank() }?.let(categories::add)
                }
                "dc:subject" -> readText(parser).takeIf { it.isNotBlank() }?.let(categories::add)
                "media:content" -> {
                    val medium = parser.getAttributeValue(null, "medium")
                    val type = parser.getAttributeValue(null, "type")
                    val isImage = medium == "image" || type?.startsWith("image/") == true ||
                        (medium == null && type == null)
                    if (isImage) offerImage(parser.getAttributeValue(null, "url"), 0)
                    skip(parser)
                }
                "media:thumbnail" -> {
                    offerImage(parser.getAttributeValue(null, "url"), 1)
                    skip(parser)
                }
                "enclosure" -> {
                    val url = parser.getAttributeValue(null, "url")
                    val type = parser.getAttributeValue(null, "type")
                    if (type?.startsWith("image/") == true || (type == null && url != null && imageExtension.containsMatchIn(url))) {
                        offerImage(url, 0)
                    }
                    skip(parser)
                }
                "media:group" -> Unit
                else -> skip(parser)
            }
        }

        if (link.isBlank() && guid.startsWith("http")) link = guid
        val cleanTitle = htmlToText(title)
        if (cleanTitle.isBlank() || link.isBlank()) return null
        if (image == null) {
            offerImage(imgTag.find(content)?.groupValues?.get(1) ?: imgTag.find(description)?.groupValues?.get(1), 2)
        }
        return FeedItem(
            title = cleanTitle,
            link = link.trim(),
            summary = TextCleaner.cleanSummary(htmlToText(description.ifBlank { content })),
            imageUrl = image?.replace("&amp;", "&"),
            publishedAt = DateParser.parse(published) ?: DateParser.parse(updated),
            categories = categories.map(::htmlToText),
        )
    }

    private fun htmlToText(text: String): String {
        if (text.isBlank()) return ""
        if ('<' !in text && '&' !in text) return text.trim()
        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replace('\uFFFC', ' ').trim()
    }

    /** Reads the text of the current element, ignoring any nested markup. */
    private fun readText(parser: XmlPullParser): String {
        val depth = parser.depth
        val builder = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> builder.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.depth == depth) return builder.toString().trim()
                XmlPullParser.END_DOCUMENT -> return builder.toString().trim()
            }
        }
    }

    private fun skip(parser: XmlPullParser) {
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.depth == depth) return
        }
    }
}
