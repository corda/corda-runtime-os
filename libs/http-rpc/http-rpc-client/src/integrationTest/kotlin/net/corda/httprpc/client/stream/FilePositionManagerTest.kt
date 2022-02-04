package net.corda.httprpc.client.stream

import com.google.common.jimfs.Jimfs
import net.corda.utilities.div
import net.corda.v5.base.stream.PositionManager
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilePositionManagerTest {

    lateinit var fs: FileSystem

    @BeforeEach
    private fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    private fun afterEach() {
        fs.close()
    }

    @Test
    fun readWriteTest() {
        val filePath = fs.getPath("readWriteTest.txt")
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

    @TempDir
    lateinit var tempFolder: Path

    @Test
    fun doubleCreateTest() {
        // JimFS doesn't handle file locks as expected, so falling back to actual fs.
        //   https://github.com/google/jimfs/issues/67
        val filePath = tempFolder / "doubleCreate.txt"
        FilePositionManager(filePath).use {
            Assertions.assertThatThrownBy {
                FilePositionManager(filePath).close()
            }.isInstanceOf<OverlappingFileLockException>()
        }
    }

    @Test
    fun existingContent() {
        val filePath = fs.getPath("existingContent.txt")
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
        val filePath = fs.getPath("wrongContent.txt")
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
        val filePath = fs.getPath("multiThreadedTest.txt")
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