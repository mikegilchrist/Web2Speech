package org.web2speech

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Receives the browser's Share action, hands the URL to [SpeechService], and vanishes. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Extractor.findUrl(shared)
        val svc = Intent(this, SpeechService::class.java).setAction(SpeechService.ACTION_ENQUEUE)
        when {
            // A page share: short title plus URL. Fetch the page.
            url != null && Extractor.isPageShare(shared) ->
                startForegroundService(svc.putExtra(SpeechService.EXTRA_URL, url))
            // Prose (e.g. a reply shared from the Claude app): read the text itself.
            shared.isNotBlank() ->
                startForegroundService(svc.putExtra(SpeechService.EXTRA_TEXT, shared))
            else -> Toast.makeText(this, "Nothing to read in shared text", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
