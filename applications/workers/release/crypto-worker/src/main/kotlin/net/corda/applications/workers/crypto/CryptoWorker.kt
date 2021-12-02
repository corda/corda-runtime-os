package net.corda.applications.workers.crypto

import net.corda.applications.workers.healthprovider.HealthProvider
import net.corda.applications.workers.workercommon.Worker
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.crypto.CryptoProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The [Worker] for interacting with the key material. */
@Suppress("Unused")
@Component(service = [Application::class])
class CryptoWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthProvider::class)
    healthProvider: HealthProvider
) : Worker(smartConfigFactory, healthProvider) {
    private companion object {
        private val logger = contextLogger()
    }

    /** Starts the [CryptoProcessor]. */
    override fun startup(config: SmartConfig) {
        logger.info("Crypto worker starting.")
        CryptoProcessor().startup(config)
    }
}