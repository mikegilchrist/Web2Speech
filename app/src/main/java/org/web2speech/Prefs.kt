package org.web2speech

import android.content.Context

/** User settings plus per-URL resume positions (chunk index). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("w2s", Context.MODE_PRIVATE)

    var rate: Float
        get() = sp.getFloat("rate", 1.0f)
        set(v) = sp.edit().putFloat("rate", v).apply()
    var pitch: Float
        get() = sp.getFloat("pitch", 1.0f)
        set(v) = sp.edit().putFloat("pitch", v).apply()
    var voice: String
        get() = sp.getString("voice", "") ?: ""
        set(v) = sp.edit().putString("voice", v).apply()
    var maxSentences: Int
        get() = sp.getInt("maxSentences", 3)
        set(v) = sp.edit().putInt("maxSentences", v.coerceIn(1, 20)).apply()
    var maxChars: Int
        get() = sp.getInt("maxChars", 600)
        set(v) = sp.edit().putInt("maxChars", v.coerceIn(100, 3900)).apply()
    var speakTitle: Boolean
        get() = sp.getBoolean("speakTitle", true)
        set(v) = sp.edit().putBoolean("speakTitle", v).apply()

    fun position(url: String): Int = sp.getInt("pos:" + url.hashCode(), 0)
    fun setPosition(url: String, i: Int) = sp.edit().putInt("pos:" + url.hashCode(), i).apply()
    fun clearPosition(url: String) = sp.edit().remove("pos:" + url.hashCode()).apply()
}
