package net.corda.db.core

import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
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
internal class SimpleDataSource(
    driverClass: String,
    private val jdbcUrl: String,
    private val username: String,
    private val password: String,
): DataSource {
    private var logWriter: PrintWriter? = null
    private var timeout: Int = 25
    init {
        Class.forName(driverClass)
    }
    override fun getLogWriter(): PrintWriter? {
        return logWriter
    }

    override fun setLogWriter(out: PrintWriter?) {
        logWriter = out
    }

    override fun setLoginTimeout(seconds: Int) {
        timeout = seconds
    }

    override fun getLoginTimeout(): Int {
        return timeout
    }

    override fun getParentLogger(): Logger? {
        return null
    }

    override fun <T : Any?> unwrap(iface: Class<T>?): T? {
        return null
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean {
        return false
    }

    override fun getConnection(): Connection {
        return DriverManager.getConnection(jdbcUrl, username, password)
    }

    override fun getConnection(username: String?, password: String?): Connection {
        return DriverManager.getConnection(jdbcUrl, username?: this.username, password?: this.password)
    }

}
internal class LoggedDataSource(
    private val datasource: DataSource,
):DataSource by datasource {

    override fun getConnection(): Connection {
        return LoggedConnection(datasource.connection)
    }

    override fun getConnection(username: String?, password: String?): Connection {
        return LoggedConnection(datasource.getConnection(username, password))
    }
}