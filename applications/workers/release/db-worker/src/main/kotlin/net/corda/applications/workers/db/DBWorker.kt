package net.corda.applications.workers.db

import net.corda.applications.workers.healthprovider.HealthProvider
import net.corda.applications.workers.workercommon.Worker
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.db.DBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The [Worker] for interacting with the database. */
@Suppress("Unused")
@Component(service = [Application::class])
class DBWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthProvider::class)
    healthProvider: HealthProvider
) : Worker(smartConfigFactory, healthProvider) {
    private companion object {
        private val logger = contextLogger()
    }

    /** Starts the [DBProcessor]. */
    override fun startup(config: SmartConfig) {
        logger.info("DB worker starting.")
        DBProcessor().startup(config)
    }
}