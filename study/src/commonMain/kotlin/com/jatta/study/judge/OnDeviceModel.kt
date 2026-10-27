package com.jatta.study.judge

enum class ModelState { Unavailable, Downloadable, Downloading, Ready }

interface OnDeviceModel {
    val name: String

    val downloadBytes: Long?

    suspend fun state(): ModelState

    suspend fun download(onProgress: (Long) -> Unit): Boolean

    suspend fun generate(prompt: String): String?
}
