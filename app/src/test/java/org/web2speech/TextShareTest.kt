package org.web2speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextShareTest {

    @Test fun titlePlusUrlIsAPageShare() {
        assertTrue(Extractor.isPageShare("Some headline\nhttps://example.com/story"))
        assertTrue(Extractor.isPageShare("https://example.com/story"))
    }

    @Test fun longProseWithALinkIsNotAPageShare() {
        val prose = "This reply explains the plan in several sentences. ".repeat(10) + "See https://example.com/x for more."
        assertFalse(Extractor.isPageShare(prose))
    }

    @Test fun plainProseWithoutUrlIsNotAPageShare() {
        assertFalse(Extractor.isPageShare("Just some words to read aloud."))
    }

    @Test fun markdownIsStrippedForSpeech() {
        val a = Extractor.fromText("## Plan\n\n- **First** step, see [the docs](https://x.org/d).\n\n```\nrm -rf /\n```\nDone.")
        assertTrue(a.text.contains("Plan"))
        assertTrue(a.text.contains("First step, see the docs."))
        assertTrue(a.text.contains("code block omitted"))
        assertFalse(a.text.contains("*"))
        assertFalse(a.text.contains("https"))
        assertFalse(a.text.contains("rm -rf"))
    }

    @Test fun titleIsFirstLineAndKeyIsStable() {
        val a = Extractor.fromText("Hello there.\n\nSecond paragraph.")
        assertEquals("Hello there.", a.title)
        assertEquals(a.url, Extractor.fromText("Hello there.\n\nSecond paragraph.").url)
        assertTrue(a.url.startsWith("text:"))
    }
}
