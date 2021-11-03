package net.corda.cpi.read.impl.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch


class CPIWatcherTest {


    @TempDir
    lateinit var tempDir: Path

    @Test
    fun watchCPIFileCreation() {
        val countDownLatch = CountDownLatch(1)
        val listener = CPIFileListenerTestImpl(countDownLatch)
        val watcher = CPIWatcher(listener)

        val thread = Thread {
            watcher.startWatching(tempDir)
            countDownLatch.await()
            watcher.stopWatching()
        }
        thread.start()
        Files.createFile(tempDir.resolve("a.cpi"))
        // Files.delete(tempDir.resolve("a.cpi"))
        thread.join()
        assertEquals(1, listener.newCPICount)
        // assertEquals(1, listener.deletedCPICount)
    }

    @Test
    fun watchCPIFileCreationThenDeletion() {
        val newCPIDownLatch = CountDownLatch(1)
        val deletedCPILatch = CountDownLatch( 1)
        val listener = CPIFileListenerTestImpl(newCPIDownLatch, deletedCPILatch = deletedCPILatch)
        val watcher = CPIWatcher(listener)

        val thread = Thread {
            watcher.startWatching(tempDir)
            deletedCPILatch.await()
            watcher.stopWatching()
        }
        thread.start()
        Files.createFile(tempDir.resolve("a.cpi"))
        newCPIDownLatch.await()
        Files.delete(tempDir.resolve("a.cpi"))
        thread.join()
        assertEquals(1, listener.newCPICount)
        assertEquals(1, listener.deletedCPICount)
    }
}


private class CPIFileListenerTestImpl(val newCPILatch: CountDownLatch? = null,
                                      val modifiedCPILatch: CountDownLatch? = null,
                                      val deletedCPILatch: CountDownLatch? = null): CPIFileListener {
    var newCPICount: Int = 0
    var modifiedCPICount: Int = 0
    var deletedCPICount: Int = 0

    override fun newCPI(cpiPath: Path) {
        newCPICount++
        println("newCPICount = $newCPICount")
        newCPILatch?.countDown()
    }

    override fun modifiedCPI(cpiPath: Path) {
        modifiedCPICount++
        println("modifiedCPICount = $modifiedCPICount")
        modifiedCPILatch?.countDown()
    }

    override fun deletedCPI(cpiPath: Path) {
        deletedCPICount++
        println("deletedCPICount = $deletedCPICount")
        deletedCPILatch?.countDown()
    }
}