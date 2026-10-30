package com.jatta.shared

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFiles(
    private val context: Context,
    private val pickSaveTarget: suspend (String) -> Uri?,
    private val pickOpenSource: suspend () -> Uri?,
) : FileExchange {

    override suspend fun save(name: String, text: String): Boolean {
        val uri = pickSaveTarget(name) ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            }.isSuccess
        }
    }

    override suspend fun open(): String? {
        val uri = pickOpenSource() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
        }
    }
}
