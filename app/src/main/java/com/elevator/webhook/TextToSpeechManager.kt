package com.elevator.webhook

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                isReady = true
            }
        }
    }

    fun speak(text: String) {
        if (isReady && tts != null) {
            tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
