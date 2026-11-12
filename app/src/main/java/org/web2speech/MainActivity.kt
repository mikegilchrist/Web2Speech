package org.web2speech

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

/** Settings and manual controls. Reading is normally started from the browser's Share menu. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private var voiceProbe: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, 24, 0, 4) }
        fun send(a: String) = startForegroundService(Intent(this, SpeechService::class.java).setAction(a))

        root.addView(TextView(this).apply {
            text = "Web2Speech\nShare a page from your browser and choose Web2Speech."
        })

        // Manual URL entry
        val urlBox = EditText(this).apply { hint = "https://..."; inputType = InputType.TYPE_TEXT_VARIATION_URI }
        root.addView(label("Read a web address"))
        root.addView(urlBox)
        root.addView(Button(this).apply {
            text = "Read this page"
            setOnClickListener {
                Extractor.findUrl(urlBox.text.toString())?.let { u ->
                    startForegroundService(
                        Intent(this@MainActivity, SpeechService::class.java)
                            .setAction(SpeechService.ACTION_ENQUEUE).putExtra(SpeechService.EXTRA_URL, u)
                    )
                }
            }
        })

        // Transport
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(t: String, a: String) = Button(this).apply {
            text = t; setOnClickListener { send(a) }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(btn("Back", SpeechService.ACTION_PREV))
        row.addView(btn("Play/Pause", SpeechService.ACTION_PLAYPAUSE))
        row.addView(btn("Next", SpeechService.ACTION_NEXT))
        row.addView(btn("Stop", SpeechService.ACTION_STOP))
        root.addView(label("Controls"))
        root.addView(row)

        // Speed (0.5 .. 3.0) and pitch (0.5 .. 2.0)
        val rateLbl = label("")
        val rateBar = SeekBar(this).apply { max = 25; progress = ((prefs.rate - 0.5f) * 10).toInt() }
        fun showRate() { rateLbl.text = "Speed: %.1fx".format(0.5f + rateBar.progress / 10f) }
        showRate()
        rateBar.setOnSeekBarChangeListener(seek { prefs.rate = 0.5f + it / 10f; showRate() })
        root.addView(rateLbl); root.addView(rateBar)

        val pitchLbl = label("")
        val pitchBar = SeekBar(this).apply { max = 15; progress = ((prefs.pitch - 0.5f) * 10).toInt() }
        fun showPitch() { pitchLbl.text = "Pitch: %.1f".format(0.5f + pitchBar.progress / 10f) }
        showPitch()
        pitchBar.setOnSeekBarChangeListener(seek { prefs.pitch = 0.5f + it / 10f; showPitch() })
        root.addView(pitchLbl); root.addView(pitchBar)

        // Chunking
        root.addView(label("Sentences per chunk (1-20)"))
        root.addView(numberBox(prefs.maxSentences) { prefs.maxSentences = it })
        root.addView(label("Max characters per chunk (100-3900)"))
        root.addView(numberBox(prefs.maxChars) { prefs.maxChars = it })
        root.addView(CheckBox(this).apply {
            text = "Speak the page title first"; isChecked = prefs.speakTitle
            setOnCheckedChangeListener { _, c -> prefs.speakTitle = c }
        })

        // Voice picker (needs a throwaway TTS instance to list voices)
        val voiceSpin = Spinner(this)
        root.addView(label("Voice"))
        root.addView(voiceSpin)
        voiceProbe = TextToSpeech(this) { st ->
            if (st != TextToSpeech.SUCCESS) return@TextToSpeech
            val vs = voiceProbe?.voices?.sortedBy { it.name } ?: return@TextToSpeech
            val names = listOf("(engine default)") + vs.map { it.name }
            runOnUiThread {
                voiceSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                voiceSpin.setSelection(names.indexOf(prefs.voice).coerceAtLeast(0), false)
                voiceSpin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        prefs.voice = if (pos == 0) "" else names[pos]
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            }
        }

        root.addView(TextView(this).apply {
            text = "\nSettings apply when playback next starts, or press Play/Pause twice."
            setPadding(0, 16, 0, 0)
        })
    }

    private fun seek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar) {}
        override fun onStopTrackingTouch(s: SeekBar) {
            startForegroundService(Intent(this@MainActivity, SpeechService::class.java).setAction(SpeechService.ACTION_APPLY))
        }
    }

    private fun numberBox(initial: Int, set: (Int) -> Unit) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(initial.toString())
        setOnFocusChangeListener { _, focused ->
            if (!focused) text.toString().toIntOrNull()?.let { set(it) }
        }
    }

    override fun onDestroy() {
        voiceProbe?.shutdown()
        super.onDestroy()
    }
}
