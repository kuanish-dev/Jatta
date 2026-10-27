package com.jatta.shared

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSData
import platform.Foundation.create

class IosSpeaker : Speaker {
    private val synthesizer = AVSpeechSynthesizer()
    private var player: AVAudioPlayer? = null

    override fun speak(text: String) {
        player?.stop()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
        synthesizer.speakUtterance(utterance)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun play(recording: ByteArray) {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        player?.stop()
        val data = recording.usePinned { NSData.create(bytes = it.addressOf(0), length = recording.size.toULong()) }
        player = AVAudioPlayer(data = data, error = null).also { it.play() }
    }
}
