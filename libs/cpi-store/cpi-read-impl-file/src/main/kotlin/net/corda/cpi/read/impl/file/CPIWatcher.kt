package net.corda.cpi.read.impl.file

import net.corda.cpi.utils.CPX_FILE_FINDER_PATTERN
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

class CPIWatcher(private val cpiFileListener: CPIFileListener): Runnable {
    companion object {
        val logger: Logger = contextLogger()
    }

    private val keys = mutableMapOf<WatchKey, Path>()
    private val watcher = FileSystems.getDefault().newWatchService()
    @Volatile
    private var stopped = true
    private val matcher = FileSystems.getDefault().getPathMatcher("glob:$CPX_FILE_FINDER_PATTERN")
    private var watchingThread = Thread(this)

    private fun registerDir(dir: Path) {
        val key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
        keys[key] = dir
    }

    private fun registerAllDirs(startDir: Path) {
        Files.walkFileTree(startDir, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                registerDir(dir!!)
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun startWatching(dir: Path) {
        if (stopped) {
            stopped = false
            registerDir(dir)
            watchingThread.start()
        }
    }

    fun stopWatching() {
        if (!stopped) {
            stopped = true
            watchingThread.join(60000)
        }
    }

    override fun run() {
        while (!stopped) {
            val key = watcher.poll(1, TimeUnit.SECONDS)
            if (key != null) {
                val dir: Path? = keys[key]
                dir?.let { handleWatchKey(key, it)} ?: logger.error("Unknown watch key")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleWatchKey(key: WatchKey, dir: Path)
    {
        for (event in key.pollEvents()) {
            val name = (event as WatchEvent<Path>).context()
            val child = dir.resolve(name)

            when (event.kind()) {
                OVERFLOW -> {
                    // NOTE: Handle this, by resending full snapshot to the client
                }
                ENTRY_CREATE -> {
                    handleEntryCreate(child)
                }
                ENTRY_MODIFY -> {
                    handleEntryModify(child)
                }
                ENTRY_DELETE -> {
                    handleEntryDelete(child)
                }
            }
            val isValid = key.reset()
            if (!isValid) {
                keys.remove(key)
            }
            if (keys.isEmpty()) {
                logger.error("No valid directories left to watch, exiting file watcher thread")
                stopped = true
            }
        }
    }

    private fun handleEntryCreate(child: Path) {
        if (Files.isDirectory(child)) {
            registerAllDirs(child)
        } else if (matcher.matches(child.fileName)) {
            cpiFileListener.newCPI(child)
        }
    }
    private fun handleEntryModify(child: Path) {
        if (!Files.isDirectory(child) && matcher.matches(child.fileName)) {
            cpiFileListener.modifiedCPI(child)
        }
    }
    private fun handleEntryDelete(child: Path) {
        if (!Files.isDirectory(child) && matcher.matches(child.fileName)) {
            cpiFileListener.deletedCPI(child)
        }
    }
}






