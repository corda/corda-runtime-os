package net.corda.testdoubles

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream


class InMemoryStream : Closeable {
    private val temp = File.createTempFile("ims", null).apply { deleteOnExit() }
    val output: OutputStream get() = temp.outputStream()
    val input: InputStream get() = temp.inputStream()

    fun readText(): String = input.bufferedReader().readText()

    override fun close() {
        temp.delete()
    }
}