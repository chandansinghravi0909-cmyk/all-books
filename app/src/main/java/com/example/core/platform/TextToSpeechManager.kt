package com.example.core.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    @Volatile
    private var isReady: Boolean = false
    private var pendingText: String? = null
    private var pendingMessageId: String? = null

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _isAvailable = MutableStateFlow(true)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true
                    _isAvailable.value = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            if (_speakingMessageId.value == utteranceId) {
                                _speakingMessageId.value = null
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (_speakingMessageId.value == utteranceId) {
                                _speakingMessageId.value = null
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (_speakingMessageId.value == utteranceId) {
                                _speakingMessageId.value = null
                            }
                        }
                    })
                    val queued = pendingText
                    val queuedId = pendingMessageId
                    if (!queued.isNullOrBlank() && !queuedId.isNullOrBlank()) {
                        pendingText = null
                        pendingMessageId = null
                        speakNow(queuedId, queued)
                    }
                } else {
                    isReady = false
                    _isAvailable.value = false
                }
            }
        } catch (_: Exception) {
            isReady = false
            _isAvailable.value = false
        }
    }

    fun toggleSpeak(messageId: String, text: String) {
        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }
        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) return

        if (!isReady) {
            pendingMessageId = messageId
            pendingText = cleanText
            return
        }
        speakNow(messageId, cleanText)
    }

    private fun speakNow(messageId: String, cleanText: String) {
        val engine = tts ?: return
        val hasDevanagari = cleanText.any { it in '\u0900'..'\u097F' }
        val preferredLocale = if (hasDevanagari) Locale("hi", "IN") else Locale("en", "IN")
        val langResult = engine.setLanguage(preferredLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        _speakingMessageId.value = messageId
        engine.speak(cleanText.take(3800), TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        pendingText = null
        pendingMessageId = null
        runCatching { tts?.stop() }
        _speakingMessageId.value = null
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        isReady = false
    }

    private fun sanitizeForSpeech(raw: String): String =
        raw.replace(Regex("[#*_`~>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
