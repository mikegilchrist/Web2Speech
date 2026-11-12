package org.web2speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {

    @Test fun packsThreeSentences() {
        val t = "One. Two. Three. Four. Five."
        val c = Chunker.chunk(t, maxSentences = 3, maxChars = 600)
        assertEquals(listOf("One. Two. Three.", "Four. Five."), c)
    }

    @Test fun neverCrossesParagraphs() {
        val t = "Alpha one. Alpha two.\n\nBeta one."
        assertEquals(listOf("Alpha one. Alpha two.", "Beta one."), Chunker.chunk(t))
    }

    @Test fun respectsCharCap() {
        val s = "word ".repeat(30).trim() + "."
        val c = Chunker.chunk("$s $s $s", maxSentences = 3, maxChars = 200)
        assertTrue(c.all { it.length <= 200 })
    }

    @Test fun splitsOverlongSentence() {
        val s = "word ".repeat(100).trim() + "."
        val c = Chunker.chunk(s, maxSentences = 3, maxChars = 120)
        assertTrue(c.size > 1)
        assertTrue(c.all { it.length <= 120 })
    }

    @Test fun findsUrlInShare() {
        assertEquals("https://example.org/a?b=1", Extractor.findUrl("Title here\nhttps://example.org/a?b=1"))
    }
}
