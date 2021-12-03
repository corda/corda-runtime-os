package net.corda.applications.workers.rpc

import net.corda.applications.workers.workercommon.WorkerParams
import net.corda.applications.workers.workercommon.createProcessorCoordinator
import net.corda.applications.workers.workercommon.statusToDown
import net.corda.applications.workers.workercommon.statusToError
import net.corda.applications.workers.workercommon.statusToUp
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.osgi.api.Application
import net.corda.processors.rpc.RPCProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The worker for handling RPC requests. */
@Suppress("Unused")
@Component(service = [Application::class])
class RPCWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = RPCProcessor::class)
    private val processor: RPCProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    // Passes start and stop events through to the RPC processor.
    private val coordinator = createProcessorCoordinator<RPCProcessor>(coordinatorFactory, processor)

    /** Parses the arguments, then initialises and starts the [RPCProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("RPC worker starting.")
        val config = WorkerParams().parseArgs(args, smartConfigFactory)
        processor.initialise(config, statusToUp(coordinator), statusToDown(coordinator), statusToError(coordinator))
        coordinator.start()
    }

    override fun shutdown() = coordinator.stop()
}