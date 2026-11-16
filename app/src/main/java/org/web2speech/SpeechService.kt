package org.web2speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * Foreground service: fetches queued URLs one at a time, chunks the text, and speaks it
 * through the system TextToSpeech engine, with notification controls and resume positions.
 */
class SpeechService : Service() {

    companion object {
        const val ACTION_ENQUEUE = "org.web2speech.ENQUEUE"
        const val ACTION_PLAYPAUSE = "org.web2speech.PLAYPAUSE"
        const val ACTION_NEXT = "org.web2speech.NEXT"
        const val ACTION_PREV = "org.web2speech.PREV"
        const val ACTION_STOP = "org.web2speech.STOP"
        const val ACTION_APPLY = "org.web2speech.APPLY"
        const val EXTRA_URL = "url"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val deferred = ArrayDeque<Intent>()

    private val waiting = ArrayDeque<String>()
    /** Shared prose waiting to be read, keyed by [Extractor.textKey]; queue entries beginning "text:" point here. */
    private val sharedText = HashMap<String, String>()
    private var loading = false
    private var current: Article? = null
    private var chunks: List<String> = emptyList()
    private var idx = 0
    private var playing = false
    private var epoch = 0

    private lateinit var audio: AudioManager
    private var focusReq: AudioFocusRequest? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        audio = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                tts?.setOnUtteranceProgressListener(listener)
                ttsReady = true
                while (deferred.isNotEmpty()) handle(deferred.removeFirst())
            } else {
                toast("Text-to-speech engine failed to start")
                shutdown()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground promptly after startForegroundService().
        startForeground(
            NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        if (intent != null) {
            if (ttsReady) handle(intent) else deferred.add(intent)
        }
        return START_NOT_STICKY
    }

    private fun handle(i: Intent) {
        when (i.action) {
            ACTION_ENQUEUE -> {
                i.getStringExtra(EXTRA_URL)?.let { enqueue(it) }
                i.getStringExtra(EXTRA_TEXT)?.let { t ->
                    val key = Extractor.textKey(t)
                    sharedText[key] = t
                    enqueue(key)
                }
            }
            ACTION_PLAYPAUSE -> if (playing) pause() else if (current != null) play()
            ACTION_NEXT -> skip(+1)
            ACTION_PREV -> skip(-1)
            ACTION_STOP -> shutdown()
            ACTION_APPLY -> if (playing) play()
        }
    }

    // ---- queue and loading -------------------------------------------------

    private fun enqueue(url: String) {
        if (current == null && !loading) {
            load(url)
        } else {
            waiting.add(url)
            toast("Queued (${waiting.size} waiting)")
        }
    }

    private fun load(url: String) {
        loading = true
        refreshNotification("Loading...", url)
        io.execute {
            try {
                val art = sharedText.remove(url)?.let { Extractor.fromText(it) } ?: Extractor.fetch(url)
                main.post { loading = false; begin(art) }
            } catch (e: Exception) {
                main.post {
                    loading = false
                    toast("Could not read page: ${e.message ?: e.javaClass.simpleName}")
                    advance()
                }
            }
        }
    }

    private fun begin(art: Article) {
        val cap = TextToSpeech.getMaxSpeechInputLength() - 100
        val body = if (prefs.speakTitle) art.title + ".\n\n" + art.text else art.text
        val list = Chunker.chunk(body, prefs.maxSentences, minOf(prefs.maxChars, cap))
        if (list.isEmpty()) {
            toast("Nothing readable found on that page")
            advance()
            return
        }
        current = art
        chunks = list
        idx = prefs.position(art.url).let { if (it in 1 until list.size) it else 0 }
        play()
    }

    private fun advance() {
        current = null
        chunks = emptyList()
        playing = false
        val next = waiting.removeFirstOrNull()
        if (next != null) load(next) else shutdown()
    }

    // ---- playback ------------------------------------------------------------

    private fun play() {
        val t = tts ?: return
        if (!requestFocus()) { toast("Audio focus denied"); return }
        t.setSpeechRate(prefs.rate)
        t.setPitch(prefs.pitch)
        if (prefs.voice.isNotEmpty()) {
            t.voices?.firstOrNull { it.name == prefs.voice }?.let { t.voice = it }
        }
        epoch++
        t.stop()
        playing = true
        for (i in idx until chunks.size) {
            t.speak(chunks[i], TextToSpeech.QUEUE_ADD, null, "$epoch:$i")
        }
        refreshNotification()
    }

    private fun pause() {
        epoch++
        tts?.stop()
        playing = false
        abandonFocus()
        refreshNotification()
    }

    private fun skip(d: Int) {
        if (current == null) return
        val n = idx + d
        if (n >= chunks.size) { finishCurrent(); return }
        idx = n.coerceAtLeast(0)
        current?.let { prefs.setPosition(it.url, idx) }
        if (playing) play() else refreshNotification()
    }

    private fun finishCurrent() {
        epoch++
        tts?.stop()
        current?.let { prefs.clearPosition(it.url) }
        abandonFocus()
        advance()
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String) {
            val (e, i) = parse(id) ?: return
            if (e != epoch) return
            idx = i
            main.post {
                current?.let { prefs.setPosition(it.url, i) }
                refreshNotification()
            }
        }

        override fun onDone(id: String) {
            val (e, i) = parse(id) ?: return
            if (e == epoch && i == chunks.size - 1) main.post { if (e == epoch) finishCurrent() }
        }

        @Deprecated("required override")
        override fun onError(id: String) = onDone(id)
    }

    private fun parse(id: String): Pair<Int, Int>? {
        val p = id.split(":")
        return if (p.size == 2) Pair(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null) else null
    }

    // ---- audio focus ----------------------------------------------------------

    private fun requestFocus(): Boolean {
        val req = focusReq ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) main.post { if (playing) pause() }
            }.build().also { focusReq = it }
        return audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() { focusReq?.let { audio.abandonAudioFocusRequest(it) } }

    // ---- notification ----------------------------------------------------------

    private fun action(act: String, icon: Int, label: String, req: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, req, Intent(this, SpeechService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon), label, pi
        ).build()
    }

    private fun notification(title: String? = null, text: String? = null): Notification {
        val t = title ?: current?.title ?: "Web2Speech"
        val s = text ?: if (chunks.isNotEmpty()) "Part ${idx + 1} of ${chunks.size}" +
            (if (waiting.isNotEmpty()) "  (+${waiting.size} queued)" else "") else ""
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(t)
            .setContentText(s)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(action(ACTION_PREV, android.R.drawable.ic_media_previous, "Back", 1))
            .addAction(
                if (playing) action(ACTION_PLAYPAUSE, android.R.drawable.ic_media_pause, "Pause", 2)
                else action(ACTION_PLAYPAUSE, android.R.drawable.ic_media_play, "Play", 2)
            )
            .addAction(action(ACTION_NEXT, android.R.drawable.ic_media_next, "Next", 3))
            .addAction(action(ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel, "Stop", 4))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun refreshNotification(title: String? = null, text: String? = null) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(title, text))
    }

    private fun toast(m: String) = main.post { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }

    private fun shutdown() {
        epoch++
        tts?.stop()
        abandonFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tts?.shutdown()
        io.shutdownNow()
        super.onDestroy()
    }
}
