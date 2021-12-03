package net.corda.applications.workers.crypto

import net.corda.applications.workers.workercommon.WorkerParams
import net.corda.applications.workers.workercommon.createProcessorCoordinator
import net.corda.applications.workers.workercommon.statusToDown
import net.corda.applications.workers.workercommon.statusToError
import net.corda.applications.workers.workercommon.statusToUp
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.osgi.api.Application
import net.corda.processors.crypto.CryptoProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The worker for interacting with the key material. */
@Suppress("Unused")
@Component(service = [Application::class])
class CryptoWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoProcessor::class)
    private val processor: CryptoProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    // Passes start and stop events through to the crypto processor.
    private val coordinator = createProcessorCoordinator<CryptoProcessor>(coordinatorFactory, processor)

    /** Parses the arguments, then initialises and starts the [CryptoProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Crypto worker starting.")
        val config = WorkerParams().parseArgs(args, smartConfigFactory)
        processor.initialise(config, statusToUp(coordinator), statusToDown(coordinator), statusToError(coordinator))
        coordinator.start()
    }

    override fun shutdown() = coordinator.stop()
}