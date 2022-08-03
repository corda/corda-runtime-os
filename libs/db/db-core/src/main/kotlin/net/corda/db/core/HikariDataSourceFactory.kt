package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import kotlin.concurrent.thread

class HikariDataSourceFactory(

    private val hikariDataSourceFactory: (c: HikariConfig) -> CloseableDataSource = { c ->
        DataSourceWrapper(HikariDataSource(c))
    }
) : DataSourceFactory {
    /**
     * [HikariDataSource] wrapper that makes it [CloseableDataSource]
     */
    private class DataSourceWrapper(private val delegate: HikariDataSource): CloseableDataSource, DataSource by delegate {

        override fun getConnection(): Connection {
            val connection = delegate.connection
            return ConnectionProxy(connection)
        }

        override fun getConnection(username: String?, password: String?): Connection {
            val connection = delegate.getConnection(username, password)
            return ConnectionProxy(connection)
        }

        override fun close() = delegate.close()
    }

    companion object {
        private val openConnections = ConcurrentHashMap<Connection, Exception>()
        init {
            println("QQQ Starting ticker...")
            thread {
                while(true) {
                    println("QQQ Tick...")
                    Thread.sleep(10*1000)
                    if(openConnections.isNotEmpty()) {
                        println("QQQ There are ${openConnections.size} open connections created by:")
                        openConnections.values.forEach {
                            it.printStackTrace(System.out)
                            println("QQQ --- QQQ")
                        }
                    }
                }
            }
        }
    }
    private class ConnectionProxy(private val proxy: Connection): Connection by proxy {
        init {
            openConnections[proxy] = Exception("QQQ")
        }
        override fun close() {
            openConnections.remove(proxy)
            proxy.close()
        }
    }

    override fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean,
        maximumPoolSize: Int
    ): CloseableDataSource {
        val conf = HikariConfig()
        conf.driverClassName = driverClass
        conf.jdbcUrl = jdbcUrl
        conf.username = username
        conf.password = password
        conf.isAutoCommit = isAutoCommit
        conf.maximumPoolSize = maximumPoolSize
        return hikariDataSourceFactory(conf)
    }
}
