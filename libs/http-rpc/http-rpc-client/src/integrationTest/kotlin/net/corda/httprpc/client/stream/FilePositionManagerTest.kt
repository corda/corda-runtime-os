package net.corda.httprpc.client.stream

import net.corda.utilities.div
import net.corda.v5.base.stream.PositionManager
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilePositionManagerTest {

    @TempDir
    lateinit var tempFolder: Path

    @Test
    fun readWriteTest() {
        val filePath = tempFolder / "readWriteTest.txt"
        val filePositionManager = FilePositionManager(filePath)
        filePositionManager.use { instance ->
            assertEquals(PositionManager.MIN_POSITION, instance.get())

            instance.accept(100)
            assertEquals(100, instance.get())

            instance.accept(99999)
            assertEquals(99999, instance.get())

            instance.accept(88)
            assertEquals(88, instance.get())
        }

        Assertions.assertThatCode {
            // Double close
            filePositionManager.close()
        }.doesNotThrowAnyException()

        Assertions.assertThatThrownBy {
            // Read after close
            filePositionManager.get()
        }.isInstanceOf<ClosedChannelException>()
    }

    @Test
    fun doubleCreateTest() {
        val filePath = tempFolder / "doubleCreate.txt"
        FilePositionManager(filePath).use {
            Assertions.assertThatThrownBy {
                FilePositionManager(filePath)
            }.isInstanceOf<OverlappingFileLockException>()
        }
    }

    @Test
    fun existingContent() {
        val filePath = tempFolder / "existingContent.txt"
        val value = 101L
        FileChannel.open(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
            it.write(ByteBuffer.wrap(value.toString().toByteArray()))
        }
        FilePositionManager(filePath).use {
            assertEquals(value, it.get())
        }
    }

    @Test
    fun unparseableFile() {
        val filePath = tempFolder / "wrongContent.txt"
        FileChannel.open(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
            it.write(ByteBuffer.wrap("fooBar".toByteArray()))
        }
        FilePositionManager(filePath).use {
            Assertions.assertThatThrownBy {
                it.get()
            }.isInstanceOf<NumberFormatException>()
        }
    }

    @Test
    fun multiThreadedTest() {
        val filePath = tempFolder / "multiThreadedTest.txt"
        FilePositionManager(filePath).use { fpm ->
            val maxPos = 10_000
            (1..maxPos).toList().stream().parallel().forEach {
                fpm.accept(it.toLong())
                Thread.sleep(1)
                assertTrue { fpm.get() <= maxPos }
            }

            assertTrue { fpm.get() <= maxPos }
        }
    }

    inline fun <reified TYPE : Throwable> AbstractThrowableAssert<*, *>.isInstanceOf(): AbstractThrowableAssert<*, *> =
        isInstanceOf(TYPE::class.java)
}