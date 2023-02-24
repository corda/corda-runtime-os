package net.corda.db.connection.manager.impl.lifecyclewrappers

import net.corda.lifecycle.LifecycleCoordinator
import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

class DownOnErrorDataSource(
    private val lifecycleCoordinator: LifecycleCoordinator,
    private val dataSource: DataSource
) : DataSource {
    override fun getLogWriter(): PrintWriter =
        lifecycleCoordinator.downOnError { dataSource.logWriter }

    override fun setLogWriter(out: PrintWriter?) =
        lifecycleCoordinator.downOnError { dataSource.logWriter = out }

    override fun setLoginTimeout(seconds: Int) =
        lifecycleCoordinator.downOnError { dataSource.loginTimeout = seconds }

    override fun getLoginTimeout(): Int =
        lifecycleCoordinator.downOnError { dataSource.loginTimeout }

    override fun getParentLogger(): Logger =
        lifecycleCoordinator.downOnError { dataSource.parentLogger }

    override fun <T : Any?> unwrap(iface: Class<T>?): T =
        lifecycleCoordinator.downOnError { dataSource.unwrap(iface) }

    override fun isWrapperFor(iface: Class<*>?): Boolean =
        lifecycleCoordinator.downOnError { dataSource.isWrapperFor(iface) }

    override fun getConnection(): Connection =
        lifecycleCoordinator.downOnError { dataSource.connection }

    override fun getConnection(username: String?, password: String?): Connection =
        lifecycleCoordinator.downOnError { dataSource.getConnection(username, password) }

}