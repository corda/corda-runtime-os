package net.corda.crypto.persistence.impl.tests.infra

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import org.mockito.kotlin.mock

class TestDbConnectionManager(
    coordinatorFactory: LifecycleCoordinatorFactory,
    val _mock: DbConnectionManager = mock()
) : DbConnectionManager by _mock {
    val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<DbConnectionManager>()
    ) { e, c ->
        if (e is StartEvent) {
            c.updateStatus(LifecycleStatus.UP)
        }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}