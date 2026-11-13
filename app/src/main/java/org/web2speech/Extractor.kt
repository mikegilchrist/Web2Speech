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
        return parse(url, html)
    }

    /** Pure function of (url, html): separated from [fetch] so it can be tested on saved pages. */
    fun parse(url: String, html: String): Article {
        val art = Readability4J(url, html).parse()
        val title = cleanTitle(art.title.orEmpty())
        var body = art.articleContent?.let { blocksOf(it) }.orEmpty()
        if (body.length < 200) {
            // Readability found little (index pages, odd markup): fall back to the whole body text.
            body = Jsoup.parse(html).body().text()
        }
        return Article(url, title.ifEmpty { url }, tidy(body))
    }

    // ---- cleaning ------------------------------------------------------------

    /** Elements that are never part of the spoken article. */
    private const val DROP = "script,style,noscript,iframe,svg,picture,video,audio,figure,figcaption," +
        "aside,nav,footer,form,button,select,[role=complementary],[aria-hidden=true]," +
        "[data-component=rich-link],[data-component=auto-linked-tag],[data-link-name*=related]"

    /** Class/id words that mark furniture. Only applied to small elements (see [blocksOf]). */
    private val JUNK_CLASS = Regex(
        "(^|[\\s_-])(related|rich-link|newsletter|promo|advert|sponsor|share|social|caption|" +
            "credit|recommended|comments|toolbar|byline-extras)([\\s_-]|$)", RegexOption.IGNORE_CASE
    )

    /** A short block that starts like a caption, credit line, or pointer to other content. */
    private val JUNK_START = Regex(
        "^(related|photograph|photo|photos|image|illustration|composite|credit|source|" +
            "read more|sign up|advertisement|sponsored)\\s*:", RegexOption.IGNORE_CASE
    )
    private val CREDIT_END = Regex(
        "(getty images|afp|reuters|associated press|epa|shutterstock|alamy|ap photo)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val NO_TERMINAL = Regex("[.!?:;\"'\\u201D\\u2019)\\]]$")

    /** Walk the Readability article and emit one paragraph per block, minus furniture. */
    internal fun blocksOf(root: org.jsoup.nodes.Element): String {
        val el = root.clone()
        el.select(DROP).remove()
        for (e in el.select("*")) {
            if (e.parent() == null) continue
            val cls = e.className() + " " + e.id()
            if (cls.isNotBlank() && JUNK_CLASS.containsMatchIn(cls) && e.select("p").size < 3) e.remove()
        }
        val out = ArrayList<String>()
        for (b in el.select("p, h1, h2, h3, h4, h5, h6, li:not(:has(p)), pre")) {
            val t = b.text().replace(' ', ' ').trim()
            if (t.isEmpty()) continue
            if (t.length < 250 && linkShare(b, t) > 0.6) continue          // link lists, "Read more"
            if (t.length < 250 && JUNK_START.containsMatchIn(t)) continue  // captions, credits, related
            if (t.length < 200 && CREDIT_END.containsMatchIn(t)) continue  // "... / Getty Images"
            val heading = b.tagName().startsWith("h")
            out.add(if (heading && !NO_TERMINAL.containsMatchIn(t)) "$t." else t)
        }
        return out.joinToString("\n\n")
    }

    private fun linkShare(b: org.jsoup.nodes.Element, t: String): Double =
        b.select("a").text().length.toDouble() / t.length

    /** Drop a trailing " | Section" or " | Site" segment from the page title. */
    internal fun cleanTitle(t: String): String {
        val s = t.trim()
        val i = s.lastIndexOf(" | ")
        return if (i >= 15) s.substring(0, i).trim() else s
    }

    /** Collapse runs of blank lines and strip leftover whitespace so paragraphs survive. */
    internal fun tidy(s: String): String =
        s.replace("\r", "")
            .replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("\\n[ ]+"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
}
