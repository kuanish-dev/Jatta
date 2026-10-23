package com.jatta.study.judge

enum class ModelState { Unavailable, Downloadable, Downloading, Ready }

/**
 * A language model that runs on the phone: Gemini Nano on Android, Apple Foundation Models on iOS.
 * Platform code implements only these three calls; prompts and checks are shared.
 */
interface OnDeviceModel {
    suspend fun state(): ModelState

    /** Downloads the model; [onProgress] gets the bytes downloaded so far. True when the model is ready. */
    suspend fun download(onProgress: (Long) -> Unit): Boolean

    /** The model's raw reply, or null if the call failed. */
    suspend fun generate(prompt: String): String?
}
