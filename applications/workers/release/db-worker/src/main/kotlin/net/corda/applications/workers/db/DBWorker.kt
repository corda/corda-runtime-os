package net.corda.applications.workers.db

import net.corda.applications.workers.workercommon.WorkerParams
import net.corda.applications.workers.workercommon.createProcessorCoordinator
import net.corda.applications.workers.workercommon.statusToDown
import net.corda.applications.workers.workercommon.statusToError
import net.corda.applications.workers.workercommon.statusToUp
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.osgi.api.Application
import net.corda.processors.db.DBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The worker for interacting with the database. */
@Suppress("Unused")
@Component(service = [Application::class])
class DBWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DBProcessor::class)
    private val processor: DBProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    // Passes start and stop events through to the DB processor.
    private val coordinator = createProcessorCoordinator<DBProcessor>(coordinatorFactory, processor)

    /** Parses the arguments, then initialises and starts the [DBProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("DB worker starting.")
        val config = WorkerParams().parseArgs(args, smartConfigFactory)
        processor.initialise(config, statusToUp(coordinator), statusToDown(coordinator), statusToError(coordinator))
        coordinator.start()
    }

    override fun shutdown() = coordinator.stop()
}