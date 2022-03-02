package net.corda.testdoubles.streams

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream


class InMemoryStream : Closeable {
    private val temp = File.createTempFile("ims", null).apply { deleteOnExit() }
    val outputStream: OutputStream get() = temp.outputStream()
    val inputStream: InputStream get() = temp.inputStream()

    fun readText(): String = inputStream.bufferedReader().readText()

    override fun close() {
        temp.delete()
    }
}