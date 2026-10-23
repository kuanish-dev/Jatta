package com.jatta.shared

import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance

/** iOS speech synthesis with an American English voice. Not run yet: needs Xcode and a simulator. */
class IosSpeaker : Speaker {
    private val synthesizer = AVSpeechSynthesizer()

    override fun speak(text: String) {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
        synthesizer.speakUtterance(utterance)
    }
}
