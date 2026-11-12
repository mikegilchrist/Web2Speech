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
        if (url == null) {
            Toast.makeText(this, "No web address found in shared text", Toast.LENGTH_LONG).show()
        } else {
            startForegroundService(
                Intent(this, SpeechService::class.java)
                    .setAction(SpeechService.ACTION_ENQUEUE)
                    .putExtra(SpeechService.EXTRA_URL, url)
            )
        }
        finish()
    }
}
