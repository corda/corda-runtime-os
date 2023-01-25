package net.corda.httprpc.client.stream

import net.corda.httprpc.durablestream.api.PositionManager
import net.corda.httprpc.durablestream.api.PositionManager.Companion.MIN_POSITION
import net.corda.v5.base.util.contextLogger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class FilePositionManager(private val filePath: Path) : PositionManager, AutoCloseable {
    companion object {
        private val log = contextLogger()
        private const val BUFFER_SIZE = Long.MAX_VALUE.toString().length
    }

    private val fileChannel = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    private val fileLock = fileChannel.lock()

    private val readWriteLock = ReentrantReadWriteLock()

    override fun accept(t: Long) {
        val byteArray = t.toString().toByteArray()
        val byteBuffer = ByteBuffer.wrap(byteArray)

        readWriteLock.write {
            fileChannel.position(0)
            fileChannel.write(byteBuffer)
            fileChannel.truncate(byteArray.size.toLong())
        }
    }

    override fun get(): Long {
        val byteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
        readWriteLock.read {
            fileChannel.position(0)
            fileChannel.read(byteBuffer)
        }

        if (byteBuffer.position() == 0) {
            return MIN_POSITION
        }

        val bytes = byteBuffer.array()
        val positionString = String(bytes, 0, byteBuffer.position())
        return java.lang.Long.parseLong(positionString)
    }

    override fun close() {
        if (fileChannel.isOpen) {
            log.info("Closing: $filePath")
            fileLock.release()
            fileChannel.close()
        } else {
            log.info("Not closing: $filePath as it is already closed")
        }
    }
}