package org.web2speech

import java.text.BreakIterator
import java.util.Locale

/**
 * Packs article text into speakable chunks.
 *
 * Rules: a chunk holds whole sentences only, at most [maxSentences] sentences and at most
 * [maxChars] characters, and never crosses a paragraph break. A single sentence longer than
 * the limit is split at the last space before the limit. [maxChars] is clamped to the
 * engine's own cap (TextToSpeech.getMaxSpeechInputLength) by the caller.
 */
object Chunker {

    fun chunk(text: String, maxSentences: Int = 3, maxChars: Int = 600): List<String> {
        val out = ArrayList<String>()
        val paragraphs = text.split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
        for (p in paragraphs) {
            val para = p.replace(Regex("\\s+"), " ").trim()
            if (para.isEmpty()) continue
            packParagraph(sentences(para), maxSentences, maxChars, out)
        }
        return out
    }

    internal fun sentences(para: String): List<String> {
        val bi = BreakIterator.getSentenceInstance(Locale.US)
        bi.setText(para)
        val res = ArrayList<String>()
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            val s = para.substring(start, end).trim()
            if (s.isNotEmpty()) res.add(s)
            start = end
            end = bi.next()
        }
        return res
    }

    private fun packParagraph(sents: List<String>, maxSent: Int, maxChars: Int, out: MutableList<String>) {
        val cur = StringBuilder()
        var n = 0
        fun flush() {
            if (cur.isNotEmpty()) out.add(cur.toString())
            cur.setLength(0)
            n = 0
        }
        for (s0 in sents) {
            for (s in splitLong(s0, maxChars)) {
                val extra = if (cur.isEmpty()) s.length else s.length + 1
                if (n >= maxSent || cur.length + extra > maxChars) flush()
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(s)
                n++
            }
        }
        flush()
    }

    private fun splitLong(s: String, maxChars: Int): List<String> {
        if (s.length <= maxChars) return listOf(s)
        val parts = ArrayList<String>()
        var rest = s
        while (rest.length > maxChars) {
            var cut = rest.lastIndexOf(' ', maxChars)
            if (cut <= 0) cut = maxChars
            parts.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts.add(rest)
        return parts
    }
}
