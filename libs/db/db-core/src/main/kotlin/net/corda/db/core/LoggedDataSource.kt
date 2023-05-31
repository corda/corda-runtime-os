package net.corda.db.core

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

internal class LoggedConnection(
    val connection: Connection
): Connection by connection {
    private companion object {
        private val logger = LoggerFactory.getLogger("QQQ")
        private val activeConnection = ConcurrentHashMap.newKeySet<LoggedConnection>()
        private val thread = Executors.newScheduledThreadPool(1).also {
            it.scheduleAtFixedRate(::report, 1, 1, TimeUnit.MINUTES)
        }
        private fun report() {
            logger.info("In $thread report, number of open connections so far is: ${activeConnection.size}")

            activeConnection.forEach {
                logger.info("$it is still open", it.created)
            }
        }
    }
    private val created = Exception("QQQ - ${connection.schema}:${connection.catalog}")
    init {
        activeConnection.add(this)
    }


    override fun close() {
        connection.close()
        activeConnection.remove(this)
        logger.info("Closing connection $created", Exception("Closed", created))
    }
}
internal class LoggedDataSource(
    private val origin: HikariDataSource
): CloseableDataSource, DataSource by origin, Closeable by origin {
    override fun getConnection(): Connection {
        return LoggedConnection(origin.connection)
    }

    override fun getConnection(username: String?, password: String?): Connection {
        return LoggedConnection(origin.getConnection(username, password))
    }
}