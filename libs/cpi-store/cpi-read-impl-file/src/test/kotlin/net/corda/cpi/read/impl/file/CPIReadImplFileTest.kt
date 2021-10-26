package net.corda.cpi.read.impl.file

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIListener
import net.corda.cpi.utils.CPX_FILE_FINDER_ROOT_DIR_CONFIG_PATH
import net.corda.packaging.CPI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch

class CPIReadImplFileTest {

    val config: Config = ConfigFactory.parseMap(mapOf(CPX_FILE_FINDER_ROOT_DIR_CONFIG_PATH to "src/test/resources"))

    @Test
    fun `start and stop CPI read in empty directory sends an empty snapshot`() {
        val cpiListenerImpl = CPIListenerImpl()
        val cpiReadImplFile = CPIReadImplFile(config)
        cpiReadImplFile.start()
        cpiReadImplFile.registerCallback(cpiListenerImpl)
        assertEquals(1, cpiListenerImpl.count)
        cpiReadImplFile.stop()
    }

    // TODO: Copy in built cpb
    @Test
    fun `start then insert cpb into directory`() {
        lateinit var snapshot:  Map<CPI.Identifier, CPI.Metadata>
        val latchIdentifier = CountDownLatch(1)
        //val cpiListenerImpl = CPIListenerImpl(latchIdentifier)
        val cpiReadImplFile = CPIReadImplFile(config)
        cpiReadImplFile.start()
        cpiReadImplFile.registerCallback { _, currentSnapshot ->
            snapshot = currentSnapshot
            latchIdentifier.countDown()
        }
        latchIdentifier.await()
//        assertEquals(1, cpiListenerImpl.count)


        // Now try to read file
        val key = snapshot.keys.first()
        val tempPath = Paths.get("/Users/adelel-beik/tmp/result")
        val filename = "${key.name}-${key.version}"
        val file = tempPath.resolve(filename)
        Files.createDirectories(file.parent)
        Files.newByteChannel(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { byteChannel ->
            var start: Long = 0

            do {
                val byteBuffer = ByteBuffer.allocate(512 * 512)
                val atEnd = cpiReadImplFile.getCPISegment(key, start, byteBuffer)
                start += byteBuffer.position()
                byteBuffer.flip()
                byteChannel.write(byteBuffer)
            } while (!atEnd)
        }
        cpiReadImplFile.stop()
    }
}

private class CPIListenerImpl(val latch: CountDownLatch? = null): CPIListener {
    var count = 0
    override fun onUpdate(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>) {
        count++
        latch?.countDown()
    }
}

