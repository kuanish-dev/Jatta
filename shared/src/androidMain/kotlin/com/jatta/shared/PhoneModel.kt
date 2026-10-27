package com.jatta.shared

import com.jatta.study.judge.ModelState
import com.jatta.study.judge.OnDeviceModel

/**
 * Gemini Nano where the phone has it, since the system runs it and keeps it up to date; on any other
 * phone, [fallback] inside the app. The choice is made once, on the first question.
 */
class PhoneModel(private val system: OnDeviceModel, private val fallback: OnDeviceModel) : OnDeviceModel {
    private var chosen: OnDeviceModel? = null

    override val name: String get() = (chosen ?: fallback).name
    override val downloadBytes: Long? get() = (chosen ?: fallback).downloadBytes

    override suspend fun state(): ModelState = chosen().state()

    override suspend fun download(onProgress: (Long) -> Unit): Boolean = chosen().download(onProgress)

    override suspend fun generate(prompt: String): String? = chosen().generate(prompt)

    private suspend fun chosen(): OnDeviceModel =
        chosen ?: (if (system.state() != ModelState.Unavailable) system else fallback).also { chosen = it }
}
