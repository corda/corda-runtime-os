package net.corda.db.core

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.sql.Connection
import javax.sql.DataSource

internal class LoggedConnection(
    val connection: Connection
): Connection by connection {
    private companion object {
        private val logger = LoggerFactory.getLogger("QQQ")
    }
    private val created = Exception("QQQ - ${connection.schema}:${connection.catalog}")

    override fun close() {
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