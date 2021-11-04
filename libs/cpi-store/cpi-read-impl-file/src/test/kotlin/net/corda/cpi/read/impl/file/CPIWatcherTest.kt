package net.corda.cpi.read.impl.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class CPIWatcherTest {
    companion object {
        const val AWAIT_TIMEOUT = 180L
    }

    @TempDir
    lateinit var tempDir: Path
    @Volatile
    var newCPILatch: CountDownLatch? = null
    @Volatile
    var deletedCPILatch: CountDownLatch? = null

    @Test
    fun watchCPIFileCreation() {
        newCPILatch = CountDownLatch(1)
        val listener = CPIFileListenerTestImpl(newCPILatch)
        val watcher = CPIWatcher(listener)
        watcher.startWatching(tempDir)
        Files.createFile(tempDir.resolve("a.cpi"))
        assertTrue(newCPILatch!!.await(AWAIT_TIMEOUT, TimeUnit.SECONDS))
        assertEquals(1, listener.newCPICount)
        watcher.stopWatching()
    }

    @Test
    fun watchCPIFileCreationThenDeletion() {
        newCPILatch = CountDownLatch(1)
        deletedCPILatch = CountDownLatch(1)
        val listener = CPIFileListenerTestImpl(newCPILatch, deletedCPILatch = deletedCPILatch)
        val watcher = CPIWatcher(listener)
        watcher.startWatching(tempDir)
        Files.createFile(tempDir.resolve("a.cpi"))
        assertTrue(newCPILatch!!.await(AWAIT_TIMEOUT, TimeUnit.SECONDS))
        Files.delete(tempDir.resolve("a.cpi"))
        assertTrue(deletedCPILatch!!.await(AWAIT_TIMEOUT, TimeUnit.SECONDS))
        assertEquals(1, listener.newCPICount)
        assertEquals(1, listener.deletedCPICount)
        watcher.stopWatching()
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