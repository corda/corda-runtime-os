package net.corda.applications.workers.db

import net.corda.applications.workers.workercommon.WorkerParams
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
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
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DBProcessor::class)
    private val processor: DBProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    // Passes start and stop events through to the DB processor.
    private val coordinator = coordinatorFactory.createCoordinator<DBProcessor> { event, _ ->
        when (event) {
            is StartEvent -> processor.start()
            is StopEvent -> processor.stop()
        }
    }

    /** Parses the arguments, then initialises and starts the [DBProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("DB worker starting.")
        val config = WorkerParams().parseArgs(args, smartConfigFactory)
        initialiseProcessor(config)
        coordinator.start()
    }

    override fun shutdown() = coordinator.stop()

    /** Initialises the [processor]. */
    private fun initialiseProcessor(config: SmartConfig) {
        processor.config = config
        processor.onStatusUpCallback = ::setStatusToUp
        processor.onStatusDownCallback = ::setStatusToDown
        processor.onStatusErrorCallback = ::setStatusToError
    }

    /** Sets the coordinator's status to [LifecycleStatus.UP]. */
    private fun setStatusToUp() = coordinator.updateStatus(LifecycleStatus.UP)

    /** Sets the coordinator's status to [LifecycleStatus.DOWN]. */
    private fun setStatusToDown() = coordinator.updateStatus(LifecycleStatus.DOWN)

    /** Sets the coordinator's status to [LifecycleStatus.ERROR]. */
    private fun setStatusToError() = coordinator.updateStatus(LifecycleStatus.ERROR)
}