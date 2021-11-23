package net.corda.cpi.read.impl.file

import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIListener
import net.corda.cpi.utils.CPX_FILE_FINDER_ROOT_DIR_CONFIG_PATH
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactoryImpl
import net.corda.packaging.CPI
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CPIReadImplFileTest {

    companion object {
        const val AWAIT_TIMEOUT = 180L
    }
    private val cpiPath: Path = Paths.get(System.getProperty("cpiPath"))

    @TempDir
    lateinit var cpisLocation: Path

    @Test
    fun `start and stop CPI read in empty directory sends an empty snapshot`() {
        val config: SmartConfig = SmartConfigFactoryImpl().create(ConfigFactory.parseMap(mapOf(CPX_FILE_FINDER_ROOT_DIR_CONFIG_PATH to cpisLocation.toString())))
        val cpiListenerImpl = CPIListenerImpl()
        val cpiReadImplFile = CPIReadImplFile(config)
        cpiReadImplFile.start()
        cpiReadImplFile.registerCallback(cpiListenerImpl)
        assertEquals(1, cpiListenerImpl.count)
        cpiReadImplFile.stop()
    }

    @Test
    fun `start then insert cpb into directory then compare`() {

        val config: SmartConfig = SmartConfigFactoryImpl().create(ConfigFactory.parseMap(mapOf(CPX_FILE_FINDER_ROOT_DIR_CONFIG_PATH to cpisLocation.toString())))
        val targetCPI = cpisLocation.resolve(cpiPath.fileName)

        lateinit var snapshot:  Map<CPI.Identifier, CPI.Metadata>
        val firstLatch = CountDownLatch(1)
        val secondLatch = CountDownLatch(2)
        val cpiReadImplFile = CPIReadImplFile(config)
        cpiReadImplFile.start()
        cpiReadImplFile.registerCallback { _, currentSnapshot ->
            snapshot = currentSnapshot
            firstLatch.countDown()
            secondLatch.countDown()
        }
        assertTrue(firstLatch.await(AWAIT_TIMEOUT, TimeUnit.SECONDS))
        assertEquals(0, snapshot.size)

        Files.copy(cpiPath, targetCPI)
        assertTrue(secondLatch.await(AWAIT_TIMEOUT, TimeUnit.SECONDS))

        assertEquals(1, snapshot.size)

        // Now try to read file
        val key = snapshot.keys.first()
        val filename = "${key.name}-${key.version}"
        val file = cpisLocation.resolve(filename)
        Files.createDirectories(file.parent)
        Files.newByteChannel(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use {
            byteChannel ->
            var start: Long = 0
            do {
                // Check we can cope with different size buffers
                val randomSize = (100000..200000).random()
                val byteBuffer = ByteBuffer.allocate(randomSize)
                val atEnd = cpiReadImplFile.getCPISegment(key, start, byteBuffer)
                start += byteBuffer.position()
                byteBuffer.flip()
                byteChannel.write(byteBuffer)
            } while (!atEnd)
        }
        cpiReadImplFile.stop()

        Files.newInputStream(Paths.get(cpiPath.toString())).use { originalStream ->
            Files.newInputStream(file).use { copiedStream ->
                Assertions.assertTrue(contentsAreSame(originalStream, copiedStream))
            }
        }
    }
}

private class CPIListenerImpl(val latch: CountDownLatch? = null): CPIListener {
    var count = 0
    override fun onUpdate(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>) {
        count++
        latch?.countDown()
    }
}

private fun contentsAreSame(in1: InputStream, in2: InputStream): Boolean {
    while (true) {
        val data1 = in1.read()
        val data2 = in2.read()
        if (data1 != data2) {
            return false
        }
        if (data1 == -1) {
            return true
        }
    }
}

