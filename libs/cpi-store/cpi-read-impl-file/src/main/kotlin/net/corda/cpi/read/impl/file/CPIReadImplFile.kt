package net.corda.cpi.read.impl.file

import com.typesafe.config.Config
import net.corda.cpi.read.CPIListener
import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPISegmentReader
import net.corda.cpi.utils.CPX_FILE_FINDER_PATTERN
import net.corda.cpi.utils.CPX_FILE_FINDER_ROOT_DIR_CONFIG_PATH
import net.corda.libs.configuration.SmartConfig
import net.corda.packaging.CPI
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.io.File
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CPIReadImplFile(private val vnodeConfig: SmartConfig): CPIFileListener, CPIRead, CPISegmentReader {
    private val cpis = Collections.synchronizedMap(mutableMapOf<Path, CPI>())
    private val cpiListeners: MutableList<CPIListener> = Collections.synchronizedList(mutableListOf())
    private lateinit var watcher: CPIWatcher

    @Volatile
    private var snapshotSent = false
    @Volatile
    private var stopped = true

    private val lock = ReentrantLock()
    private lateinit var startDir: Path

    companion object {
        val logger: Logger = contextLogger()
    }

    private fun populateSnapshot(startDir: Path) {
        val paths = mutableSetOf<Path>()
        val fileFinder = FileFinder(CPX_FILE_FINDER_PATTERN, paths)
        Files.walkFileTree(startDir, fileFinder)
        paths.forEach {
            cpis[it] = loadCPI(it)
        }
    }

    override fun start() {

        startDir = Paths.get(vnodeConfig.getString(CPX_FILE_FINDER_ROOT_DIR_CONFIG_PATH))

        lock.withLock {
            if (stopped) {
                stopped = false
                watcher = CPIWatcher(this)
                watcher.startWatching(startDir)
                populateSnapshot(startDir)
                updateListeners(cpis.map { it.value.metadata.id }.toSet())
                snapshotSent = true
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                stopped = true
                watcher.stopWatching()
            }
        }
    }

    private fun updateListeners(changedKeys: Set<CPI.Identifier>) {
        synchronized(cpiListeners) {
            cpiListeners.forEach {
                it.onUpdate(changedKeys, cpis.map { el -> el.value.metadata.id to el.value.metadata }.toMap())
            }
        }
    }

    override fun getCPI(cpiIdentifier: CPI.Identifier): CompletableFuture<InputStream> {
        val path = getCPIPath(cpiIdentifier)
        val resp = CompletableFuture<InputStream>()
        resp.complete(Files.newInputStream(path))
        return resp
    }

    private fun getCPIPath(cpbIdentifier: CPI.Identifier): Path {
        val paths = cpis.filter { it.value.metadata.id == cpbIdentifier }.keys
        if (paths.isEmpty()) {
            throw IllegalArgumentException("Unknown cpbIdentifier")
        }
        if (paths.size > 1) {
            throw IllegalStateException("More than one CPI identifier found")
        }
        return paths.first()
    }

    override fun registerCallback(cpiListener: CPIListener): AutoCloseable {
        synchronized(cpiListeners) {
            cpiListeners.add(cpiListener)
            if (snapshotSent) {
                val keys = cpis.map { it.value.metadata.id }.toSet()
                val identities = cpis.map { it.value.metadata.id to it.value.metadata }.toMap()
                cpiListener.onUpdate(keys, identities)
            }
            return CPIListenerRegistration(this, cpiListener)
        }
    }

    override val isRunning: Boolean
        get() = !stopped

    override fun newCPI(cpiPath: Path) {
        val cpi = loadCPI(cpiPath)
        cpis[cpiPath] = cpi
        updateListeners(setOf(cpi.metadata.id))
    }

    override fun modifiedCPI(cpiPath: Path) {
        val cpi = loadCPI(cpiPath)
        cpis[cpiPath] = cpi
        updateListeners(setOf(cpi.metadata.id))
    }

    override fun deletedCPI(cpiPath: Path) {
        val cpi = cpis[cpiPath]
        if (cpi == null) {
            val errorStr = "Trying to delete an unknown CPI - path: $cpiPath"
            logger.error(errorStr)
            throw IllegalArgumentException(errorStr)
        }
        cpis.remove(cpiPath)
        updateListeners(setOf(cpi.metadata.id))
    }

    private fun loadCPI(cpiPath: Path): CPI {
        return CPI.from(File(cpiPath.toString()).inputStream(), cpiPath.parent, cpiPath.parent.toString(), true)
    }

    private fun unregisterCPIListCallback(cpiListener: CPIListener) {
        synchronized(cpiListeners) {
            cpiListeners.remove(cpiListener)
        }
    }

    class CPIListenerRegistration(private val readImplFile: CPIReadImplFile, private val cpiListener: CPIListener): AutoCloseable {
        override fun close() {
            readImplFile.unregisterCPIListCallback(cpiListener)
        }
    }

    override fun getCPISegment(cpiIdentifier: CPI.Identifier, start: Long, byteBuffer: ByteBuffer): Boolean {
        val path = getCPIPath(cpiIdentifier)
        Files.newByteChannel(path).use { byteChannel ->
            byteChannel.position(start)
            while (byteBuffer.hasRemaining()) {
                val numBytesRead = byteChannel.read(byteBuffer)
                if (numBytesRead == -1) {
                    return true
                }
            }
            return false
        }
    }
}

private class FileFinder(pattern: String, private val files: MutableSet<Path>): SimpleFileVisitor<Path>() {
    private val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")

    override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
        if (file != null && matcher.matches(file.fileName)) {
            files.add(file)
        }
        return FileVisitResult.CONTINUE
    }
}



