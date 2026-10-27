package com.jatta.study.judge

enum class ModelState { Unavailable, Downloadable, Downloading, Ready }

/**
 * A language model that runs on the phone: Gemini Nano or Gemma 4 on Android, Apple Foundation Models on iOS.
 * Platform code implements only these calls; prompts and checks are shared.
 */
interface OnDeviceModel {
    /** Shown to the learner: "Gemma 4 E2B". */
    val name: String

    /** How much the download takes, when the app downloads it itself; null when the system does. */
    val downloadBytes: Long?

    suspend fun state(): ModelState

    /** Downloads the model; [onProgress] gets the bytes downloaded so far. True when the model is ready. */
    suspend fun download(onProgress: (Long) -> Unit): Boolean

    /** The model's raw reply, or null if the call failed. */
    suspend fun generate(prompt: String): String?
}
