package org.web2speech

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorTest {

    private fun blocks(html: String) = Extractor.blocksOf(Jsoup.parse("<div id=r>$html</div>").getElementById("r")!!)

    @Test fun dropsFigureAndCaption() {
        val t = blocks("<p>Real text one.</p><figure><img src=x><figcaption>An oil refinery.</figcaption></figure><p>Real text two.</p>")
        assertEquals("Real text one.\n\nReal text two.", t)
    }

    @Test fun dropsLooseCaptionAndCreditParagraphs() {
        val t = blocks("<p>Body.</p><p>Photograph: VW Pics/Universal Images Group/Getty Images</p>" +
            "<p>Protesters march in Chicago, 15 September 2023. Kamil Krzaczynski/AFP via Getty Images</p><p>More body.</p>")
        assertEquals("Body.\n\nMore body.", t)
    }

    @Test fun dropsRelatedAndLinkOnlyBlocks() {
        val t = blocks("<p>Body.</p><p>Related: <a href=#>Other story about oil</a></p>" +
            "<p><a href=#>Read the full report here</a></p><aside><p>Sidebar text.</p></aside>")
        assertEquals("Body.", t)
    }

    @Test fun keepsInlineLinkText() {
        val t = blocks("<p>A <a href=#>coalition of the willing</a> met in April and agreed a plan to phase out fuels.</p>")
        assertTrue(t.contains("coalition of the willing"))
    }

    @Test fun keepsParagraphsSeparateAndPunctuatesHeadings() {
        val t = blocks("<h2>Why it matters</h2><p>First.</p><p>Second.</p>")
        assertEquals("Why it matters.\n\nFirst.\n\nSecond.", t)
    }

    @Test fun doesNotRemoveArticleWrappedInShareClass() {
        val t = blocks("<div class='share-wrap'><p>One.</p><p>Two.</p><p>Three.</p></div>")
        assertFalse(t.isEmpty())
    }

    @Test fun trimsTitleSuffix() {
        assertEquals("California governor Gavin Newsom endorses global fossil fuel phaseout",
            Extractor.cleanTitle("California governor Gavin Newsom endorses global fossil fuel phaseout | Cop31"))
        assertEquals("A | B", Extractor.cleanTitle("A | B"))
    }
}
