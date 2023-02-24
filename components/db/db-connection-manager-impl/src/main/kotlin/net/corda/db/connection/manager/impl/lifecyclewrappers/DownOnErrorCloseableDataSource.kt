package net.corda.db.connection.manager.impl.lifecyclewrappers

import net.corda.db.core.CloseableDataSource
import net.corda.lifecycle.LifecycleCoordinator
import java.io.Closeable
import javax.sql.DataSource

class DownOnErrorCloseableDataSource(
    private val lifecycleCoordinator: LifecycleCoordinator,
    private val closeableDataSource: CloseableDataSource
) : CloseableDataSource,
    Closeable by DownOnErrorCloseable(lifecycleCoordinator, closeableDataSource),
    DataSource by DownOnErrorDataSource(lifecycleCoordinator, closeableDataSource)
