package com.jatta.shared

import com.jatta.study.judge.ChatTurn
import com.jatta.study.judge.ModelState
import com.jatta.study.judge.OnDeviceModel

class PhoneModel(private val system: OnDeviceModel, private val fallback: OnDeviceModel) : OnDeviceModel {
    private var chosen: OnDeviceModel? = null

    override val name: String get() = (chosen ?: fallback).name
    override val downloadBytes: Long? get() = (chosen ?: fallback).downloadBytes

    override suspend fun state(): ModelState = chosen().state()

    override suspend fun download(onProgress: (Long) -> Unit): Boolean = chosen().download(onProgress)

    override suspend fun generate(prompt: String): String? = chosen().generate(prompt)

    override suspend fun chat(system: String, turns: List<ChatTurn>): String? = chosen().chat(system, turns)

    private suspend fun chosen(): OnDeviceModel =
        chosen ?: (if (system.state() != ModelState.Unavailable) system else fallback).also { chosen = it }
}
