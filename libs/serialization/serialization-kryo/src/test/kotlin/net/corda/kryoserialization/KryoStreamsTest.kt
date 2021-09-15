package net.corda.kryoserialization

import net.corda.utilities.reflection.declaredField
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.BufferOverflowException
import java.util.*
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

class KryoStreamsTest {
    class NegOutputStream(private val stream: OutputStream) : OutputStream() {
        override fun write(b: Int) = stream.write(-b)
    }

    class NegInputStream(private val stream: InputStream) : InputStream() {
        override fun read() = stream.read().let {
            if (it != -1) 0xff and -it else -1
        }
    }

    @Test
	fun `substitute output works`() {
        assertArrayEquals(byteArrayOf(100, -101), kryoOutput {
            write(100)
            substitute(KryoStreamsTest::NegOutputStream)
            write(101)
        })
    }

    @Test
    fun `substitute input works`() {
        kryoInput(byteArrayOf(100, 101).inputStream()) {
            assertEquals(100, read())
            substitute(KryoStreamsTest::NegInputStream)
            assertEquals(-101, read().toByte())
            assertEquals(-1, read())
        }
    }

    @Test
    fun `zip round-trip`() {
        val data = ByteArray(12345).also { Random(0).nextBytes(it) }
        val encoded = kryoOutput {
            write(data)
            substitute(::DeflaterOutputStream)
            write(data)
            substitute(::DeflaterOutputStream) // Potentially useful if a different codec.
            write(data)
        }
        kryoInput(encoded.inputStream()) {
            assertArrayEquals(data, readBytes(data.size))
            substitute(::InflaterInputStream)
            assertArrayEquals(data, readBytes(data.size))
            substitute(::InflaterInputStream)
            assertArrayEquals(data, readBytes(data.size))
            assertEquals(-1, read())
        }
    }

    @Test
    fun `ByteBufferOutputStream works`() {
        val stream = ByteBufferOutputStream(3)
        stream.write("abc".toByteArray())
        val getBuf = stream.declaredField<ByteArray>(ByteArrayOutputStream::class, "buf")::value
        assertEquals(3, getBuf().size)
        repeat(2) {
            assertSame(BufferOverflowException::class.java, catchThrowable {
                stream.alsoAsByteBuffer(9) {
                    it.put("0123456789".toByteArray())
                }
            }.javaClass)
            assertEquals(3 + 9, getBuf().size)
        }
        // This time make too much space:
        stream.alsoAsByteBuffer(11) {
            it.put("0123456789".toByteArray())
        }
        stream.write("def".toByteArray())
        assertArrayEquals("abc0123456789def".toByteArray(), stream.toByteArray())
    }

    @Test
    fun `ByteBufferOutputStream discards data after final position`() {
        val stream = ByteBufferOutputStream(0)
        stream.alsoAsByteBuffer(10) {
            it.put("0123456789".toByteArray())
            it.position(5)
        }
        stream.write("def".toByteArray())
        assertArrayEquals("01234def".toByteArray(), stream.toByteArray())
    }
}
