package com.jatta.study

import com.jatta.study.judge.ModelFile
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelFileTest {
    private val content = Random(7).nextBytes(3_000_000)
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private var cutAt: Int? = null
    private val ranges = mutableListOf<String?>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/resolve/model") { exchange ->
            exchange.responseHeaders.add("Location", "/storage/model")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        createContext("/storage/model") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            ranges += range
            val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            val body = content.copyOfRange(from, cutAt ?: content.size)
            if (range != null) exchange.responseHeaders.add("Content-Range", "bytes $from-${content.size - 1}/${content.size}")
            exchange.sendResponseHeaders(if (range != null) 206 else 200, (content.size - from).toLong())
            runCatching { exchange.responseBody.use { it.write(body) } }
            exchange.close()
        }
        start()
    }
    private val dir: File = createTempDirectory("jatta-model").toFile()
    private val url = "http://127.0.0.1:${server.address.port}/resolve/model"

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun aBrokenDownloadContinuesWhereItStopped() {
        val file = ModelFile(url, content.size.toLong(), sha, File(dir, "model.litertlm"))
        cutAt = 1_000_000
        assertFalse(runCatching { file.download {} }.getOrDefault(false), "the connection broke")
        assertFalse(file.isReady)

        cutAt = null
        val progress = mutableListOf<Long>()
        assertTrue(file.download { progress += it })
        assertTrue(file.isReady)
        assertContentEquals(content, file.target.readBytes())
        assertEquals(listOf(null, "bytes=1000000-"), ranges)
        assertEquals(content.size.toLong(), progress.last())
        assertTrue(file.download {}, "nothing to do once it is there")
        assertEquals(2, ranges.size)
    }

    @Test
    fun aStoppedDownloadKeepsWhatItHas() {
        val file = ModelFile(url, content.size.toLong(), sha, File(dir, "model.litertlm"))
        var chunks = 0
        assertFalse(file.download(isStopped = { chunks++ > 0 }) {})
        val kept = File(dir, "model.litertlm.part").length()
        assertTrue(kept in 1 until content.size, "kept $kept bytes")
        assertTrue(file.download {})
        assertEquals(listOf(null, "bytes=$kept-"), ranges)
    }

    @Test
    fun aDamagedFileIsThrownAwayAndNotUsed() {
        val file = ModelFile(url, content.size.toLong(), "0".repeat(64), File(dir, "model.litertlm"))
        assertFalse(file.download {})
        assertFalse(file.isReady)
        assertFalse(File(dir, "model.litertlm.part").exists(), "the next try starts over")
    }
}
