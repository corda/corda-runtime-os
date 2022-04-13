package net.corda.entityprocessor.impl.tests.fake

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import org.mockito.Mockito.mock
import org.osgi.service.component.annotations.Component


// UNUSED AT THE MOMENT - MOCKING SEEMS FINE

//@ServiceRanking(Int.MAX_VALUE)
@Component(service = [DbConnectionManager::class, FakeDbConnectionManager::class])
class FakeDbConnectionManager(
    val dataSourceFactory: DataSourceFactory = mock(DataSourceFactory::class.java),
    val dbConnectionOps: DbConnectionOps = mock(DbConnectionOps::class.java),
): DbConnectionManager, DbConnectionOps by dbConnectionOps, DataSourceFactory by dataSourceFactory {
    private companion object {
        private val logger = contextLogger()
    }

    private var smartConfig: SmartConfig? = null

    override fun initialise(config: SmartConfig) {
        smartConfig = config
        logger.info("Stub DbConnectionManager initialised with $config")
    }

    override val clusterConfig: SmartConfig
        get() = smartConfig!!

    override fun bootstrap(config: SmartConfig) {
        smartConfig = config
        logger.info("Stub DbConnectionManager bootstrapped with $config")
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Stub DbConnectionManager started")
    }

    override fun stop() {
        logger.info("Stub DbConnectionManager stopped")
    }

}
