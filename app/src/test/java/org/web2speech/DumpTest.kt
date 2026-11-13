package org.web2speech

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Dev aid: dumps extracted text for a saved page. Skipped unless W2S_HTML is set. */
class DumpTest {
    @Test fun dump() {
        val src = System.getenv("W2S_HTML")
        assumeTrue(src != null && File(src).exists())
        val art = Extractor.parse(System.getenv("W2S_URL") ?: "https://example.org/", File(src!!).readText())
        val chunks = Chunker.chunk(art.title + ".\n\n" + art.text)
        File(System.getenv("W2S_OUT") ?: "/tmp/w2s-dump.txt").writeText(
            "TITLE: ${art.title}\nCHARS: ${art.text.length}  CHUNKS: ${chunks.size}\n\n" +
                chunks.mapIndexed { i, c -> "[${i + 1}] $c" }.joinToString("\n\n")
        )
    }
}
