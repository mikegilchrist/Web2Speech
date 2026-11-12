package org.web2speech

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup

data class Article(val url: String, val title: String, val text: String)

/** Fetches a URL and strips it to readable article text (title + body, no nav/ads/footers). */
object Extractor {

    private val urlRegex = Regex("https?://[^\\s<>\"']+")

    /** Fennec shares "Title\nURL" or just the URL; pull out the first URL. */
    fun findUrl(shared: String): String? =
        urlRegex.find(shared)?.value?.trimEnd('.', ',', ')', ';')

    fun fetch(url: String): Article {
        val html = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
            .timeout(20_000)
            .maxBodySize(8 * 1024 * 1024)
            .followRedirects(true)
            .get()
            .outerHtml()
        val art = Readability4J(url, html).parse()
        val title = art.title?.trim().orEmpty()
        var body = art.textContent?.trim().orEmpty()
        if (body.length < 200) {
            // Readability found little (index pages, odd markup): fall back to the whole body text.
            body = Jsoup.parse(html).body().text()
        }
        return Article(url, title.ifEmpty { url }, tidy(body))
    }

    /** Collapse runs of blank lines and strip leftover whitespace so paragraphs survive. */
    internal fun tidy(s: String): String =
        s.replace("\r", "")
            .replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("\\n[ ]+"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
}
