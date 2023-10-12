package net.corda.db.core

import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

internal class LogCloseableDataSource(
    private val origin: DataSource,
): DataSource {
    companion object {
        init {
            Executors.newScheduledThreadPool(1, DaemonFactory).also {
                it.scheduleAtFixedRate(::report, 60, 10, TimeUnit.SECONDS)
            }
        }
        private val logger = LoggerFactory.getLogger("QQQ")
        private val allLiveConnections = ConcurrentHashMap<Connection, Throwable>()
        private val maxSoFar = AtomicInteger()
        private fun wrapConnection(connection: Connection) : Connection {
            allLiveConnections[connection] = Exception("Connection to ${connection.metaData.url}")
            return MyConnection(connection)
        }
        private fun forgetConnection(connection: Connection) {
            if (connection is MyConnection) {
                forgetConnection(connection.connection)
            }
            allLiveConnections.remove(connection)
        }
        private fun report() {
            logger.info("We now have ${allLiveConnections.size} live connections")
            if (allLiveConnections.size > maxSoFar.get()) {
                maxSoFar.set(allLiveConnections.size)
                logger.info("New pick - ${allLiveConnections.size}:")
                allLiveConnections.values.forEachIndexed { index, throwable ->
                    logger.info("Connection$index", throwable)
                }
            }
        }
    }


    override fun getLogWriter() = origin.logWriter

    override fun setLogWriter(out: PrintWriter?) {
        origin.logWriter = out
    }

    override fun setLoginTimeout(seconds: Int) {
        origin.loginTimeout=seconds
    }

    override fun getLoginTimeout() = origin.loginTimeout

    override fun getParentLogger() = origin.parentLogger

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        return origin.unwrap(iface)
    }

    override fun isWrapperFor(iface: Class<*>?) = origin.isWrapperFor(iface)

    override fun getConnection(): Connection {
        return wrapConnection(origin.connection)
    }

    override fun getConnection(username: String?, password: String?): Connection {
        return wrapConnection(origin.getConnection(username, password))
    }

    private class MyConnection(
        val connection: Connection
    ): Connection by connection {
        override fun close() {
            forgetConnection(connection)
            connection.close()
        }
    }

    private object DaemonFactory: ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            return Thread(r).also {
                it.isDaemon = true
            }
        }

    }
}