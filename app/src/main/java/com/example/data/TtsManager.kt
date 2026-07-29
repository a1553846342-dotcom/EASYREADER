package com.example.data

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentParagraphIndex = MutableStateFlow(0)
    val currentParagraphIndex: StateFlow<Int> = _currentParagraphIndex

    private var paragraphs = listOf<String>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINESE
            isReady = true
        }
    }

    fun startReading(content: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        if (!isReady) return
        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)

        paragraphs = content.split("\n").filter { it.isNotBlank() }
        _currentParagraphIndex.value = 0

        if (paragraphs.isNotEmpty()) {
            _isPlaying.value = true
            tts?.speak(paragraphs[0], TextToSpeech.QUEUE_FLUSH, null, "tts_para_0")
        }
    }

    fun pause() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun nextParagraph() {
        if (paragraphs.isEmpty()) return
        val next = (_currentParagraphIndex.value + 1).coerceAtMost(paragraphs.size - 1)
        _currentParagraphIndex.value = next
        tts?.speak(paragraphs[next], TextToSpeech.QUEUE_FLUSH, null, "tts_para_$next")
    }

    fun previousParagraph() {
        if (paragraphs.isEmpty()) return
        val prev = (_currentParagraphIndex.value - 1).coerceAtLeast(0)
        _currentParagraphIndex.value = prev
        tts?.speak(paragraphs[prev], TextToSpeech.QUEUE_FLUSH, null, "tts_para_$prev")
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
        _isPlaying.value = false
    }
}
