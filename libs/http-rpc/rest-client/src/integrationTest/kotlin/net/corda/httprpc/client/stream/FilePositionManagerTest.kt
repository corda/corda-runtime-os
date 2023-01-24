package net.corda.httprpc.client.stream

import net.corda.utilities.deleteRecursively
import net.corda.utilities.div
import net.corda.httprpc.durablestream.api.PositionManager
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilePositionManagerTest {

    private companion object {
        val log = contextLogger()
    }

    private val tempFolder = Files.createTempDirectory(this::class.java.simpleName)

    @AfterEach
    fun teardown() {
        // When executed on CI with K8s on Windows, the file operations seems to be quite slow/asynchronous.
        // In particular when it comes to deleting of directories and their content, it may lead to
        // having a directory structure in the inconsistent state. Therefore, being a bit more lenient here
        // by catching any exception and logging them rather than failing the test as `@TempDir` would do.
        try {
            tempFolder.deleteRecursively()

        } catch (th: Throwable) {
            log.warn("Error whilst cleaning-up directories", th)
        }
    }

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

    private inline fun <reified TYPE : Throwable> AbstractThrowableAssert<*, *>.isInstanceOf(): AbstractThrowableAssert<*, *> =
        isInstanceOf(TYPE::class.java)
}