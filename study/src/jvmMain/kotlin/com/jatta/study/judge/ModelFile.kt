package com.jatta.study.judge

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * A large model file fetched over HTTPS. A broken download continues where it stopped, the way phones
 * lose connection; the finished file must match its size and SHA-256 before it is used.
 */
class ModelFile(private val url: String, val bytes: Long, private val sha256: String, val target: File) {
    private val part = File(target.path + ".part")

    val isReady: Boolean get() = target.length() == bytes

    /** Fetches what is missing; [onProgress] gets the bytes on disk so far. True when the file is complete and intact. */
    fun download(onProgress: (Long) -> Unit): Boolean {
        if (isReady) return true
        target.parentFile?.mkdirs()
        if (part.length() > bytes) part.delete()
        if (target.parentFile!!.usableSpace < bytes - part.length() + SPARE_BYTES) return false
        if (part.length() < bytes) fetch(onProgress)
        if (part.length() != bytes || digest(part) != sha256) {
            part.delete()
            return false
        }
        return part.renameTo(target)
    }

    private fun fetch(onProgress: (Long) -> Unit) {
        val connection = open(part.length())
        try {
            val append = when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false // the server ignored the range: start over
                else -> return
            }
            var done = if (append) part.length() else 0L
            var reported = done
            onProgress(done)
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done - reported >= REPORT_EVERY_BYTES) {
                            reported = done
                            onProgress(done)
                        }
                    }
                }
            }
            onProgress(done)
        } finally {
            connection.disconnect()
        }
    }

    /** Hugging Face answers with a redirect to its file storage; the range has to go with every hop. */
    private fun open(from: Long): HttpURLConnection {
        var location = URL(url)
        repeat(MAX_REDIRECTS) {
            val connection = location.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (from > 0) connection.setRequestProperty("Range", "bytes=$from-")
            if (connection.responseCode !in 300..399) return connection
            location = URL(location, connection.getHeaderField("Location"))
            connection.disconnect()
        }
        error("Too many redirects for $url")
    }

    private fun digest(file: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sha.update(buffer, 0, read)
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_BYTES = 1 shl 20
        const val REPORT_EVERY_BYTES = 8L shl 20
        const val SPARE_BYTES = 200L shl 20
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_MS = 30_000
    }
}
