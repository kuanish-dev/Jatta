package com.jatta.shared

import androidx.compose.runtime.staticCompositionLocalOf

interface FileExchange {
    suspend fun save(name: String, text: String): Boolean

    suspend fun open(): String?
}

val LocalFiles = staticCompositionLocalOf<FileExchange?> { null }
