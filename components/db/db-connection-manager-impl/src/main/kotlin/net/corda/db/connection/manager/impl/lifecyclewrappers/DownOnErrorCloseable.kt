package net.corda.db.connection.manager.impl.lifecyclewrappers

import net.corda.lifecycle.LifecycleCoordinator
import java.io.Closeable

class DownOnErrorCloseable(
    private val lifecycleCoordinator: LifecycleCoordinator,
    private val closeable: Closeable
) : Closeable {
    override fun close() = lifecycleCoordinator.downOnError { closeable.close() }
}